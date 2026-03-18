package com.example.contextagent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    defaultUserId: String,
    onLoginSuccess: (userId: String, token: String) -> Unit
) {
    var userIdInput by remember { mutableStateOf(defaultUserId) }
    var tokenInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F5F2))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Evelyn",
            style = MaterialTheme.typography.headlineLarge,
            color = Color(0xFF2D2D2D)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Entre para continuar",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = userIdInput,
            onValueChange = { userIdInput = it; errorMessage = null },
            label = { Text("ID do usuário ou e-mail") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = tokenInput,
            onValueChange = { tokenInput = it; errorMessage = null },
            label = { Text("Token (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val id = userIdInput.trim()
                if (id.isBlank()) {
                    errorMessage = "Informe o ID do usuário."
                    return@Button
                }
                onLoginSuccess(id, tokenInput.trim())
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Entrar")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Entrar com Google em breve.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}
