package com.example.contextagent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun VoiceProfilesScreen(
    slots: List<VoiceSlot>,
    onBack: () -> Unit,
    onSelectSlot: (VoiceSlot) -> Unit,
    onAddKnown: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F5F2))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Incluir vozes", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onBack) {
                Text("Voltar")
            }
        }
        Spacer(Modifier.height(16.dp))
        slots.forEach { slot ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                onClick = { onSelectSlot(slot) },
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(slot.title, style = MaterialTheme.typography.titleMedium)
                        if (slot.isSelf) {
                            Text(
                                "Sou eu (usuário do app)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF1976D2)
                            )
                        }
                    }
                    Text(
                        slot.statusLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onAddKnown) {
            Text("+ Incluir conhecidos")
        }
    }
}
