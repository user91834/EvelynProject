package com.example.contextagent

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.messaging.FirebaseMessaging
import android.widget.Toast
import kotlinx.coroutines.delay
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ArticulationPose(
    val jawOpen: Float,
    val lipRound: Float,
    val lipSpread: Float,
    val lipPress: Float,
    val tongueTipUp: Float,
    val tongueTipForward: Float,
    val tongueBodyHigh: Float,
    val tongueBodyFront: Float,
    val tongueVisible: Float
)

data class GestureFrame(
    val tMs: Long,
    val type: String,
    val ipa: String,
    val durationMs: Long,
    val pose: ArticulationPose
)

data class SpeechMetaOut(
    val ipaText: String,
    val gestureTimeline: List<GestureFrame>
)

data class ChatMessage(
    val role: String,
    val text: String,
    val tsMs: Long,
    val audioUrl: String?,
    val modality: String,
    val speechMeta: SpeechMetaOut? = null
)

data class AssistantMessageOut(
    val text: String,
    val modality: String,
    val delayMs: Long = 0L,
    val sequenceId: String? = null,
    val audioUrl: String? = null,
    val speechMeta: SpeechMetaOut? = null
)

data class VoiceSlot(
    val id: String,
    val title: String,
    val displayName: String,
    val type: String,
    val enrolled: Boolean,
    val isSelf: Boolean = false
) {
    fun statusLabel(): String = when {
        !enrolled -> "não cadastrado"
        type == "nonverbal" -> "não verbal cadastrado"
        else -> "cadastrado"
    }
}

class MainActivity : ComponentActivity() {

    private val baseUrl: String
        get() = BuildConfig.BASE_URL

    private var currentUserId: String? = null
    private val userId: String
        get() = currentUserId?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_USER_ID

    private fun getStoredUserId(): String? =
        getSharedPreferences("evelyn_prefs", MODE_PRIVATE).getString("user_id", null)

    private fun saveLogin(userId: String, token: String) {
        getSharedPreferences("evelyn_prefs", MODE_PRIVATE).edit()
            .putString("user_id", userId)
            .putString("jwt_token", token)
            .apply()
        currentUserId = userId
    }

    private fun logout() {
        getSharedPreferences("evelyn_prefs", MODE_PRIVATE).edit()
            .remove("user_id")
            .apply()
        currentUserId = null
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(Interceptor { chain ->
                val prefs = getSharedPreferences("evelyn_prefs", MODE_PRIVATE)
                val token = prefs.getString("jwt_token", null)
                val userId = prefs.getString("user_id", null) ?: BuildConfig.DEFAULT_USER_ID
                var req = chain.request().newBuilder()
                if (!token.isNullOrBlank()) {
                    req = req.addHeader("Authorization", "Bearer $token")
                }
                if (userId.isNotBlank()) {
                    req = req.addHeader("X-User-Id", userId)
                }
                chain.proceed(req.build())
            })
            .build()
    }

