package com.example.contextagent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Foreground service for pseudo-synchronous voice conversation.
 * Pipeline: AudioRecord -> circular buffer -> VAD -> closed segment -> STT (backend) -> LLM -> TTS -> playback.
 * Runs when app is in background or screen is locked (with appropriate permissions).
 */
class VoiceCaptureForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "evelyn_voice_capture"
        const val NOTIFICATION_ID = 2001
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_SAMPLE = 2
        const val BUFFER_DURATION_MS = 30_000
        const val BUFFER_SIZE_SAMPLES = (SAMPLE_RATE * BUFFER_DURATION_MS / 1000)
        const val BUFFER_SIZE_BYTES = BUFFER_SIZE_SAMPLES * BYTES_PER_SAMPLE
        const val VAD_CHUNK_SAMPLES = 320
        const val VAD_CHUNK_MS = (VAD_CHUNK_SAMPLES * 1000) / SAMPLE_RATE
        const val VAD_SPEECH_THRESHOLD = 800.0
        const val VAD_SILENCE_MS_TO_END = 600
        const val VAD_MIN_SPEECH_MS = 400
        const val PRE_SPEECH_PADDING_MS = 300

        const val EXTRA_BASE_URL = "base_url"
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_SPEAKER_ID = "speaker_id"
        const val ACTION_STOP = "action_stop"
    }

    private val client = OkHttpClient.Builder().build()
    private var baseUrl: String = ""
    private var userId: String = ""
    private var speakerId: String? = null

    private var audioRecord: AudioRecord? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var captureJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    private var mediaPlayer: MediaPlayer? = null

    private val circularBuffer = ByteArray(BUFFER_SIZE_BYTES)
    private var writeIndex = 0
    private var speechStartIndex = -1
    private var speechStartTimeMs: Long = 0
    private var lastSpeechTimeMs: Long = 0
    private var inSpeech = false
    private var silenceFramesCount = 0
    private val preSpeechPaddingSamples = (SAMPLE_RATE * PRE_SPEECH_PADDING_MS / 1000) * BYTES_PER_SAMPLE
    private val minSpeechSamples = (SAMPLE_RATE * VAD_MIN_SPEECH_MS / 1000) * BYTES_PER_SAMPLE
    private val silenceFramesToEnd = VAD_SILENCE_MS_TO_END / VAD_CHUNK_MS

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        baseUrl = intent?.getStringExtra(EXTRA_BASE_URL) ?: ""
        userId = intent?.getStringExtra(EXTRA_USER_ID) ?: ""
        speakerId = intent?.getStringExtra(EXTRA_SPEAKER_ID)?.takeIf { it.isNotBlank() }

        if (baseUrl.isBlank() || userId.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!isRunning.getAndSet(true)) {
            startCapture()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning.set(false)
        captureJob?.cancel()
        scope.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Conversa por voz (Evelyn)",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, VoiceCaptureForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Evelyn – ouvindo")
            .setContentText("Conversa por voz ativa. Toque para abrir.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Parar", stopIntent)
            .build()
    }

    private fun startCapture() {
        val minBufferBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferBytes == AudioRecord.ERROR_BAD_VALUE || minBufferBytes == AudioRecord.ERROR) {
            return
        }
        val bufferSize = (minBufferBytes * 2).coerceAtLeast(VAD_CHUNK_SAMPLES * BYTES_PER_SAMPLE)
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
        } catch (e: Exception) {
            return
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return
        }
        audioRecord = rec
        rec.startRecording()

        captureJob = scope.launch {
            val chunk = ShortArray(VAD_CHUNK_SAMPLES)
            val chunkBytes = ByteArray(VAD_CHUNK_SAMPLES * BYTES_PER_SAMPLE)
            while (isRunning.get()) {
                val read = rec.read(chunk, 0, chunk.size)
                if (read <= 0) continue
                val actualSamples = read
                val chunkLen = actualSamples * BYTES_PER_SAMPLE
                ByteBuffer.allocate(chunkLen).order(ByteOrder.LITTLE_ENDIAN).apply {
                    for (i in 0 until actualSamples) putShort(chunk[i])
                    rewind()
                    get(chunkBytes, 0, chunkLen.coerceAtMost(chunkBytes.size))
                }
                writeChunk(chunkBytes, actualSamples * BYTES_PER_SAMPLE)
                val rms = computeRms(chunk, actualSamples)
                vadStep(rms, actualSamples * BYTES_PER_SAMPLE)
            }
        }
    }

    private fun writeChunk(data: ByteArray, len: Int) {
        synchronized(circularBuffer) {
            for (i in 0 until len) {
                circularBuffer[writeIndex] = data[i]
                writeIndex = (writeIndex + 1) % BUFFER_SIZE_BYTES
            }
        }
    }

    private fun computeRms(samples: ShortArray, count: Int): Double {
        var sum = 0.0
        for (i in 0 until count) {
            val v = samples[i].toDouble() / 32768.0
            sum += v * v
        }
        return sqrt(sum / count.coerceAtLeast(1))
    }

    private fun vadStep(rms: Double, chunkBytes: Int) {
        val now = System.currentTimeMillis()
        if (rms >= VAD_SPEECH_THRESHOLD / 32768.0) {
            silenceFramesCount = 0
            if (!inSpeech) {
                inSpeech = true
                speechStartTimeMs = now
                speechStartIndex = (writeIndex - chunkBytes - preSpeechPaddingSamples + BUFFER_SIZE_BYTES) % BUFFER_SIZE_BYTES
            }
            lastSpeechTimeMs = now
        } else {
            if (inSpeech) {
                silenceFramesCount++
                if (silenceFramesCount >= silenceFramesToEnd) {
                    val durationMs = lastSpeechTimeMs - speechStartTimeMs
                    if (durationMs >= VAD_MIN_SPEECH_MS) {
                        val endIndex = (writeIndex - silenceFramesCount * chunkBytes + BUFFER_SIZE_BYTES) % BUFFER_SIZE_BYTES
                        onSpeechSegmentClosed(speechStartIndex, endIndex)
                    }
                    inSpeech = false
                    silenceFramesCount = 0
                }
            }
        }
    }

    private fun onSpeechSegmentClosed(startIdx: Int, endIdx: Int) {
        scope.launch {
            val wavFile = extractSegmentToWav(startIdx, endIdx) ?: return@launch
            try {
                uploadAndPlay(wavFile)
            } finally {
                try { wavFile.delete() } catch (_: Exception) {}
            }
        }
    }

    private fun extractSegmentToWav(startIdx: Int, endIdx: Int): File? {
        val (start, size) = if (endIdx > startIdx) {
            startIdx to (endIdx - startIdx)
        } else {
            startIdx to (BUFFER_SIZE_BYTES - startIdx + endIdx)
        }
        if (size < minSpeechSamples) return null
        val maxSamples = 30 * SAMPLE_RATE * BYTES_PER_SAMPLE
        val actualSize = size.coerceAtMost(maxSamples)
        val pcm = ByteArray(actualSize)
        synchronized(circularBuffer) {
            for (i in 0 until actualSize) {
                pcm[i] = circularBuffer[(start + i) % BUFFER_SIZE_BYTES]
            }
        }
        val numSamples = actualSize / BYTES_PER_SAMPLE
        val wavFile = File(cacheDir, "pseudo_sync_${UUID.randomUUID()}.wav")
        return try {
            writeWavHeader(wavFile, numSamples)
            RandomAccessFile(wavFile, "rw").use { raf ->
                raf.seek(44)
                raf.write(pcm)
            }
            wavFile
        } catch (_: Exception) {
            null
        }
    }

    private fun writeWavHeader(file: File, numSamples: Int) {
        val dataSize = numSamples * BYTES_PER_SAMPLE
        val fileSize = 36 + dataSize
        RandomAccessFile(file, "rw").use { raf ->
            raf.write("RIFF".toByteArray())
            raf.write(intToLittleEndian(fileSize))
            raf.write("WAVE".toByteArray())
            raf.write("fmt ".toByteArray())
            raf.write(intToLittleEndian(16))
            raf.write(shortToLittleEndian(1))
            raf.write(shortToLittleEndian(1))
            raf.write(intToLittleEndian(SAMPLE_RATE))
            raf.write(intToLittleEndian(SAMPLE_RATE * BYTES_PER_SAMPLE))
            raf.write(shortToLittleEndian(BYTES_PER_SAMPLE))
            raf.write(shortToLittleEndian(16))
            raf.write("data".toByteArray())
            raf.write(intToLittleEndian(dataSize))
        }
    }

    private fun intToLittleEndian(v: Int): ByteArray =
        byteArrayOf(
            (v and 0xff).toByte(),
            (v shr 8 and 0xff).toByte(),
            (v shr 16 and 0xff).toByte(),
            (v shr 24 and 0xff).toByte()
        )

    private fun shortToLittleEndian(v: Int): ByteArray =
        byteArrayOf((v and 0xff).toByte(), (v shr 8 and 0xff).toByte())

    private suspend fun uploadAndPlay(wavFile: File) = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", wavFile.name, wavFile.asRequestBody("audio/wav".toMediaType()))
            .apply {
                speakerId?.let { id ->
                    addFormDataPart("speaker_id", id)
                }
            }
            .build()
        val request = Request.Builder()
            .url("$baseUrl/chat/$userId/send_audio")
            .post(body)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext
                val bodyStr = response.body?.string() ?: return@withContext
                val obj = JSONObject(bodyStr)
                if (!obj.optBoolean("ok", false)) return@withContext
                val arr = obj.optJSONArray("assistant_messages") ?: return@withContext
                for (i in 0 until arr.length()) {
                    val msg = arr.getJSONObject(i)
                    val audioUrl = msg.optString("audio_url", "").takeIf { it.isNotBlank() }
                    if (audioUrl != null) {
                        playResponseAudio(audioUrl)
                        break
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun playResponseAudio(url: String) {
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        val player = MediaPlayer().apply {
            setDataSource(url)
            setOnPreparedListener {
                it.start()
                acquireWakeLock()
            }
            setOnCompletionListener {
                releaseWakeLock()
                it.release()
                if (mediaPlayer === it) mediaPlayer = null
            }
            setOnErrorListener { _, _, _ ->
                releaseWakeLock()
                true
            }
            prepareAsync()
        }
        mediaPlayer = player
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private fun acquireWakeLock() {
        releaseWakeLock()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VoiceCaptureForegroundService:playback").apply {
            acquire(10_000L)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
    }
}
