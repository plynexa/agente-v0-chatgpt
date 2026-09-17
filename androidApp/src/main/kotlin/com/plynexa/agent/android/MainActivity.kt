package com.plynexa.agent.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plynexa.agent.android.host.AgentHostService
import com.plynexa.agent.android.host.AgentHostState
import com.plynexa.agent.android.host.HostUiState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AgentHostScreen() }
    }
}

@Composable
private fun AgentHostScreen() {
    val context = LocalContext.current
    val state by AgentHostState.state.collectAsStateWithLifecycle()
    var confirmRestore by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results[Manifest.permission.RECORD_AUDIO] == true) {
            sendServiceAction(context, AgentHostService.ACTION_ENABLE_VOICE)
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Background, surface = Surface)) {
        if (confirmRestore) {
            AlertDialog(
                onDismissRequest = { confirmRestore = false },
                title = { Text("Restaurar último backup?") },
                text = { Text("O Agent Core será reiniciado e os dados atuais serão substituídos pelo snapshot validado.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmRestore = false
                        sendServiceAction(context, AgentHostService.ACTION_RESTORE_LATEST)
                    }) { Text("Restaurar") }
                },
                dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancelar") } },
            )
        }
        Column(
            Modifier.fillMaxSize().background(Background).verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Text("AGENT V0", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Text("Galaxy S10 Host", color = Accent)
            Spacer(Modifier.height(24.dp))
            StatusCard(state)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { sendServiceAction(context, AgentHostService.ACTION_CREATE_BACKUP) },
                    modifier = Modifier.weight(1f),
                ) { Text("Criar backup") }
                OutlinedButton(
                    onClick = { confirmRestore = true },
                    modifier = Modifier.weight(1f),
                ) { Text("Restaurar último") }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val permissions = buildList {
                            add(Manifest.permission.RECORD_AUDIO)
                            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        permissionLauncher.launch(permissions.toTypedArray())
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Ativar voz") }
                OutlinedButton(
                    onClick = { sendServiceAction(context, AgentHostService.ACTION_MUTE) },
                    modifier = Modifier.weight(1f),
                ) { Text("Desligar microfone") }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { requestBatteryExemption(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Configurar otimização de bateria") }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { sendServiceAction(context, AgentHostService.ACTION_ENABLE_PAIRING) },
                    modifier = Modifier.weight(1f),
                ) { Text("Parear Windows") }
                OutlinedButton(
                    onClick = { sendServiceAction(context, AgentHostService.ACTION_DISABLE_LAN) },
                    modifier = Modifier.weight(1f),
                ) { Text("Desativar LAN") }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "A API fica somente no próprio telefone por padrão. Acesso pela rede será liberado após pareamento.",
                color = Color.Gray,
            )
        }
    }
}

@Composable
private fun StatusCard(state: HostUiState) {
    Card(colors = CardDefaults.cardColors(containerColor = Surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.statusText, color = if (state.running) Accent else Color.Gray)
            Text("Voice State: ${state.voiceState}", color = Color.White)
            Text("API: ${state.apiHost}:${state.apiPort}", color = Color.White)
            Text(
                "Reconhecimento local: ${if (state.onDeviceSpeechAvailable) "disponível" else "não confirmado"}",
                color = Color.White,
            )
            state.pairingCode?.let {
                Text("Código de pareamento: $it", color = Accent, style = MaterialTheme.typography.titleLarge)
                Text("Expira em até 5 minutos e aceita no máximo 5 tentativas.", color = Color.Gray)
            }
            Text("LAN: ${if (state.lanEnabled) "habilitada para clientes pareados" else "somente localhost"}", color = Color.White)
            state.error?.let { Text("Erro: $it", color = Color(0xFFFF7A7A)) }
        }
    }
}

private fun sendServiceAction(context: Context, action: String) {
    val intent = Intent(context, AgentHostService::class.java).setAction(action)
    if (action == AgentHostService.ACTION_STOP) context.startService(intent)
    else ContextCompat.startForegroundService(context, intent)
}

private fun requestBatteryExemption(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

private val Background = Color(0xFF080B12)
private val Surface = Color(0xFF131824)
private val Accent = Color(0xFFF59E0B)
