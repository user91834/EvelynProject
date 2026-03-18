package com.example.contextagent

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

@Composable
fun VoiceEnrollmentScreen(
    slot: VoiceSlot,
    baseUrl: String,
    userId: String,
    client: okhttp3.OkHttpClient,
    onStartRecording: () -> Unit,
    onStopRecording: ((File?) -> Unit) -> Unit,
    getAudioDurationMs: (File, (Long) -> Unit) -> Unit,
    onPlayRecordedFile: (File) -> Unit,
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    onDebug: (String) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(slot.displayName) }
    var isNonVerbal by remember { mutableStateOf(slot.type == "nonverbal") }
    var isRecording by remember { mutableStateOf(false) }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val instructionText = if (isNonVerbal) "Grave a sua voz" else "Grave sua voz lendo esse texto"
    val readingText = if (isNonVerbal) "" else "Olá, estou gravando minha voz para que o sistema possa me reconhecer com mais precisão."

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F5F2))
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(slot.title, style = MaterialTheme.typography.titleLarge)
            Button(onClick = onBack) {
                Text("Voltar")
            }
        }
        Spacer(Modifier.height(16.dp))
        if (slot.isSelf) {
            Text(
                "Esta voz é a sua (usuário do app).",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF1976D2)
            )
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(if (slot.isSelf) "Seu nome" else "Nome do usuário/conhecido") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Não verbal")
            Spacer(Modifier.width(8.dp))
            Switch(checked = isNonVerbal, onCheckedChange = { isNonVerbal = it })
        }
        Spacer(Modifier.height(12.dp))
        Text(instructionText, style = MaterialTheme.typography.bodyMedium)
        if (readingText.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFEEEEEE),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    readingText,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            if (!isRecording) {
                Button(onClick = {
                    isRecording = true
                    recordedFile = null
                    onStartRecording()
                }) {
                    Text("Gravar")
                }
            } else {
                Button(onClick = {
                    isRecording = false
                    onStopRecording { file -> recordedFile = file }
                }) {
                    Text("Parar")
                }
            }
            Button(
                onClick = {
                    val f = recordedFile
                    if (f != null) onPlayRecordedFile(f)
                },
                enabled = recordedFile != null
            ) {
                Text("Ouvir")
            }
            Button(
                onClick = {
                    val n = name.trim()
                    val f = recordedFile
                    if (n.isBlank()) {
                        Toast.makeText(context, "Gravação sem sucesso, tente novamente", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (f == null || !f.exists()) {
                        Toast.makeText(context, "Gravação sem sucesso, tente novamente", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isSaving = true
                    getAudioDurationMs(f) { durationMs ->
                        val minMs = if (isNonVerbal) 1000L else 3000L
                        if (durationMs < minMs) {
                            isSaving = false
                            Toast.makeText(context, "Gravação sem sucesso, tente novamente", Toast.LENGTH_SHORT).show()
                            return@getAudioDurationMs
                        }
                        val multipart = MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("profile_id", slot.id)
                            .addFormDataPart("display_name", n)
                            .addFormDataPart("voice_type", if (isNonVerbal) "nonverbal" else "verbal")
                            .addFormDataPart("file", f.name, f.asRequestBody("audio/mp4".toMediaType()))
                            .build()
                        val req = Request.Builder()
                            .url("$baseUrl/voice_profiles/$userId/enroll")
                            .post(multipart)
                            .build()
                        Thread {
                            try {
                                client.newCall(req).execute().use { resp ->
                                    val ok = resp.isSuccessful
                                    Handler(Looper.getMainLooper()).post {
                                        isSaving = false
                                        if (ok) {
                                            Toast.makeText(context, "Gravação bem sucedida", Toast.LENGTH_SHORT).show()
                                            onSuccess()
                                        } else {
                                            Toast.makeText(context, "Gravação sem sucesso, tente novamente", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Handler(Looper.getMainLooper()).post {
                                    isSaving = false
                                    Toast.makeText(context, "Gravação sem sucesso, tente novamente", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }.start()
                    }
                },
                enabled = !isSaving && recordedFile != null
            ) {
                Text(if (isSaving) "..." else "Salvar")
            }
        }
    }
}
