package com.example.contextagent

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsPanel(
    relationshipMode: String,
    currentActivity: String,
    currentPartOfDay: String,
    currentTimezone: String,
    inactiveDeliveryMode: String,
    allowBackgroundAudio: Boolean,
    allowLockscreenAudio: Boolean,
    insistentMode: Boolean,
    pseudoSyncEnabledDraft: Boolean,
    respondToVoicesDraft: String,
    preferredLanguagesDraft: String,
    relationshipModeDraft: String,
    inactiveDeliveryModeDraft: String,
    allowBackgroundAudioDraft: Boolean,
    allowLockscreenAudioDraft: Boolean,
    insistentModeDraft: Boolean,
    preferredUserInput: String,
    preferredAssistantOutput: String,
    voiceAffinityScore: Int,
    interruptionsEnabled: Boolean,
    scarcityLevel: Float,
    inconvenienceLevel: Float,
    contextText: TextFieldValue,
    debugText: String,
    onClose: () -> Unit,
    onContextTextChange: (TextFieldValue) -> Unit,
    onInterruptionsEnabledChange: (Boolean) -> Unit,
    onScarcityChange: (Float) -> Unit,
    onInconvenienceChange: (Float) -> Unit,
    onRelationshipModeDraftChange: (String) -> Unit,
    onInactiveDeliveryModeDraftChange: (String) -> Unit,
    onAllowBackgroundAudioDraftChange: (Boolean) -> Unit,
    onAllowLockscreenAudioDraftChange: (Boolean) -> Unit,
    onInsistentModeDraftChange: (Boolean) -> Unit,
    onPseudoSyncEnabledDraftChange: (Boolean) -> Unit,
    onRespondToVoicesDraftChange: (String) -> Unit,
    onPreferredLanguagesDraftChange: (String) -> Unit,
    onOpenVoiceProfiles: () -> Unit,
    onOpenPersonalityModes: () -> Unit,
    onSendContext: () -> Unit,
    onSaveAutonomy: () -> Unit,
    onSaveRelationshipMode: () -> Unit,
    onSaveDeliveryPreferences: () -> Unit,
    onLogout: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val prefs = remember(context) { context.getSharedPreferences("evelyn_prefs", Context.MODE_PRIVATE) }
    var jwtToken by remember(prefs) { mutableStateOf(prefs.getString("jwt_token", "") ?: "") }

    val scope = rememberCoroutineScope()
    var deliverySaveJob by remember { mutableStateOf<Job?>(null) }
    var autonomySaveJob by remember { mutableStateOf<Job?>(null) }

    fun scheduleDeliverySave() {
        deliverySaveJob?.cancel()
        deliverySaveJob = scope.launch {
            delay(600)
            onSaveDeliveryPreferences()
        }
    }

    fun scheduleAutonomySave() {
        autonomySaveJob?.cancel()
        autonomySaveJob = scope.launch {
            delay(600)
            onSaveAutonomy()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F5F2))
            .verticalScroll(scrollState)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Conversation settings", style = MaterialTheme.typography.titleLarge)
            Button(onClick = {
                onSaveRelationshipMode()
                onSaveDeliveryPreferences()
                onSaveAutonomy()
                onClose()
            }) {
                Text("Back")
            }
        }

        Spacer(Modifier.height(12.dp))

        if (onLogout != null) {
            SettingsCard("Conta") {
                Button(onClick = onLogout) {
                    Text("Sair")
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        SettingsCard("Token (opcional)") {
            Text("Se o backend exigir autenticação, informe o token JWT. Será enviado em todas as requisições.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = jwtToken,
                onValueChange = { v ->
                    jwtToken = v
                    prefs.edit().putString("jwt_token", v).apply()
                },
                label = { Text("JWT Token") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(12.dp))

        SettingsCard("Vozes") {
            Button(onClick = onOpenVoiceProfiles) {
                Text("Incluir vozes")
            }
        }

        Spacer(Modifier.height(10.dp))

        SettingsCard("Modos de personalidade") {
            Text("Ajuste a intensidade de cada modo (0–100). Afeta o estilo da linguagem da Evelyn.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onOpenPersonalityModes) {
                Text("Configurar modos")
            }
        }

        Spacer(Modifier.height(10.dp))

        SettingsCard("Current internal state") {
            SettingsInfo("Relationship mode", relationshipMode)
            SettingsInfo("Part of day", currentPartOfDay)
            SettingsInfo("Timezone", currentTimezone)
            SettingsInfo("Legacy activity", currentActivity)
            SettingsInfo("Inactive delivery", inactiveDeliveryMode)
            SettingsInfo("Background audio", allowBackgroundAudio.toString())
            SettingsInfo("Lockscreen audio", allowLockscreenAudio.toString())
            SettingsInfo("Insistent mode", insistentMode.toString())
        }

        Spacer(Modifier.height(10.dp))

        SettingsCard("Relationship mode") {
            RelationshipModeSelector(
                selectedMode = relationshipModeDraft,
                onModeSelected = { newMode ->
                    onRelationshipModeDraftChange(newMode)
                    onSaveRelationshipMode()
                }
            )
            Spacer(Modifier.height(8.dp))
            Text("Current saved mode: $relationshipMode", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(10.dp))

        SettingsCard("Delivery preferences") {
            DeliveryModeSelector(
                selectedMode = inactiveDeliveryModeDraft,
                onModeSelected = { newMode ->
                    onInactiveDeliveryModeDraftChange(newMode)
                    scheduleDeliverySave()
                }
            )
            Spacer(Modifier.height(8.dp))
            StatusRow("Allow background audio", allowBackgroundAudioDraft) { checked ->
                onAllowBackgroundAudioDraftChange(checked)
                scheduleDeliverySave()
            }
            StatusRow("Allow lockscreen audio", allowLockscreenAudioDraft) { checked ->
                onAllowLockscreenAudioDraftChange(checked)
                scheduleDeliverySave()
            }
            StatusRow("Insistent mode", insistentModeDraft) { checked ->
                onInsistentModeDraftChange(checked)
                scheduleDeliverySave()
            }
            Spacer(Modifier.height(10.dp))
            SettingsCard("Pseudo-sync conversation") {
                Text(
                    "Runs when app is in background or screen locked. Listens continuously, detects speech, then STT → LLM → TTS → play.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                StatusRow("Pseudo-sync (background/lockscreen)", pseudoSyncEnabledDraft) { checked ->
                    onPseudoSyncEnabledDraftChange(checked)
                    scheduleDeliverySave()
                }
                Spacer(Modifier.height(8.dp))
                Text("Respond to:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "only_me" to "Only my voice",
                        "known_too" to "Known voices too",
                        "unknown_too" to "Unknown too"
                    ).forEach { (value, label) ->
                        Button(
                            onClick = {
                                onRespondToVoicesDraftChange(value)
                                scheduleDeliverySave()
                            }
                        ) {
                            Text(if (respondToVoicesDraft == value) "✓ $label" else label)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            SettingsCard("Idiomas preferenciais") {
                Text(
                    "Códigos de idioma para a conversa (ex: pt-BR, en). A personagem tenderá a responder nesses idiomas quando natural.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = preferredLanguagesDraft,
                    onValueChange = { v ->
                        onPreferredLanguagesDraftChange(v)
                        scheduleDeliverySave()
                    },
                    label = { Text("Ex: pt-BR, en") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("Current saved delivery: $inactiveDeliveryMode", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(10.dp))

        SettingsCard("Channel preference memory") {
            SettingsInfo("Preferred user input", preferredUserInput)
            SettingsInfo("Preferred assistant output", preferredAssistantOutput)
            SettingsInfo("Voice affinity", voiceAffinityScore.toString())
        }

        Spacer(Modifier.height(10.dp))

        SettingsCard("Quick context") {
            OutlinedTextField(
                value = contextText,
                onValueChange = onContextTextChange,
                label = { Text("Example: I'm walking in the park") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    autoCorrect = true
                )
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onSendContext) {
                Text("Send context")
            }
        }

        Spacer(Modifier.height(10.dp))

        SettingsCard("Behavior") {
            StatusRow("Available for interruptions", interruptionsEnabled) { checked ->
                onInterruptionsEnabledChange(checked)
                scheduleAutonomySave()
            }
            Spacer(Modifier.height(8.dp))
            Text("Allowed scarcity: ${scarcityLevel.toInt()}")
            Slider(
                value = scarcityLevel,
                onValueChange = { v ->
                    onScarcityChange(v)
                    scheduleAutonomySave()
                },
                valueRange = 0f..100f
            )
            Spacer(Modifier.height(8.dp))
            Text("Allowed inconvenience: ${inconvenienceLevel.toInt()}")
            Slider(
                value = inconvenienceLevel,
                onValueChange = { v ->
                    onInconvenienceChange(v)
                    scheduleAutonomySave()
                },
                valueRange = 0f..100f
            )
        }

        Spacer(Modifier.height(10.dp))

        SettingsCard("Debug") {
            Text(debugText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SettingsInfo(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun RelationshipModeSelector(
    selectedMode: String,
    onModeSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val options = listOf(
            "friendship" to "Friendship",
            "friends_with_benefits" to "Friends with benefits",
            "open_relationship" to "Open relationship",
            "monogamous_relationship" to "Monogamous relationship"
        )
        options.forEach { (value, label) ->
            Button(onClick = { onModeSelected(value) }) {
                Text(if (selectedMode == value) "✓ $label" else label)
            }
        }
    }
}

@Composable
private fun DeliveryModeSelector(
    selectedMode: String,
    onModeSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf("text", "audio", "both").forEach { mode ->
            Button(onClick = { onModeSelected(mode) }) {
                Text(if (selectedMode == mode) "✓ $mode" else mode)
            }
        }
    }
}

@Composable
fun StatusRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
