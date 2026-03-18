package com.example.contextagent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DEFAULT_MODE_ORDER = listOf(
    "miguxes",
    "ego",
    "dependente",
    "implicante",
    "dramatica",
    "eletrica",
    "arlequina",
    "subindo_pelas_paredes",
    "instavel",
    "tagarela",
    "reflexiva",
    "provocadora"
)

private val DEFAULT_MODE_LABELS = mapOf(
    "miguxes" to "Miguxês",
    "ego" to "Ego",
    "dependente" to "Dependente",
    "implicante" to "Implicante",
    "dramatica" to "Dramática",
    "eletrica" to "Elétrica",
    "arlequina" to "Arlequina",
    "subindo_pelas_paredes" to "Subindo pelas paredes",
    "instavel" to "Instável",
    "tagarela" to "Tagarela",
    "reflexiva" to "Reflexiva",
    "provocadora" to "Provocadora"
)

@Composable
fun PersonalityModesScreen(
    modes: Map<String, Int?>,
    labels: Map<String, String>,
    onBack: () -> Unit,
    onSave: (Map<String, Int?>) -> Unit
) {
    val order = if (modes.isEmpty() && labels.isEmpty()) DEFAULT_MODE_ORDER
    else (modes.keys + labels.keys).distinct().sorted()

    val effectiveLabels = if (labels.isEmpty()) DEFAULT_MODE_LABELS else labels

    // draft[key] == null  -> "não aplicado"
    // draft[key] != null  -> "aplicado" + intensidade 0..100
    var draft by remember(modes, order) {
        val initial = order.associateWith { modes[it] }.toMutableMap()
        mutableStateOf(initial)
    }

    val scope = rememberCoroutineScope()
    var saveJob by remember { mutableStateOf<Job?>(null) }

    fun scheduleSave(snapshot: Map<String, Int?>) {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(650)
            onSave(snapshot)
        }
    }

    fun scheduleSaveCurrent() {
        scheduleSave(draft.toMap())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F5F2))
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Modos de personalidade", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    onSave(draft.toMap())
                    onBack()
                }) { Text("Voltar") }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Cada modo tem 3 estados: não aplicado, aplicado e intensidade (0–100) quando aplicado.",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                draft = order.associateWith { null }.toMutableMap()
                scheduleSaveCurrent()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Neutro (todos não aplicados)")
        }

        Spacer(Modifier.height(16.dp))

        order.forEach { key ->
            val label = effectiveLabels[key] ?: key
            val value = draft[key]
            val applied = value != null

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (applied) "${value}/100" else "não aplicado",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val newVal = draft[key] ?: 50
                            draft = draft.toMutableMap().apply { put(key, newVal) }
                            scheduleSaveCurrent()
                        }
                    ) {
                        Text(if (applied) "Aplicado ✓" else "Aplicar")
                    }
                    Button(
                        onClick = {
                            draft = draft.toMutableMap().apply { put(key, null) }
                            scheduleSaveCurrent()
                        }
                    ) {
                        Text(if (!applied) "Não aplicado ✓" else "Não aplicar")
                    }
                }

                if (applied) {
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = (value ?: 0).toFloat().coerceIn(0f, 100f),
                        onValueChange = { newVal ->
                            val v = newVal.toInt().coerceIn(0, 100)
                            draft = draft.toMutableMap().apply { put(key, v) }
                            scheduleSaveCurrent()
                        },
                        valueRange = 0f..100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Intensidade desativada",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