    private fun testBackendConnectivity(
        onOk: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "$baseUrl/ping"
        val attempts = 3
        val backoffMs = 2000L
        Thread {
            var lastErr: Exception? = null
            repeat(attempts) { idx ->
                try {
                    val req = Request.Builder()
                        .url(url)
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        if (resp.isSuccessful) {
                            Handler(Looper.getMainLooper()).post {
                                onOk("Backend OK (${resp.code})")
                            }
                            return@Thread
                        }
                        lastErr = RuntimeException("HTTP ${resp.code}: $body")
                    }
                } catch (e: Exception) {
                    lastErr = e
                }

                if (idx < attempts - 1) {
                    try {
                        Thread.sleep(backoffMs * (idx + 1))
                    } catch (_: Exception) {}
                }
            }

            val detail = lastErr?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: "unknown error"
            Handler(Looper.getMainLooper()).post {
                onError("Nao foi possivel alcancar o backend no Render.\nURL: $url\nDetalhe: $detail")
            }
        }.start()
    }
    private val deviceId by lazy { getOrCreateDeviceId() }

    private var recorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var player: MediaPlayer? = null
    private var currentlyPlayingUrl: String? = null

    private val playbackHandler = Handler(Looper.getMainLooper())
    private var playbackProgressRunnable: Runnable? = null
    private var activeSpeechMeta: SpeechMetaOut? = null

    @Volatile
    private var isAppInForeground: Boolean = false

    override fun onResume() {
        super.onResume()
        isAppInForeground = true
        postDeviceState()
    }

    override fun onPause() {
        super.onPause()
        isAppInForeground = false
        postDeviceState()
    }

    override fun onDestroy() {
        super.onDestroy()
        recorder?.release()
        recorder = null
        stopAudioInternal()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentUserId = getStoredUserId()

        ensureNotificationPermission()
        ensureAudioPermission()
        createMessageChannel()

        setContent {
            var isLoggedIn by remember { mutableStateOf(!getStoredUserId().isNullOrBlank()) }
            if (!isLoggedIn) {
                LoginScreen(
                    defaultUserId = BuildConfig.DEFAULT_USER_ID,
                    onLoginSuccess = { id, token ->
                        saveLogin(id, token)
                        isLoggedIn = true
                    }
                )
                return@setContent
            }

            var debugText by remember { mutableStateOf("Ready.") }
            var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
            var inputText by remember { mutableStateOf(TextFieldValue("")) }
            var contextText by remember { mutableStateOf(TextFieldValue("")) }

            var unreadCount by remember { mutableIntStateOf(0) }
            var relationshipMode by remember { mutableStateOf("friendship") }
            var isSending by remember { mutableStateOf(false) }
            var isRecording by remember { mutableStateOf(false) }
            var pendingAssistant by remember { mutableStateOf(false) }
            var assistantTyping by remember { mutableStateOf(false) }
            var settingsOpen by remember { mutableStateOf(false) }
            var networkError by remember { mutableStateOf<String?>(null) }
            val snackbarHostState = remember { SnackbarHostState() }

            val onDebugWithNetwork: (String) -> Unit = { msg ->
                debugText = msg
                val lower = msg.lowercase()

                val isAuth = lower.contains("401") || lower.contains("403") ||
                    lower.contains("unauthorized") || lower.contains("forbidden")

                val isNetwork = lower.contains("timeout") ||
                    lower.contains("timed out") ||
                    lower.contains("unknownhost") ||
                    lower.contains("ssl") ||
                    lower.contains("connectexception") ||
                    lower.contains("failed to connect") ||
                    lower.contains("connection refused") ||
                    lower.contains("no route") ||
                    lower.contains("i/o exception") ||
                    lower.contains("ioexception") ||
                    lower.contains("connect timed out")

                val isErrorish = lower.contains("error") || lower.contains("exception")

                if (isErrorish && (isAuth || isNetwork)) {
                    networkError = if (isAuth) {
                        "Falha na autenticacao. Informe o token JWT (ou entre novamente)."
                    } else {
                        "Falha na conexao com o backend (Render). Verifique a URL e tente novamente."
                    }
                }
            }

            var playingUrl by remember { mutableStateOf<String?>(null) }
            var playbackPositionMs by remember { mutableLongStateOf(0L) }
            var playbackDurationMs by remember { mutableLongStateOf(0L) }
            var lastAutoPlayedAudioUrl by remember { mutableStateOf<String?>(null) }

            var currentActivity by remember { mutableStateOf("...") } // legado, pode manter por enquanto
            var currentPartOfDay by remember { mutableStateOf("...") }
            var currentTimezone by remember { mutableStateOf("America/Sao_Paulo") }

            var preferredUserInput by remember { mutableStateOf("mixed") }
            var preferredAssistantOutput by remember { mutableStateOf("mixed") }
            var voiceAffinityScore by remember { mutableIntStateOf(0) }

            var inactiveDeliveryMode by remember { mutableStateOf("text") }
            var allowBackgroundAudio by remember { mutableStateOf(false) }
            var allowLockscreenAudio by remember { mutableStateOf(false) }
            var insistentMode by remember { mutableStateOf(false) }
            var pseudoSyncEnabled by remember { mutableStateOf(false) }
            var respondToVoices by remember { mutableStateOf("only_me") }

            var relationshipModeDraft by remember { mutableStateOf("friendship") }
            var inactiveDeliveryModeDraft by remember { mutableStateOf("text") }
            var allowBackgroundAudioDraft by remember { mutableStateOf(false) }
            var allowLockscreenAudioDraft by remember { mutableStateOf(false) }
            var insistentModeDraft by remember { mutableStateOf(false) }
            var pseudoSyncEnabledDraft by remember { mutableStateOf(false) }
            var respondToVoicesDraft by remember { mutableStateOf("only_me") }

            var voiceProfilesScreenOpen by remember { mutableStateOf(false) }
            var voiceEnrollmentSlot by remember { mutableStateOf<VoiceSlot?>(null) }
            var voiceProfilesList by remember { mutableStateOf<List<VoiceSlot>>(emptyList()) }

            var personalityModesScreenOpen by remember { mutableStateOf(false) }
            var personalityModes by remember { mutableStateOf<Map<String, Int?>>(emptyMap()) }
            var personalityModeLabels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

            var interruptionsEnabled by remember { mutableStateOf(true) }
            var scarcityLevel by remember { mutableFloatStateOf(40f) }
            var inconvenienceLevel by remember { mutableFloatStateOf(35f) }

            val listState = rememberLazyListState()

            LaunchedEffect(messages.size, assistantTyping) {
                if (messages.isNotEmpty()) {
                    val extra = if (assistantTyping) 1 else 0
                    listState.animateScrollToItem((messages.size - 1 + extra).coerceAtLeast(0))
                }
            }

            // Autoplay for assistant voice messages (normal chat).
            // Pseudo-sync already plays audio inside the foreground service.
            LaunchedEffect(messages.size, playingUrl, pseudoSyncEnabled, allowBackgroundAudio, allowLockscreenAudio, isAppInForeground) {
                if (pseudoSyncEnabled) return@LaunchedEffect
                if (playingUrl != null) return@LaunchedEffect
                if (!isAppInForeground && !allowBackgroundAudio && !allowLockscreenAudio) return@LaunchedEffect

                val candidate = messages.lastOrNull { msg ->
                    msg.role == "assistant" &&
                        msg.modality == "voice" &&
                        !msg.audioUrl.isNullOrBlank()
                } ?: return@LaunchedEffect

                val url = candidate.audioUrl ?: return@LaunchedEffect
                if (url == lastAutoPlayedAudioUrl) return@LaunchedEffect

                lastAutoPlayedAudioUrl = url
                playAudio(
                    url = url,
                    speechMeta = candidate.speechMeta,
                    onStarted = { duration ->
                        playingUrl = url
                        playbackDurationMs = duration
                        playbackPositionMs = 0L
                        debugText = "Playing audio (auto)"
                    },
                    onProgress = { position, duration ->
                        if (playingUrl == url) {
                            playbackPositionMs = position
                            playbackDurationMs = duration
                        }
                    },
                    onFinished = {
                        if (playingUrl == url) {
                            playingUrl = null
                            playbackPositionMs = 0L
                            playbackDurationMs = 0L
                        }
                    },
                    onDebug = onDebugWithNetwork
                )
            }

            LaunchedEffect(Unit) {
                testBackendConnectivity(
                    onOk = { onDebugWithNetwork(it) },
                    onError = { err ->
                        debugText = err
                        networkError = err
                    }
                )

                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        debugText = "TOKEN ERROR: ${task.exception?.message}"
                        return@addOnCompleteListener
                    }

                    registerToken(
                        token = task.result,
                        onDebug = onDebugWithNetwork
                    )
                }

                loadChat(
                    onResult = { loaded -> messages = loaded },
                    onUnread = { unreadCount = it },
                    onRelationshipMode = {
                        relationshipMode = it
                        relationshipModeDraft = it
                    },
                    onPending = { pendingAssistant = it },
                    onTyping = { assistantTyping = it },
                    onDeliveryLoaded = { mode, bgAudio, lockAudio, insist, pseudoSync, respondTo ->
                        inactiveDeliveryMode = mode
                        allowBackgroundAudio = bgAudio
                        allowLockscreenAudio = lockAudio
                        insistentMode = insist
                        pseudoSyncEnabled = pseudoSync
                        respondToVoices = respondTo

                        inactiveDeliveryModeDraft = mode
                        allowBackgroundAudioDraft = bgAudio
                        allowLockscreenAudioDraft = lockAudio
                        insistentModeDraft = insist
                        pseudoSyncEnabledDraft = pseudoSync
                        respondToVoicesDraft = respondTo
                        updatePseudoSyncService(pseudoSync)
                    },
                    onTemporalLoaded = { partOfDay, timezone ->
                        currentPartOfDay = partOfDay
                        currentTimezone = timezone
                    },
                    onDebug = onDebugWithNetwork
                )

                loadAutonomy(
                    onLoaded = { enabled, scarcity, inconvenience, prefUser, prefAssistant, voiceAffinity ->
                        interruptionsEnabled = enabled
                        scarcityLevel = scarcity.toFloat()
                        inconvenienceLevel = inconvenience.toFloat()
                        preferredUserInput = prefUser
                        preferredAssistantOutput = prefAssistant
                        voiceAffinityScore = voiceAffinity
                    },
                    onDebug = onDebugWithNetwork
                )

                loadRoutine(
                    onLoaded = { activityText, partOfDay, timezone, voiceAffinity ->
                        currentActivity = activityText
                        currentPartOfDay = partOfDay
                        currentTimezone = timezone
                        voiceAffinityScore = voiceAffinity
                    },
                    onDebug = onDebugWithNetwork
                )
            }

            LaunchedEffect(Unit) {
                var lastUnread = 0
                while (true) {
                    postDeviceState()

                    pollUnread(
                        onUnread = { count, mode, preview, pending, typing, partOfDay, timezone ->
                            val hasNewUnread = count > lastUnread && count > 0

                            if (hasNewUnread && !isAppInForeground && preview.isNotBlank()) {
                                showLocalNotification("Evelyn", preview)
                            }

                            if (hasNewUnread || pending || typing != assistantTyping) {
                                loadChat(
                                    onResult = { loaded -> messages = loaded },
                                    onUnread = { unreadCount = it },
                                    onRelationshipMode = {
                                        relationshipMode = it
                                        relationshipModeDraft = it
                                    },
                                    onPending = { pendingAssistant = it },
                                    onTyping = { assistantTyping = it },
                                    onDeliveryLoaded = { modeDelivery, bgAudio, lockAudio, insist, pseudoSync, respondTo ->
                                        inactiveDeliveryMode = modeDelivery
                                        allowBackgroundAudio = bgAudio
                                        allowLockscreenAudio = lockAudio
                                        insistentMode = insist
                                        pseudoSyncEnabled = pseudoSync
                                        respondToVoices = respondTo

                                        inactiveDeliveryModeDraft = modeDelivery
                                        allowBackgroundAudioDraft = bgAudio
                                        allowLockscreenAudioDraft = lockAudio
                                        insistentModeDraft = insist
                                        pseudoSyncEnabledDraft = pseudoSync
                                        respondToVoicesDraft = respondTo
                                        updatePseudoSyncService(pseudoSync)
                                    },
                                    onTemporalLoaded = { pod, tz ->
                                        currentPartOfDay = pod
                                        currentTimezone = tz
                                    },
                                    onDebug = onDebugWithNetwork
                                )
                            }

                            lastUnread = count
                            unreadCount = count
                            relationshipMode = mode
                            relationshipModeDraft = mode
                            pendingAssistant = pending
                            assistantTyping = typing
                            currentPartOfDay = partOfDay
                            currentTimezone = timezone
                        },
                        onDebug = onDebugWithNetwork
                    )

                    loadRoutine(
                        onLoaded = { activityText, partOfDay, timezone, voiceAffinity ->
                            currentActivity = activityText
                            currentPartOfDay = partOfDay
                            currentTimezone = timezone
                            voiceAffinityScore = voiceAffinity
                        },
                        onDebug = onDebugWithNetwork
                    )

                    delay(1800)
                }
            }

            LaunchedEffect(networkError) {
                val msg = networkError
                if (msg != null) {
                    snackbarHostState.showSnackbar(msg, actionLabel = "OK")
                    networkError = null
                }
            }

            MaterialTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .navigationBarsPadding(),
                    snackbarHost = {
                        SnackbarHost(snackbarHostState) { data ->
                            Snackbar(snackbarData = data)
                        }
                    }
                ) { innerPadding ->

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(Color(0xFFF7F5F2))
                    ) {
                        ChatTopBar(
                            pendingAssistant = pendingAssistant,
                            assistantTyping = assistantTyping,
                            onOpenSettings = { settingsOpen = true }
                        )

                        if (voiceEnrollmentSlot != null) {
                            VoiceEnrollmentScreen(
                                slot = voiceEnrollmentSlot!!,
                                baseUrl = baseUrl,
                                userId = userId,
                                client = client,
                                onStartRecording = { startRecording(onStarted = {}, onError = {}) },
                                onStopRecording = { cb ->
                                    stopRecording(
                                        onStopped = { cb(it) },
                                        onError = { cb(null) }
                                    )
                                },
                                getAudioDurationMs = { file, cb -> getAudioDurationMs(file, cb) },
                                onPlayRecordedFile = { file -> playLocalFile(file, {}) },
                                onBack = { voiceEnrollmentSlot = null },
                                onSuccess = {
                                    voiceEnrollmentSlot = null
                                    loadVoiceProfiles(
                                        onLoaded = { voiceProfilesList = it },
                                        onDebug = onDebugWithNetwork
                                    )
                                },
                                onDebug = onDebugWithNetwork
                            )
                        } else if (voiceProfilesScreenOpen) {
                            LaunchedEffect(voiceProfilesScreenOpen) {
                                loadVoiceProfiles(
                                    onLoaded = { voiceProfilesList = it },
                                    onDebug = onDebugWithNetwork
                                )
                            }
                            VoiceProfilesScreen(
                                slots = voiceProfilesList,
                                onBack = { voiceProfilesScreenOpen = false },
                                onSelectSlot = { voiceEnrollmentSlot = it },
                                onAddKnown = {
                                    val n = voiceProfilesList.count { it.id.startsWith("known_") }
                                    voiceEnrollmentSlot = VoiceSlot(
                                        id = "known_${n + 1}",
                                        title = "Conhecido ${n + 1}",
                                        displayName = "",
                                        type = "verbal",
                                        enrolled = false,
                                        isSelf = false
                                    )
                                }
                            )
                        } else if (personalityModesScreenOpen) {
                            LaunchedEffect(personalityModesScreenOpen) {
                                loadPersonalityModes(
                                    onLoaded = { modes, labels ->
                                        personalityModes = modes
                                        personalityModeLabels = labels
                                    },
                                    onDebug = onDebugWithNetwork
                                )
                            }
                            PersonalityModesScreen(
                                modes = personalityModes,
                                labels = personalityModeLabels,
                                onBack = { personalityModesScreenOpen = false },
                                onSave = { newModes ->
                                    savePersonalityModes(
                                        newModes = newModes,
                                        onSaved = {
                                            personalityModes = newModes
                                            personalityModesScreenOpen = false
                                        },
                                        onDebug = onDebugWithNetwork
                                    )
                                }
                            )
                        } else if (settingsOpen) {
                            SettingsPanel(
                                relationshipMode = relationshipMode,
                                currentActivity = currentActivity,
                                currentPartOfDay = currentPartOfDay,
                                currentTimezone = currentTimezone,
                                inactiveDeliveryMode = inactiveDeliveryMode,
                                allowBackgroundAudio = allowBackgroundAudio,
                                allowLockscreenAudio = allowLockscreenAudio,
                                insistentMode = insistentMode,
                                relationshipModeDraft = relationshipModeDraft,
                                inactiveDeliveryModeDraft = inactiveDeliveryModeDraft,
                                allowBackgroundAudioDraft = allowBackgroundAudioDraft,
                                allowLockscreenAudioDraft = allowLockscreenAudioDraft,
                                insistentModeDraft = insistentModeDraft,
                                pseudoSyncEnabledDraft = pseudoSyncEnabledDraft,
                                respondToVoicesDraft = respondToVoicesDraft,
                                preferredUserInput = preferredUserInput,
                                preferredAssistantOutput = preferredAssistantOutput,
                                voiceAffinityScore = voiceAffinityScore,
                                interruptionsEnabled = interruptionsEnabled,
                                scarcityLevel = scarcityLevel,
                                inconvenienceLevel = inconvenienceLevel,
                                contextText = contextText,
                                debugText = debugText,
                                onClose = { settingsOpen = false },
                                onContextTextChange = { contextText = it },
                                onInterruptionsEnabledChange = { interruptionsEnabled = it },
                                onScarcityChange = { scarcityLevel = it },
                                onInconvenienceChange = { inconvenienceLevel = it },
                                onRelationshipModeDraftChange = { relationshipModeDraft = it },
                                onInactiveDeliveryModeDraftChange = { inactiveDeliveryModeDraft = it },
                                onAllowBackgroundAudioDraftChange = { allowBackgroundAudioDraft = it },
                                onAllowLockscreenAudioDraftChange = { allowLockscreenAudioDraft = it },
                                onInsistentModeDraftChange = { insistentModeDraft = it },
                                onPseudoSyncEnabledDraftChange = { pseudoSyncEnabledDraft = it },
                                onRespondToVoicesDraftChange = { respondToVoicesDraft = it },
                                onSendContext = {
                                    val text = contextText.text.trim()
                                    if (text.isNotBlank()) {
                                        sendContext(text) { debugText = it }
                                        contextText = TextFieldValue("")
                                    }
                                },
                                onSaveAutonomy = {
                                    saveAutonomy(
                                        interruptionsEnabled = interruptionsEnabled,
                                        scarcityLevel = scarcityLevel.toInt(),
                                        inconvenienceLevel = inconvenienceLevel.toInt(),
                                        onDebug = onDebugWithNetwork
                                    )
                                },
                                onSaveRelationshipMode = {
                                    saveRelationshipMode(
                                        mode = relationshipModeDraft,
                                        onSaved = { savedMode ->
                                            relationshipMode = savedMode
                                            relationshipModeDraft = savedMode
                                        },
                                        onDebug = onDebugWithNetwork
                                    )
                                },
                                onOpenVoiceProfiles = { voiceProfilesScreenOpen = true },
                                onOpenPersonalityModes = { personalityModesScreenOpen = true },
                                onSaveDeliveryPreferences = {
                                    saveDeliveryPreferences(
                                        inactiveDeliveryMode = inactiveDeliveryModeDraft,
                                        allowBackgroundAudio = allowBackgroundAudioDraft,
                                        allowLockscreenAudio = allowLockscreenAudioDraft,
                                        insistentMode = insistentModeDraft,
                                        pseudoSyncEnabled = pseudoSyncEnabledDraft,
                                        respondToVoices = respondToVoicesDraft,
                                        onSaved = { mode, bg, lock, insist, pseudoSync, respondTo ->
                                            inactiveDeliveryMode = mode
                                            allowBackgroundAudio = bg
                                            allowLockscreenAudio = lock
                                            insistentMode = insist
                                            pseudoSyncEnabled = pseudoSync
                                            respondToVoices = respondTo

                                            inactiveDeliveryModeDraft = mode
                                            allowBackgroundAudioDraft = bg
                                            allowLockscreenAudioDraft = lock
                                            insistentModeDraft = insist
                                            pseudoSyncEnabledDraft = pseudoSync
                                            respondToVoicesDraft = respondTo
                                            updatePseudoSyncService(pseudoSync)
                                        },
                                        onDebug = onDebugWithNetwork
                                    )
                                },
                                onLogout = {
                                    logout()
                                    isLoggedIn = false
                                }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(messages) { msg ->
                                        ChatBubble(
                                            msg = msg,
                                            isPlaying = playingUrl == msg.audioUrl && !msg.audioUrl.isNullOrBlank(),
                                            playbackPositionMs = if (playingUrl == msg.audioUrl) playbackPositionMs else 0L,
                                            playbackDurationMs = if (playingUrl == msg.audioUrl) playbackDurationMs else 0L,
                                            onToggleAudio = { url, speechMeta ->
                                                if (playingUrl == url) {
                                                    stopAudioInternal()
                                                    playingUrl = null
                                                    playbackPositionMs = 0L
                                                    playbackDurationMs = 0L
                                                    debugText = "Audio stopped"
                                                } else {
                                                    playAudio(
                                                        url = url,
                                                        speechMeta = speechMeta,
                                                        onStarted = { duration ->
                                                            playingUrl = url
                                                            playbackDurationMs = duration
                                                            playbackPositionMs = 0L
                                                            debugText = "Playing audio"
                                                        },
                                                        onProgress = { position, duration ->
                                                            if (playingUrl == url) {
                                                                playbackPositionMs = position
                                                                playbackDurationMs = duration
                                                            }
                                                        },
                                                        onFinished = {
                                                            if (playingUrl == url) {
                                                                playingUrl = null
                                                                playbackPositionMs = 0L
                                                            }
                                                        },
                                                        onDebug = onDebugWithNetwork
                                                    )
                                                }
                                            }
                                        )
                                    }

                                    if (assistantTyping) {
                                        item {
                                            TypingBubble()
                                        }
                                    }
                                }
                            }

                            InputBar(
                                inputText = inputText,
                                isSending = isSending,
                                isRecording = isRecording,
                                onInputTextChange = { inputText = it },
                                onSendText = {
                                    val text = inputText.text.trim()
                                    if (text.isNotEmpty()) {
                                        val localMessage = ChatMessage(
                                            role = "user",
                                            text = text,
                                            tsMs = System.currentTimeMillis(),
                                            audioUrl = null,
                                            modality = "text",
                                            speechMeta = null
                                        )

                                        val snapshotMessages = messages + localMessage
                                        messages = snapshotMessages
                                        inputText = TextFieldValue("")
                                        isSending = true

                                        sendChatMessage(
                                            text = text,
                                            currentMessages = snapshotMessages,
                                            onResult = { updated -> messages = updated },
                                            onUnread = { unreadCount = it },
                                            onRelationshipMode = {
                                                relationshipMode = it
                                                relationshipModeDraft = it
                                            },
                                            onPending = { pendingAssistant = it },
                                            onTyping = { assistantTyping = it },
                                            onRoutine = { activityText, partOfDay, timezone, prefUser, prefAssistant, voiceAffinity ->
                                                currentActivity = activityText
                                                currentPartOfDay = partOfDay
                                                currentTimezone = timezone
                                                preferredUserInput = prefUser
                                                preferredAssistantOutput = prefAssistant
                                                voiceAffinityScore = voiceAffinity
                                            },
                                            onDone = { isSending = false },
                                            onDebug = onDebugWithNetwork
                                        )
                                    }
                                },
                                onStartRecording = {
                                    startRecording(
                                        onStarted = {
                                            isRecording = true
                                            debugText = "Recording started"
                                        },
                                        onError = { debugText = it }
                                    )
                                },
                                onStopRecording = {
                                    stopRecording(
                                        onStopped = { file ->
                                            isRecording = false
                                            debugText = "Sending audio..."
                                            sendAudioMessage(
                                                file = file,
                                                currentMessages = messages,
                                                onResult = { updated -> messages = updated },
                                                onUnread = { unreadCount = it },
                                                onRelationshipMode = {
                                                    relationshipMode = it
                                                    relationshipModeDraft = it
                                                },
                                                onPending = { pendingAssistant = it },
                                                onTyping = { assistantTyping = it },
                                                onRoutine = { activityText, partOfDay, timezone, prefUser, prefAssistant, voiceAffinity ->
                                                    currentActivity = activityText
                                                    currentPartOfDay = partOfDay
                                                    currentTimezone = timezone
                                                    preferredUserInput = prefUser
                                                    preferredAssistantOutput = prefAssistant
                                                    voiceAffinityScore = voiceAffinity
                                                },
                                                onDone = { },
                                                onDebug = onDebugWithNetwork
                                            )
                                        },
                                        onError = {
                                            isRecording = false
                                            debugText = it
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun getOrCreateDeviceId(): String {
        val prefs = getSharedPreferences("evelyn_prefs", MODE_PRIVATE)
        val existing = prefs.getString("device_id", null)
        if (!existing.isNullOrBlank()) return existing

        val created = UUID.randomUUID().toString()
        prefs.edit().putString("device_id", created).apply()
        return created
    }

    private fun currentScreenInteractive(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isInteractive
    }

    private fun parseArticulationPose(obj: JSONObject?): ArticulationPose {
        if (obj == null) {
            return ArticulationPose(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        }
        return ArticulationPose(
            jawOpen = obj.optDouble("jaw_open", 0.0).toFloat(),
            lipRound = obj.optDouble("lip_round", 0.0).toFloat(),
            lipSpread = obj.optDouble("lip_spread", 0.0).toFloat(),
            lipPress = obj.optDouble("lip_press", 0.0).toFloat(),
            tongueTipUp = obj.optDouble("tongue_tip_up", 0.0).toFloat(),
            tongueTipForward = obj.optDouble("tongue_tip_forward", 0.0).toFloat(),
            tongueBodyHigh = obj.optDouble("tongue_body_high", 0.0).toFloat(),
            tongueBodyFront = obj.optDouble("tongue_body_front", 0.0).toFloat(),
            tongueVisible = obj.optDouble("tongue_visible", 0.0).toFloat()
        )
    }

    private fun parseSpeechMeta(obj: JSONObject?): SpeechMetaOut? {
        if (obj == null) return null

        val ipaText = obj.optString("ipa_text", "")
        val timelineJson = obj.optJSONArray("gesture_timeline")
        val frames = mutableListOf<GestureFrame>()

        if (timelineJson != null) {
            for (i in 0 until timelineJson.length()) {
                val item = timelineJson.optJSONObject(i) ?: continue
                frames.add(
                    GestureFrame(
                        tMs = item.optLong("t_ms", 0L),
                        type = item.optString("type", ""),
                        ipa = item.optString("ipa", ""),
                        durationMs = item.optLong("duration_ms", 0L),
                        pose = parseArticulationPose(item.optJSONObject("pose"))
                    )
                )
            }
        }

        return SpeechMetaOut(
            ipaText = ipaText,
            gestureTimeline = frames
        )
    }

    private fun currentGestureFrame(
        timeline: List<GestureFrame>,
        playbackMs: Long
    ): GestureFrame? {
        if (timeline.isEmpty()) return null

        var current: GestureFrame? = timeline.first()

        for (frame in timeline) {
            if (frame.tMs <= playbackMs) {
                current = frame
            } else {
                break
            }
        }
        return current
    }

    private fun appendAssistantMessage(
        currentMessages: List<ChatMessage>,
        text: String,
        modality: String,
        audioUrl: String? = null,
        speechMeta: SpeechMetaOut? = null,
        tsMs: Long = System.currentTimeMillis()
    ): List<ChatMessage> {
        return currentMessages + ChatMessage(
            role = "assistant",
            text = text,
            tsMs = tsMs,
            audioUrl = audioUrl,
            modality = modality,
            speechMeta = speechMeta
        )
    }

    private fun mergeMessagesPreferFresh(
        currentMessages: List<ChatMessage>,
        incomingMessages: List<ChatMessage>
    ): List<ChatMessage> {
        if (incomingMessages.isEmpty()) return currentMessages
        if (currentMessages.isEmpty()) return incomingMessages

        val merged = currentMessages.toMutableList()

        for (incoming in incomingMessages) {
            val alreadyExists = merged.any { existing ->
                existing.role == incoming.role &&
                        existing.text == incoming.text &&
                        existing.audioUrl == incoming.audioUrl &&
                        kotlin.math.abs(existing.tsMs - incoming.tsMs) <= 3000L
            }

            if (!alreadyExists) {
                merged.add(incoming)
            }
        }

        return merged.sortedBy { it.tsMs }
    }

    private fun scheduleAssistantMessages(
        currentMessages: List<ChatMessage>,
        responseMessages: List<AssistantMessageOut>,
        rootSpeechMeta: SpeechMetaOut? = null,
        onMessagesUpdated: (List<ChatMessage>) -> Unit
    ) {
        var base = currentMessages
        var usedRootSpeechMeta = false

        responseMessages.forEach { msg ->
            val delayMs = msg.delayMs.coerceAtLeast(0L)
            val speechMetaForMessage = msg.speechMeta
                ?: (if (!usedRootSpeechMeta && msg.modality == "voice") {
                    usedRootSpeechMeta = true
                    rootSpeechMeta
                } else null)

            Handler(Looper.getMainLooper()).postDelayed({
                base = appendAssistantMessage(
                    currentMessages = base,
                    text = msg.text,
                    modality = msg.modality,
                    audioUrl = msg.audioUrl,
                    speechMeta = speechMetaForMessage,
                    tsMs = System.currentTimeMillis()
                )
                onMessagesUpdated(base)
            }, delayMs)
        }
    }

    private fun postDeviceState() {
        val ts = System.currentTimeMillis()
        val appForeground = isAppInForeground
        val screenInteractive = currentScreenInteractive()

        val json = """
        {
          "user_id": "$userId",
          "device_id": "$deviceId",
          "ts_ms": $ts,
          "event_type": "DEVICE_STATE",
          "payload": {
            "app_foreground": $appForeground,
            "screen_interactive": $screenInteractive
          }
        }
        """.trimIndent()

        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/event")
            .post(body)
            .build()

        Thread {
            try {
                client.newCall(request).execute().close()
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    private fun updatePseudoSyncService(enabled: Boolean) {
        val intent = Intent(this, VoiceCaptureForegroundService::class.java)
        if (enabled) {
            intent.putExtra(VoiceCaptureForegroundService.EXTRA_BASE_URL, baseUrl)
            intent.putExtra(VoiceCaptureForegroundService.EXTRA_USER_ID, userId)
            ContextCompat.startForegroundService(this, intent)
        } else {
            intent.action = VoiceCaptureForegroundService.ACTION_STOP
            startService(intent)
        }
    }

    private fun ensureAudioPermission() {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                1002
            )
        }
    }

    private fun createMessageChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "evelyn_messages",
                "Mensagens da Evelyn",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun showLocalNotification(title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val builder = NotificationCompat.Builder(this, "evelyn_messages")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(shorten(body, 100))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        NotificationManagerCompat.from(this)
            .notify((System.currentTimeMillis() % 100000).toInt(), builder.build())
    }

    private fun startRecording(
        onStarted: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val outputFile = File(cacheDir, "user_audio_${System.currentTimeMillis()}.m4a")
            currentRecordingFile = outputFile

            recorder?.release()
            recorder = null

            val mediaRecorder = MediaRecorder()
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioSamplingRate(44100)
            mediaRecorder.setAudioEncodingBitRate(96000)
            mediaRecorder.setOutputFile(outputFile.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()

            recorder = mediaRecorder
            onStarted()
        } catch (e: Exception) {
            onError("START RECORD ERROR: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun stopRecording(
        onStopped: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            recorder?.apply {
                stop()
                reset()
                release()
            }
            recorder = null

            val file = currentRecordingFile
            currentRecordingFile = null

            if (file == null || !file.exists()) {
                onError("Recording file not found")
                return
            }

            onStopped(file)
        } catch (e: Exception) {
            recorder = null
            onError("STOP RECORD ERROR: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun getAudioDurationMs(file: File, onResult: (Long) -> Unit) {
        Thread {
            try {
                val mp = MediaPlayer()
                mp.setDataSource(file.absolutePath)
                mp.prepare()
                val dur = mp.duration.toLong().coerceAtLeast(0L)
                mp.release()
                Handler(Looper.getMainLooper()).post { onResult(dur) }
            } catch (_: Exception) {
                Handler(Looper.getMainLooper()).post { onResult(0L) }
            }
        }.start()
    }

    fun playLocalFile(file: File, onFinished: () -> Unit) {
        try {
            stopAudioInternal()
            val mp = MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener {
                it.release()
                onFinished()
            }
            mp.setOnErrorListener { _, _, _ -> true }
            mp.prepare()
            mp.start()
            player = mp
        } catch (_: Exception) {
            onFinished()
        }
    }

    private fun stopAudioInternal() {
        playbackProgressRunnable?.let { playbackHandler.removeCallbacks(it) }
        playbackProgressRunnable = null
        activeSpeechMeta = null

        try {
            player?.stop()
        } catch (_: Exception) {
        }

        try {
            player?.release()
        } catch (_: Exception) {
        }

        player = null
        currentlyPlayingUrl = null
    }

    private fun playAudio(
        url: String,
        speechMeta: SpeechMetaOut? = null,
        onStarted: (Long) -> Unit,
        onProgress: (Long, Long) -> Unit,
        onFinished: () -> Unit,
        onDebug: (String) -> Unit
    ) {
        try {
            stopAudioInternal()
            activeSpeechMeta = speechMeta

            val mp = MediaPlayer()
            mp.setDataSource(url)
            mp.setOnPreparedListener { prepared ->
                currentlyPlayingUrl = url
                prepared.start()

                val duration = prepared.duration.toLong().coerceAtLeast(0L)
                onStarted(duration)

                val runnable = object : Runnable {
                    override fun run() {
                        val current = player ?: return
                        if (!current.isPlaying) return

                        val pos = current.currentPosition.toLong().coerceAtLeast(0L)
                        val dur = current.duration.toLong().coerceAtLeast(0L)

                        val frame = currentGestureFrame(activeSpeechMeta?.gestureTimeline.orEmpty(), pos)
                        if (frame != null) {
                            onDebug(
                                "ARTICULATION -> ipa=${frame.ipa} jaw=${frame.pose.jawOpen} " +
                                        "round=${frame.pose.lipRound} spread=${frame.pose.lipSpread} " +
                                        "tongueVisible=${frame.pose.tongueVisible}"
                            )
                        }

                        onProgress(pos, dur)

                        playbackHandler.postDelayed(this, 120L)
                    }
                }

                playbackProgressRunnable = runnable
                playbackHandler.post(runnable)
            }

            mp.setOnCompletionListener {
                playbackProgressRunnable?.let { runnable -> playbackHandler.removeCallbacks(runnable) }
                playbackProgressRunnable = null
                activeSpeechMeta = null

                it.release()
                if (currentlyPlayingUrl == url) {
                    currentlyPlayingUrl = null
                }
                if (player === it) {
                    player = null
                }
                onFinished()
            }

            mp.prepareAsync()
            player = mp
        } catch (e: Exception) {
            onDebug("PLAY AUDIO ERROR: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun registerToken(
        token: String,
        onDebug: (String) -> Unit
    ) {
        val json = """
    {
      "user_id": "$userId",
      "device_id": "$deviceId",
      "fcm_token": "$token"
    }
    """.trimIndent()

        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder()
            .url("$baseUrl/register_token")
            .post(body)
            .build()

        Thread {
            try {
                client.newCall(req).execute().use { resp ->
                    Handler(Looper.getMainLooper()).post {
                        onDebug("TOKEN REGISTER -> ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onDebug("TOKEN ERROR: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }.start()
    }

    private fun sendContext(
        text: String,
        onDebug: (String) -> Unit
    ) {
        val ts = System.currentTimeMillis()

        val json = """
        {
          "user_id": "$userId",
          "device_id": "$deviceId",
          "ts_ms": $ts,
          "text": ${text.escapeJsonString()},
          "source": "manual"
        }
        """.trimIndent()

        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder()
            .url("$baseUrl/context")
            .post(body)
            .build()

        Thread {
            try {
                client.newCall(req).execute().use { resp ->
                    Handler(Looper.getMainLooper()).post {
                        onDebug("CONTEXT SEND -> ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onDebug("CONTEXT ERROR: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }.start()
    }

    private fun saveAutonomy(
        interruptionsEnabled: Boolean,
        scarcityLevel: Int,
        inconvenienceLevel: Int,
        onDebug: (String) -> Unit
    ) {
        val json = """
        {
          "interruptions_enabled": $interruptionsEnabled,
          "scarcity_level": $scarcityLevel,
          "inconvenience_level": $inconvenienceLevel
        }
        """.trimIndent()

        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder()
            .url("$baseUrl/autonomy/$userId")
            .post(body)
            .build()

        Thread {
            try {
                client.newCall(req).execute().use { resp ->
                    Handler(Looper.getMainLooper()).post {
                        onDebug("BEHAVIOR SAVE -> ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onDebug("BEHAVIOR SAVE ERROR: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }.start()
    }

    private fun loadAutonomy(
        onLoaded: (Boolean, Int, Int, String, String, Int) -> Unit,
        onDebug: (String) -> Unit
    ) {
        val req = Request.Builder()
            .url("$baseUrl/autonomy/$userId")
            .get()
            .build()

        Thread {
            try {
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    val obj = JSONObject(body)
                    val auto = obj.getJSONObject("autonomy_settings")
                    val prefs = obj.optJSONObject("channel_preferences")

                    Handler(Looper.getMainLooper()).post {
                        onLoaded(
                            auto.optBoolean("interruptions_enabled", true),
                            auto.optInt("scarcity_level", 40),
                            auto.optInt("inconvenience_level", 35),
                            prefs?.optString("preferred_user_input", "mixed") ?: "mixed",
                            prefs?.optString("preferred_assistant_output", "mixed") ?: "mixed",
                            prefs?.optInt("voice_affinity_score", 0) ?: 0
                        )
                        onDebug("AUTONOMY LOAD -> ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onDebug("AUTONOMY LOAD ERROR: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }.start()
    }

    private fun saveRelationshipMode(
        mode: String,
        onSaved: (String) -> Unit,
        onDebug: (String) -> Unit
    ) {
        val json = """
    {
      "mode": ${mode.escapeJsonString()}
    }
    """.trimIndent()

        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder()
            .url("$baseUrl/relationship_mode/$userId")
            .post(body)
            .build()

        Thread {
            try {
                client.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    val obj = JSONObject(bodyStr)
                    val relationshipStructure = obj.optJSONObject("relationship_structure")
                    val currentMode =
                        relationshipStructure?.optString("current_mode", mode) ?: mode

                    Handler(Looper.getMainLooper()).post {
                        onSaved(currentMode)
                        onDebug("RELATIONSHIP MODE SAVE -> ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onDebug("RELATIONSHIP MODE ERROR: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }.start()
    }

    private fun saveDeliveryPreferences(
        inactiveDeliveryMode: String,
        allowBackgroundAudio: Boolean,
        allowLockscreenAudio: Boolean,
        insistentMode: Boolean,
        pseudoSyncEnabled: Boolean,
        respondToVoices: String,
        onSaved: (String, Boolean, Boolean, Boolean, Boolean, String) -> Unit,
        onDebug: (String) -> Unit
    ) {
        val json = """
    {
      "inactive_delivery_mode": ${inactiveDeliveryMode.escapeJsonString()},
      "allow_background_audio": $allowBackgroundAudio,
      "allow_lockscreen_audio": $allowLockscreenAudio,
      "insistent_mode": $insistentMode,
      "pseudo_sync_enabled": $pseudoSyncEnabled,
      "respond_to_voices": ${respondToVoices.escapeJsonString()}
    }
    """.trimIndent()

        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder()
            .url("$baseUrl/delivery_preferences/$userId")
            .post(body)
            .build()

        Thread {
            try {
                client.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    val obj = JSONObject(bodyStr)
                    val prefs = obj.optJSONObject("delivery_preferences")

                    Handler(Looper.getMainLooper()).post {
                        onSaved(
                            prefs?.optString("inactive_delivery_mode", inactiveDeliveryMode) ?: inactiveDeliveryMode,
                            prefs?.optBoolean("allow_background_audio", allowBackgroundAudio) ?: allowBackgroundAudio,
                            prefs?.optBoolean("allow_lockscreen_audio", allowLockscreenAudio) ?: allowLockscreenAudio,
                            prefs?.optBoolean("insistent_mode", insistentMode) ?: insistentMode,
                            prefs?.optBoolean("pseudo_sync_enabled", pseudoSyncEnabled) ?: pseudoSyncEnabled,
                            prefs?.optString("respond_to_voices", respondToVoices) ?: respondToVoices
                        )
                        onDebug("DELIVERY PREFS SAVE -> ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onDebug("DELIVERY PREFS ERROR: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }.start()
    }

    private fun loadRoutine(
        onLoaded: (String, String, String, Int) -> Unit,
        onDebug: (String) -> Unit
    ) {
        val req = Request.Builder()
            .url("$baseUrl/routine/$userId")
            .get()
            .build()

        Thread {
            try {
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    val obj = JSONObject(body)

                    val legacyRoutine = obj.optJSONObject("daily_routine_legacy")
                    val temporalContext = obj.optJSONObject("temporal_context")
                    val prefs = obj.optJSONObject("channel_preferences")

                    Handler(Looper.getMainLooper()).post {
                        if (!resp.isSuccessful) {
                            onDebug("ROUTINE LOAD FAIL -> ${resp.code}: ${body.take(400)}")
                        }
                        onLoaded(
                            legacyRoutine?.optString("current_activity", "...") ?: "...",
                            temporalContext?.optString("part_of_day", "...") ?: "...",
                            temporalContext?.optString("timezone", "America/Sao_Paulo") ?: "America/Sao_Paulo",
                            prefs?.optInt("voice_affinity_score", 0) ?: 0
                        )
                        onDebug("ROUTINE LOAD -> ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onDebug("ROUTINE LOAD ERROR: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }.start()
    }

    private fun loadChat(
        onResult: (List<ChatMessage>) -> Unit,
        onUnread: (Int) -> Unit,
        onRelationshipMode: (String) -> Unit,
        onPending: (Boolean) -> Unit,
        onTyping: (Boolean) -> Unit,
        onDeliveryLoaded: (String, Boolean, Boolean, Boolean, Boolean, String) -> Unit,
        onTemporalLoaded: (String, String) -> Unit,
        onDebug: (String) -> Unit
    ) {
        val req = Request.Builder()
            .url("$baseUrl/chat/$userId")
            .get()
            .build()

        Thread {
            try {
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    val obj = JSONObject(body)
                    val parsed = parseMessagesFromObject(obj)

                    val relationshipStructure = obj.optJSONObject("relationship_structure")
                    val deliveryPreferences = obj.optJSONObject("delivery_preferences")
                    val temporalContext = obj.optJSONObject("temporal_context")

                    Handler(Looper.getMainLooper()).post {
                        onResult(parsed)
                        onUnread(obj.optInt("unread_assistant_count", 0))
                        onRelationshipMode(
                            relationshipStructure?.optString("current_mode", "friendship") ?: "friendship"
                        )
                        onPending(obj.optBoolean("pending_assistant", false))
                        onTyping(obj.optBoolean("assistant_typing", false))
                        onDeliveryLoaded(
                            deliveryPreferences?.optString("inactive_delivery_mode", "text") ?: "text",
                            deliveryPreferences?.optBoolean("allow_background_audio", false) ?: false,
                            deliveryPreferences?.optBoolean("allow_lockscreen_audio", false) ?: false,
                            deliveryPreferences?.optBoolean("insistent_mode", false) ?: false,
                            deliveryPreferences?.optBoolean("pseudo_sync_enabled", false) ?: false,
                            deliveryPreferences?.optString("respond_to_voices", "only_me") ?: "only_me"
                        )
                        onTemporalLoaded(
                            temporalContext?.optString("part_of_day", "...") ?: "...",
                            temporalContext?.optString("timezone", "America/Sao_Paulo") ?: "America/Sao_Paulo"
                        )
                        onDebug("CHAT LOAD -> ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onDebug("CHAT LOAD ERROR: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }.start()
    }

    private fun loadVoiceProfiles(
        onLoaded: (List<VoiceSlot>) -> Unit,
        onDebug: (String) -> Unit
    ) {
        val req = Request.Builder()
            .url("$baseUrl/voice_profiles/$userId")
            .get()
            .build()
        Thread {
            try {
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    val obj = JSONObject(body)
                    if (!obj.optBoolean("ok", false)) {
                        Handler(Looper.getMainLooper()).post {
                            onDebug("VOICE PROFILES LOAD -> ${resp.code}")
                        }
                        return@use
                    }
                    val owner = obj.optJSONObject("owner")
                    val ownerSlot = VoiceSlot(
                        id = owner?.optString("id", "owner") ?: "owner",
                        title = owner?.optString("title", "Usuário") ?: "Usuário",
                        displayName = owner?.optString("display_name", "") ?: "",
                        type = owner?.optString("type", "verbal") ?: "verbal",
                        enrolled = owner?.optBoolean("enrolled", false) ?: false,
                        isSelf = true
                    )
                    val knownArr = obj.optJSONArray("known") ?: org.json.JSONArray()
                    val knownSlots = mutableListOf<VoiceSlot>()
                    for (i in 0 until knownArr.length()) {
                        val k = knownArr.optJSONObject(i) ?: continue
                        knownSlots.add(
                            VoiceSlot(
                                id = k.optString("id", ""),
                                title = k.optString("title", "Conhecido ${i + 1}"),
                                displayName = k.optString("display_name", ""),
                                type = k.optString("type", "verbal"),
                                enrolled = k.optBoolean("enrolled", false),
                                isSelf = false
                            )
                        )
                    }
                    Handler(Looper.getMainLooper()).post {
                        onLoaded(listOf(ownerSlot) + knownSlots)
                        onDebug("VOICE PROFILES LOAD -> ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onDebug("VOICE PROFILES ERROR: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }.start()
    }

    private fun loadPersonalityModes(
        onLoaded: (Map<String, Int?>, Map<String, String>) -> Unit,
        onDebug: (String) -> Unit
    ) {
        val req = Request.Builder()
            .url("$baseUrl/personality_modes/$userId")
            .get()
            .build()
        Thread {
            try {
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    val obj = JSONObject(body)
                    if (!obj.optBoolean("ok", false)) {
                        Handler(Looper.getMainLooper()).post { onDebug("PERSONALITY MODES LOAD -> ${resp.code}") }
                        return@use
                    }
                    val modesObj = obj.optJSONObject("modes") ?: JSONObject()
                    val labelsObj = obj.optJSONObject("labels") ?: JSONObject()
                    val modes = mutableMapOf<String, Int?>()
                    val labels = mutableMapOf<String, String>()
                    for (key in modesObj.keys()) {
                        val v = modesObj.opt(key)
                        modes[key] = when (v) {
                            null -> null
                            JSONObject.NULL -> null
                            is Number -> v.toInt().coerceIn(0, 100)
                            else -> null
                        }
                    }
                    for (key in labelsObj.keys()) {
                        labels[key] = labelsObj.optString(key, key)
                    }
                    Handler(Looper.getMainLooper()).post {
                        onLoaded(modes, labels)
                        onDebug("PERSONALITY MODES LOAD -> ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onDebug("PERSONALITY MODES ERROR: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }.start()
    }

    private fun savePersonalityModes(
        newModes: Map<String, Int?>,
        onSaved: () -> Unit,
        onDebug: (String) -> Unit
    ) {
        val json = JSONObject()
        newModes.forEach { (k, v) ->
            if (v == null) json.put(k, JSONObject.NULL) else json.put(k, v)
        }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder()
            .url("$baseUrl/personality_modes/$userId")
            .post(body)
            .build()
        Thread {
            try {
                client.newCall(req).execute().use { resp ->
                    Handler(Looper.getMainLooper()).post {
                        onSaved()
                        onDebug("PERSONALITY MODES SAVE -> ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onDebug("PERSONALITY MODES SAVE ERROR: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }.start()
    }

    private fun sendChatMessage(
        text: String,
        currentMessages: List<ChatMessage>,
        onResult: (List<ChatMessage>) -> Unit,
        onUnread: (Int) -> Unit,
        onRelationshipMode: (String) -> Unit,
        onPending: (Boolean) -> Unit,
        onTyping: (Boolean) -> Unit,
        onRoutine: (String, String, String, String, String, Int) -> Unit,
        onDone: () -> Unit,
        onDebug: (String) -> Unit
    ) {
        val json = """
    {
      "text": ${text.escapeJsonString()}
    }
    """.trimIndent()

        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder()
            .url("$baseUrl/chat/$userId/send")
            .post(body)
            .build()

        Thread {
            try {
                client.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    val obj = JSONObject(bodyStr)

                    val parsedChat = parseMessagesFromObject(obj)
                    val responseMessages = parseAssistantMessagesOut(obj)
                    val rootSpeechMeta = parseSpeechMeta(obj.optJSONObject("speech_meta"))
                    val relationshipStructure = obj.optJSONObject("relationship_structure")
                    val temporalContext = obj.optJSONObject("temporal_context")
                    val legacyRoutine = obj.optJSONObject("daily_routine_legacy")
                    val prefs = obj.optJSONObject("channel_preferences")

                    Handler(Looper.getMainLooper()).post {
                        if (responseMessages.isNotEmpty()) {
                            scheduleAssistantMessages(
                                currentMessages = currentMessages,
                                responseMessages = responseMessages,
                                rootSpeechMeta = rootSpeechMeta,
                                onMessagesUpdated = onResult
                            )
                        } else {
                            onResult(mergeMessagesPreferFresh(currentMessages, parsedChat))
                        }

                        onUnread(obj.optInt("unread_assistant_count", 0))
                        onRelationshipMode(
                            relationshipStructure?.optString("current_mode", "friendship") ?: "friendship"
                        )
                        onPending(obj.optBoolean("pending_assistant", false))
                        onTyping(obj.optBoolean("assistant_typing", false))
                        onRoutine(
                            legacyRoutine?.optString("current_activity", "...") ?: "...",
                            temporalContext?.optString("part_of_day", "...") ?: "...",
                            temporalContext?.optString("timezone", "America/Sao_Paulo") ?: "America/Sao_Paulo",
                            prefs?.optString("preferred_user_input", "mixed") ?: "mixed",
                            prefs?.optString("preferred_assistant_output", "mixed") ?: "mixed",
                            prefs?.optInt("voice_affinity_score", 0) ?: 0
                        )
                        onDone()
                        onDebug("CHAT SEND -> ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onDone()
                    onDebug("CHAT SEND ERROR: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }.start()
    }

    private fun sendAudioMessage(
        file: File,
        currentMessages: List<ChatMessage>,
        onResult: (List<ChatMessage>) -> Unit,
        onUnread: (Int) -> Unit,
        onRelationshipMode: (String) -> Unit,
        onPending: (Boolean) -> Unit,
        onTyping: (Boolean) -> Unit,
        onRoutine: (String, String, String, String, String, Int) -> Unit,
        onDone: () -> Unit,
        onDebug: (String) -> Unit
    ) {
        val fileBody = file.asRequestBody("audio/mp4".toMediaType())
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, fileBody)
            .build()

        val req = Request.Builder()
            .url("$baseUrl/chat/$userId/send_audio")
            .post(multipartBody)
            .build()

        Thread {
            try {
                client.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    val obj = JSONObject(bodyStr)

                    if (!obj.optBoolean("ok", false)) {
                        Handler(Looper.getMainLooper()).post {
                            onDone()
                            onDebug("AUDIO SEND ERROR -> ${obj.optString("error", "unknown")}")
                        }
                        return@use
                    }

                    val parsedChat = parseMessagesFromObject(obj)
                    val responseMessages = parseAssistantMessagesOut(obj)
                    val rootSpeechMeta = parseSpeechMeta(obj.optJSONObject("speech_meta"))
                    val relationshipStructure = obj.optJSONObject("relationship_structure")
                    val temporalContext = obj.optJSONObject("temporal_context")
                    val legacyRoutine = obj.optJSONObject("daily_routine_legacy")
                    val prefs = obj.optJSONObject("channel_preferences")

                    Handler(Looper.getMainLooper()).post {
                        if (responseMessages.isNotEmpty()) {
                            scheduleAssistantMessages(
                                currentMessages = currentMessages,
                                responseMessages = responseMessages,
                                rootSpeechMeta = rootSpeechMeta,
                                onMessagesUpdated = onResult
                            )
                        } else {
                            onResult(mergeMessagesPreferFresh(currentMessages, parsedChat))
                        }

                        onUnread(obj.optInt("unread_assistant_count", 0))
                        onRelationshipMode(
                            relationshipStructure?.optString("current_mode", "friendship") ?: "friendship"
                        )
                        onPending(obj.optBoolean("pending_assistant", false))
                        onTyping(obj.optBoolean("assistant_typing", false))
                        onRoutine(
                            legacyRoutine?.optString("current_activity", "...") ?: "...",
                            temporalContext?.optString("part_of_day", "...") ?: "...",
                            temporalContext?.optString("timezone", "America/Sao_Paulo") ?: "America/Sao_Paulo",
                            prefs?.optString("preferred_user_input", "mixed") ?: "mixed",
                            prefs?.optString("preferred_assistant_output", "mixed") ?: "mixed",
                            prefs?.optInt("voice_affinity_score", 0) ?: 0
                        )
                        onDone()
                        onDebug("AUDIO SEND -> ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onDone()
                    onDebug("AUDIO SEND ERROR: ${e.javaClass.simpleName}: ${e.message}")
                }
            } finally {
                try {
                    if (file.exists()) file.delete()
                } catch (_: Exception) {
                }
            }
        }.start()
    }

    private fun pollUnread(
        onUnread: (Int, String, String, Boolean, Boolean, String, String) -> Unit,
        onDebug: (String) -> Unit
    ) {
        val req = Request.Builder()
            .url("$baseUrl/unread/$userId")
            .get()
            .build()

        Thread {
            try {
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    val obj = JSONObject(body)

                    val relationshipStructure = obj.optJSONObject("relationship_structure")
                    val temporalContext = obj.optJSONObject("temporal_context")

                    Handler(Looper.getMainLooper()).post {
                        onUnread(
                            obj.optInt("unread_assistant_count", 0),
                            relationshipStructure?.optString("current_mode", "friendship") ?: "friendship",
                            obj.optString("latest_unread_preview", ""),
                            obj.optInt("pending_replies", 0) > 0,
                            obj.optBoolean("assistant_typing", false),
                            temporalContext?.optString("part_of_day", "...") ?: "...",
                            temporalContext?.optString("timezone", "America/Sao_Paulo") ?: "America/Sao_Paulo"
                        )
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onDebug("UNREAD ERROR: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }.start()
    }

    private fun parseAssistantMessagesOut(obj: JSONObject): List<AssistantMessageOut> {
        val result = mutableListOf<AssistantMessageOut>()
        val arr = obj.optJSONArray("assistant_messages") ?: return emptyList()

        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)

            val audioUrl = if (item.has("audio_url") && !item.isNull("audio_url")) {
                item.getString("audio_url")
            } else {
                null
            }

            result.add(
                AssistantMessageOut(
                    text = item.optString("text", ""),
                    modality = item.optString("modality", if (audioUrl != null) "voice" else "text"),
                    delayMs = item.optLong("delay_ms", 0L),
                    sequenceId = if (item.has("sequence_id") && !item.isNull("sequence_id")) {
                        item.getString("sequence_id")
                    } else {
                        null
                    },
                    audioUrl = audioUrl
                )
            )
        }

        return result
    }

    private fun parseMessagesFromObject(obj: JSONObject): List<ChatMessage> {
        val arr = obj.optJSONArray("messages") ?: return emptyList()
        val result = mutableListOf<ChatMessage>()

        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val audioUrl = if (item.has("audio_url") && !item.isNull("audio_url")) {
                item.getString("audio_url")
            } else {
                null
            }

            val modality = item.optString("modality", if (audioUrl != null) "voice" else "text")
            val speechMeta = parseSpeechMeta(item.optJSONObject("speech_meta"))

            result.add(
                ChatMessage(
                    role = item.getString("role"),
                    text = item.getString("text"),
                    tsMs = item.getLong("ts_ms"),
                    audioUrl = audioUrl,
                    modality = modality,
                    speechMeta = speechMeta
                )
            )
        }

        return result
    }
}

@Composable
private fun ChatTopBar(
    pendingAssistant: Boolean,
    assistantTyping: Boolean,
    onOpenSettings: () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF075E54))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.evelyn_avatar),
                contentDescription = "Evelyn",
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text("Evelyn", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    text = when {
                        assistantTyping -> "Evelyn is typing…"
                        pendingAssistant -> "thinking…"
                        else -> "online"
                    },
                    color = Color(0xFFD7F8F2),
                    fontSize = 12.sp
                )
            }

            Button(onClick = onOpenSettings) {
                Text("Settings")
            }
        }
    }
}

@Composable
private fun InputBar(
    inputText: TextFieldValue,
    isSending: Boolean,
    isRecording: Boolean,
    onInputTextChange: (TextFieldValue) -> Unit,
    onSendText: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .background(Color(0xFFF0EEE9))
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputTextChange,
                    label = { Text("Message") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        autoCorrect = true
                    )
                )

                Spacer(Modifier.width(8.dp))

                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(38.dp))
                } else {
                    Button(onClick = onSendText) {
                        Text("Send")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isRecording) {
                    Button(onClick = onStartRecording) {
                        Text("Voice")
                    }
                } else {
                    Button(onClick = onStopRecording) {
                        Text("Stop")
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(0.45f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SoundWaveMini(active = true)
                Spacer(Modifier.width(8.dp))
                Text("Evelyn is typing…", color = Color.Gray)
            }
        }
    }
}

@Composable
private fun ChatBubble(
    msg: ChatMessage,
    isPlaying: Boolean,
    playbackPositionMs: Long,
    playbackDurationMs: Long,
    onToggleAudio: (String, SpeechMetaOut?) -> Unit
) {
    val isUser = msg.role == "user"
    val bubbleColor = if (isUser) Color(0xFFDCF8C6) else Color.White

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(0.82f)
        ) {
            Column(Modifier.padding(10.dp)) {
                if (msg.audioUrl.isNullOrBlank()) {
                    Text(
                        text = msg.text,
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    AudioMessageBubble(
                        text = msg.text,
                        isPlaying = isPlaying,
                        playbackPositionMs = playbackPositionMs,
                        playbackDurationMs = playbackDurationMs,
                        onToggle = { onToggleAudio(msg.audioUrl, msg.speechMeta) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioMessageBubble(
    text: String,
    isPlaying: Boolean,
    playbackPositionMs: Long,
    playbackDurationMs: Long,
    onToggle: () -> Unit
) {
    val progress = if (playbackDurationMs > 0L) {
        (playbackPositionMs.toFloat() / playbackDurationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onToggle) {
                Text(if (isPlaying) "Pause" else "Play")
            }

            Spacer(Modifier.width(8.dp))

            SoundWaveMini(active = isPlaying)

            Spacer(Modifier.width(8.dp))

            Text(
                text = formatDuration(
                    if (isPlaying && playbackPositionMs > 0L) playbackPositionMs else playbackDurationMs
                ),
                color = Color.Gray,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF444444)
        )
    }
}

@Composable
private fun SoundWaveMini(active: Boolean) {
    val infinite = rememberInfiniteTransition(label = "wave")
    val a1 by infinite.animateFloat(
        initialValue = 8f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 700
                20f at 250
                10f at 500
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "a1"
    )
    val a2 by infinite.animateFloat(
        initialValue = 12f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 700
                24f at 180
                12f at 430
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "a2"
    )
    val a3 by infinite.animateFloat(
        initialValue = 10f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 700
                18f at 300
                9f at 560
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "a3"
    )
    val a4 by infinite.animateFloat(
        initialValue = 14f,
        targetValue = 26f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 700
                26f at 200
                13f at 470
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "a4"
    )

    val bars = if (active) listOf(a1, a2, a3, a4) else listOf(8f, 12f, 10f, 14f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        bars.forEach { h ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF34B7F1))
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000L).toInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun String.escapeJsonString(): String {
    val escaped = this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    return "\"$escaped\""
}

private fun shorten(text: String, max: Int): String {
    return if (text.length <= max) {
        text
    } else {
        text.substring(0, max).trimEnd() + "..."
    }
}