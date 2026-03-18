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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DEFAULT_MODE_ORDER = listOf(
    "miguxes", "ego", "dependente", "implicante", "dramatica", "eletrica",
    "arlequina", "subindo_pelas_paredes", "instavel", "tagarela", "reflexiva", "provocadora"
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
    modes: Map<String, Int>,
    labels: Map<String, String>,
    onBack: () -> Unit,
    onSave: (Map<String, Int>) -> Unit
) {
    val order = if (modes.isEmpty() && labels.isEmpty()) DEFAULT_MODE_ORDER
        else (modes.keys + labels.keys).distinct().sorted()
    val effectiveLabels = if (labels.isEmpty()) DEFAULT_MODE_LABELS else labels
    var draft by remember(modes, order) {
        val initial = order.associateWith { modes[it] ?: 0 }.toMutableMap()
        mutableStateOf(initial)
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
                Button(onClick = onBack) { Text("Voltar") }
                Button(onClick = { onSave(draft) }) { Text("Salvar") }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("0 = desligado, 100 = máximo. Afeta o estilo da fala da personagem.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { draft = order.associateWith { 0 }.toMutableMap() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Resetar para neutro (todos em 0)")
        }
        Spacer(Modifier.height(16.dp))
        order.forEach { key ->
            val label = effectiveLabels[key] ?: key
            val value = draft[key] ?: 0
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    Text("$value", style = MaterialTheme.typography.bodyMedium)
                }
                Slider(
                    value = value.toFloat().coerceIn(0f, 100f),
                    onValueChange = { newVal ->
                        val v = newVal.toInt().coerceIn(0, 100)
                        draft = draft.toMutableMap().apply { put(key, v) }
                    },
                    valueRange = 0f..100f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
