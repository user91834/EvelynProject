package com.example.contextagent

import androidx.compose.foundation.background
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

@Composable
fun PersonalityModesScreen(
    modes: Map<String, Int>,
    labels: Map<String, String>,
    onBack: () -> Unit,
    onSave: (Map<String, Int>) -> Unit
) {
    val order = (modes.keys + labels.keys).distinct().sorted()
    var draft by remember(modes) { mutableStateOf(modes.toMutableMap()) }

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
            Text("Modos de personalidade", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBack) { Text("Voltar") }
                Button(onClick = { onSave(draft) }) { Text("Salvar") }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("0 = desligado, 100 = máximo. Afeta o estilo da fala da personagem.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        order.forEach { key ->
            val label = labels[key] ?: key
            val value = draft[key] ?: 0
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    Text("$value", style = MaterialTheme.typography.bodyMedium)
                }
                Slider(
                    value = value.toFloat().coerceIn(0f, 100f),
                    onValueChange = { draft = draft.toMutableMap().apply { put(key, it.toInt().coerceIn(0, 100)) } },
                    valueRange = 0f..100f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
