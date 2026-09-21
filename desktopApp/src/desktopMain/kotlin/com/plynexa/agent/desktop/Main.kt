package com.plynexa.agent.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.plynexa.agent.core.api.RemoteConnectionState
import com.plynexa.agent.core.events.AgentEvent
import com.plynexa.agent.core.model.AgentTask
import com.plynexa.agent.core.model.Memory
import com.plynexa.agent.core.model.Message
import com.plynexa.agent.core.model.Reminder
import com.plynexa.agent.core.model.TaskStatus
import com.plynexa.agent.core.provider.ProviderKind
import com.plynexa.agent.core.provider.SaveProviderConfigurationRequest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Background = Color(0xFF080B12)
private val Surface = Color(0xFF131824)
private val SurfaceRaised = Color(0xFF1A2231)
private val Accent = Color(0xFFF59E0B)
private val Good = Color(0xFF52D3A5)

private enum class Screen(val label: String) {
    DASHBOARD("Dashboard"), CHAT("Chat"), MEMORY("Memória"), PROJECTS("Projetos"),
    SKILLS("Skills"), TASKS("Tarefas"), REMINDERS("Lembretes"), HISTORY("Histórico"),
    DEVICES("Dispositivos"), MODELS("Modelos"), LOGS("Logs"), SETTINGS("Configurações"),
}

fun main() = application {
    val controller = remember { DesktopAgentController() }
    Window(
        onCloseRequest = { controller.close(); exitApplication() },
        title = "Agent V0 ChatGPT — Windows Client",
    ) {
        DisposableEffect(Unit) { onDispose(controller::close) }
        LaunchedEffect(Unit) { controller.connect(controller.state.value.endpoint) }
        MaterialTheme(colorScheme = darkColorScheme(primary = Accent, surface = Surface, background = Background)) {
            AgentDesktop(controller)
        }
    }
}

@Composable
private fun AgentDesktop(controller: DesktopAgentController) {
    val state by controller.state.collectAsState()
    var screen by remember { mutableStateOf(Screen.DASHBOARD) }
    Row(Modifier.fillMaxSize().background(Background)) {
        Navigation(screen, state.connection) { screen = it }
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(screen.label, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text(state.endpoint, color = Color.Gray)
                }
                ConnectionBadge(state.connection)
            }
            state.error?.let { Text("Conexão: $it", color = Color(0xFFFF7A7A), modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(18.dp))
            when (screen) {
                Screen.DASHBOARD -> DashboardView(state, controller::refresh)
                Screen.CHAT -> ChatView(state, controller::sendChat, controller::createConversation)
                Screen.MEMORY -> MemoryView(
                    state.memories, controller::createMemory, controller::searchMemories,
                    controller::updateMemory, controller::deleteMemory,
                )
                Screen.PROJECTS -> SimpleList(state.projects, { it.name }, { it.description ?: it.status })
                Screen.SKILLS -> SimpleList(state.skills, { it.name }, { "${it.version} • ${it.description}" })
                Screen.TASKS -> TasksView(state.tasks, controller::cancelTask)
                Screen.REMINDERS -> RemindersView(
                    state.reminders, controller::createReminder,
                    controller::updateReminder, controller::deleteReminder,
                )
                Screen.HISTORY -> HistoryView(
                    state, controller::selectConversation, controller::createConversation,
                    controller::renameConversation, controller::deleteConversation,
                )
                Screen.DEVICES -> SimpleListWithAction(
                    state.devices, { it.name }, { "${it.status} • ${it.capabilities.joinToString()}" },
                    action = { device -> if (device.id != "android-host") ({ controller.revokeDevice(device.id) }) else null },
                    actionLabel = "Revogar",
                )
                Screen.MODELS -> ModelsView(state, controller::saveProvider, controller::deleteProvider)
                Screen.LOGS -> EventsView(state.events)
                Screen.SETTINGS -> SettingsView(
                    state.endpoint, state.pairingEnabled, state.discovering, controller::connect,
                    controller::discoverHost,
                    controller::pair, controller::forgetPairing,
                )
            }
        }
    }
}

@Composable
private fun Navigation(selected: Screen, connection: RemoteConnectionState, select: (Screen) -> Unit) {
    Column(Modifier.width(218.dp).fillMaxHeight().background(Surface).padding(16.dp)) {
        Text("AGENT V0", color = Accent, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("REMOTE PANEL", color = Color.Gray)
        Spacer(Modifier.height(24.dp))
        Screen.entries.forEach { screen ->
            Text(
                screen.label,
                color = if (screen == selected) Accent else Color.White,
                modifier = Modifier.fillMaxWidth().clickable { select(screen) }.padding(vertical = 8.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text("S10 • ${connection.name}", color = if (connection == RemoteConnectionState.CONNECTED) Good else Color.Gray)
    }
}

@Composable
private fun ConnectionBadge(connection: RemoteConnectionState) {
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceRaised)) {
        Text(
            connection.name,
            color = if (connection == RemoteConnectionState.CONNECTED) Good else Accent,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ColumnScope.DashboardView(state: DesktopState, refresh: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusCard("Agent status", state.status?.state?.name ?: "OFFLINE")
        StatusCard("S10", state.devices.firstOrNull()?.status ?: "OFFLINE")
        StatusCard("Core", state.coreVersion ?: "—")
        StatusCard("Fila", state.tasks.count { it.status == TaskStatus.QUEUED }.toString())
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusCard("Memórias", state.memories.size.toString())
        StatusCard("Tarefas ativas", state.tasks.count { it.status == TaskStatus.RUNNING }.toString())
        StatusCard("Lembretes", state.reminders.size.toString())
        StatusCard("Último backup", state.status?.lastBackupAt?.let(::formatTime) ?: "—")
    }
    Spacer(Modifier.height(20.dp))
    Card(colors = CardDefaults.cardColors(containerColor = Surface), modifier = Modifier.fillMaxWidth().weight(1f)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ÁREA DO AVATAR", color = Color.Gray)
                Text("Agent Core no Galaxy S10", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text("Provider: ${state.status?.providerId ?: "roteamento local"}", color = Accent)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Button(onClick = refresh) { Text("Atualizar estado") }
}

@Composable
private fun ColumnScope.ChatView(state: DesktopState, send: (String) -> Unit, createConversation: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    var newChatTitle by remember { mutableStateOf("") }
    fun submit() {
        val text = input.trim()
        if (text.isNotEmpty() && state.activeConversationId != null) {
            input = ""
            send(text)
        }
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            newChatTitle, { newChatTitle = it }, label = { Text("Nome do novo chat") },
            modifier = Modifier.weight(1f), singleLine = true,
        )
        Button(onClick = {
            createConversation(newChatTitle.ifBlank { "Nova conversa" })
            newChatTitle = ""
        }) { Text("+ Novo chat") }
    }
    Text(
        "Conversa atual: ${state.conversations.firstOrNull { it.id == state.activeConversationId }?.title ?: "—"}",
        color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp),
    )
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.scrollToItem(state.messages.lastIndex)
    }
    Box(Modifier.fillMaxWidth().weight(1f)) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(end = 12.dp),
        ) {
            items(state.messages, key = Message::id) { message ->
                Card(colors = CardDefaults.cardColors(containerColor = if (message.role.name == "USER") SurfaceRaised else Surface)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("${message.role} / ${message.source} • ${formatTime(message.timestamp)}", color = Accent)
                        Text(message.content, color = Color.White)
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            input,
            { input = it },
            label = { Text("Mensagem para o S10") },
            modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                    submit()
                    true
                } else false
            },
            singleLine = false,
            maxLines = 4,
        )
        Button(
            onClick = ::submit,
            enabled = state.activeConversationId != null && input.isNotBlank(),
        ) { Text("Enviar") }
    }
}

@Composable
private fun MemoryView(
    memories: List<Memory>,
    create: (String) -> Unit,
    search: (String) -> Unit,
    update: (Memory, String) -> Unit,
    delete: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(value, { value = it }, label = { Text("Memória ou busca") }, modifier = Modifier.weight(1f), singleLine = true)
        OutlinedButton(onClick = { search(value) }) { Text("Buscar") }
        Button(onClick = { create(value); value = "" }) { Text("Salvar") }
    }
    Spacer(Modifier.height(12.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        items(memories, key = Memory::id) { memory ->
            var editing by remember(memory.id) { mutableStateOf(false) }
            var content by remember(memory.id, memory.updatedAt) { mutableStateOf(memory.content) }
            Card(colors = CardDefaults.cardColors(containerColor = Surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editing) {
                        OutlinedTextField(content, { content = it }, label = { Text("Conteúdo") }, modifier = Modifier.fillMaxWidth())
                    } else Text(memory.content, color = Color.White)
                    Text("${memory.type} • importância ${memory.importance}", color = Color.Gray)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (editing) {
                            Button(onClick = { update(memory, content); editing = false }) { Text("Salvar") }
                            OutlinedButton(onClick = { content = memory.content; editing = false }) { Text("Cancelar") }
                        } else OutlinedButton(onClick = { editing = true }) { Text("Editar") }
                        OutlinedButton(onClick = { delete(memory.id) }) { Text("Excluir") }
                    }
                }
            }
        }
    }
}

@Composable
private fun TasksView(tasks: List<AgentTask>, cancel: (String) -> Unit) {
    SimpleListWithAction(
        tasks,
        { it.description },
        { "${it.status} • ${it.type}${it.progress?.let { p -> " • ${(p * 100).toInt()}%" } ?: ""}" },
        action = { task -> if (task.finishedAt == null) ({ cancel(task.id) }) else null },
        actionLabel = "Cancelar",
    )
}

@Composable
private fun RemindersView(
    reminders: List<Reminder>,
    create: (String, Long) -> Unit,
    update: (Reminder, String, Long) -> Unit,
    delete: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    var dateTime by remember { mutableStateOf(formatDateInput(System.currentTimeMillis() + 10 * 60_000)) }
    var validation by remember { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(value, { value = it }, label = { Text("Novo lembrete") }, modifier = Modifier.weight(1f), singleLine = true)
        OutlinedTextField(dateTime, { dateTime = it }, label = { Text("Data e hora (dd/MM/aaaa HH:mm)") }, modifier = Modifier.width(250.dp), singleLine = true)
        Button(onClick = {
            parseDateInput(dateTime)?.takeIf { it > System.currentTimeMillis() }?.let {
                create(value, it); value = ""; validation = null
            } ?: run { validation = "Informe uma data e hora futuras." }
        }, enabled = value.isNotBlank()) { Text("Criar") }
    }
    validation?.let { Text(it, color = Color(0xFFFF7A7A)) }
    Spacer(Modifier.height(12.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        items(reminders, key = Reminder::id) { reminder ->
            var editing by remember(reminder.id) { mutableStateOf(false) }
            var text by remember(reminder.id, reminder.text) { mutableStateOf(reminder.text) }
            var whenText by remember(reminder.id, reminder.triggerAt) { mutableStateOf(formatDateInput(reminder.triggerAt)) }
            Card(colors = CardDefaults.cardColors(containerColor = Surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editing) {
                        OutlinedTextField(text, { text = it }, label = { Text("Lembrete") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(whenText, { whenText = it }, label = { Text("Data e hora") })
                    } else {
                        Text(reminder.text, color = Color.White)
                        Text("${reminder.status} • ${formatTime(reminder.triggerAt)}", color = Color.Gray)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (editing) {
                            Button(onClick = {
                                parseDateInput(whenText)?.let { update(reminder, text, it); editing = false }
                            }) { Text("Salvar") }
                            OutlinedButton(onClick = { editing = false }) { Text("Cancelar") }
                        } else OutlinedButton(onClick = { editing = true }) { Text("Editar") }
                        OutlinedButton(onClick = { delete(reminder.id) }) { Text("Excluir") }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryView(
    state: DesktopState,
    select: (String) -> Unit,
    create: (String) -> Unit,
    rename: (String, String) -> Unit,
    delete: (String) -> Unit,
) {
    var newTitle by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(newTitle, { newTitle = it }, label = { Text("Nome da conversa") }, modifier = Modifier.weight(1f))
            Button(onClick = { create(newTitle.ifBlank { "Nova conversa" }); newTitle = "" }) { Text("Criar chat") }
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyColumn(Modifier.width(340.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.conversations, key = { it.id }) { conversation ->
                var editing by remember(conversation.id) { mutableStateOf(false) }
                var title by remember(conversation.id, conversation.title) { mutableStateOf(conversation.title.orEmpty()) }
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (conversation.id == state.activeConversationId) SurfaceRaised else Surface),
                    modifier = Modifier.fillMaxWidth().clickable { select(conversation.id) },
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (editing) OutlinedTextField(title, { title = it }, label = { Text("Nome") })
                        else Text(conversation.title ?: conversation.id, color = Color.White)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (editing) Button(onClick = { rename(conversation.id, title); editing = false }) { Text("Salvar") }
                            else OutlinedButton(onClick = { editing = true }) { Text("Renomear") }
                            OutlinedButton(onClick = { delete(conversation.id) }) { Text("Excluir") }
                        }
                    }
                }
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.messages, key = Message::id) { message ->
                Text("${formatTime(message.timestamp)}  ${message.role}: ${message.content}", color = Color.White)
            }
        }
        }
    }
}

@Composable
private fun ModelsView(
    state: DesktopState,
    save: (SaveProviderConfigurationRequest) -> Unit,
    delete: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = Surface), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Adicionar provider compatível com OpenAI", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("Exemplos: servidor local, OpenRouter ou outra API compatível.", color = Color.Gray)
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("URL da API") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(model, { model = it }, label = { Text("Modelo") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    apiKey, { apiKey = it }, label = { Text("Chave (opcional para modelo local)") },
                    modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(),
                )
                Button(
                    onClick = {
                        val id = name.lowercase().replace(Regex("[^a-z0-9_-]"), "-").ifBlank { "custom-provider" }
                        save(SaveProviderConfigurationRequest(
                            id, name, ProviderKind.OPENAI_COMPATIBLE, baseUrl, model,
                            apiKey.takeIf(String::isNotBlank), true,
                        ))
                        apiKey = ""
                    },
                    enabled = name.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank(),
                ) { Text("Salvar configuração") }
            }
        }
        Text("Providers cadastrados", color = Color.White, style = MaterialTheme.typography.titleMedium)
        if (state.providers.isEmpty()) Text("Nenhum provider externo configurado. O agente continua local.", color = Color.Gray)
        state.providers.forEach { provider ->
            Card(colors = CardDefaults.cardColors(containerColor = Surface), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(provider.name, color = Color.White)
                        Text("${provider.model} • ${provider.baseUrl} • chave: ${if (provider.hasApiKey) "protegida" else "não informada"}", color = Color.Gray)
                    }
                    OutlinedButton(onClick = { delete(provider.id) }) { Text("Excluir") }
                }
            }
        }
        Text("As chaves ficam criptografadas no S10 e nunca retornam para o Windows.", color = Color.Gray)
    }
}

@Composable
private fun EventsView(events: List<AgentEvent>) {
    SimpleList(events, ::eventLabel, { event ->
        buildString {
            append(formatTime(event.timestampEpochMillis))
            append(" • ")
            append(event.source)
            event.metadata.entries.take(3).takeIf { it.isNotEmpty() }?.let { values ->
                append(" • ")
                append(values.joinToString { "${it.key}: ${it.value}" })
            }
        }
    })
}

@Composable
private fun SettingsView(
    endpoint: String,
    pairingEnabled: Boolean,
    discovering: Boolean,
    connect: (String) -> Unit,
    discover: () -> Unit,
    pair: (String, String) -> Unit,
    forgetPairing: () -> Unit,
) {
    var value by remember(endpoint) { mutableStateOf(endpoint) }
    var code by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("Windows Client") }
    Card(colors = CardDefaults.cardColors(containerColor = Surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Endereço do Galaxy S10", color = Color.White, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value, { value = it }, label = { Text("http://IP-DO-S10:8787") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { connect(value) }) { Text("Conectar / reconectar") }
            OutlinedButton(onClick = discover, enabled = !discovering) {
                Text(if (discovering) "Procurando S10..." else "Localizar S10 automaticamente")
            }
            Text(
                if (pairingEnabled) "Pareamento aberto no S10. Digite o código exibido no telefone."
                else "No S10, toque em ‘Parear Windows’ para gerar um código de 6 dígitos.",
                color = if (pairingEnabled) Accent else Color.Gray,
            )
            OutlinedTextField(deviceName, { deviceName = it }, label = { Text("Nome deste computador") })
            OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("Código de pareamento") })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { pair(code, deviceName); code = "" }) { Text("Parear") }
                OutlinedButton(onClick = forgetPairing) { Text("Esquecer token local") }
            }
            Text("No Windows, o token é protegido pelo DPAPI do usuário e nunca aparece nos logs.", color = Color.Gray)
        }
    }
}

@Composable
private fun <T> SimpleList(values: List<T>, title: (T) -> String, subtitle: (T) -> String) {
    SimpleListWithAction(values, title, subtitle, { null }, "")
}

@Composable
private fun <T> SimpleListWithAction(
    values: List<T>,
    title: (T) -> String,
    subtitle: (T) -> String,
    action: (T) -> (() -> Unit)?,
    actionLabel: String,
) {
    if (values.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhum item no host.", color = Color.Gray) }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        items(values) { value ->
            Card(colors = CardDefaults.cardColors(containerColor = Surface), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title(value), color = Color.White)
                        Text(subtitle(value), color = Color.Gray)
                    }
                    action(value)?.let { callback -> OutlinedButton(onClick = callback) { Text(actionLabel) } }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(label: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Surface)) {
        Column(Modifier.width(175.dp).padding(16.dp)) {
            Text(label, color = Color.Gray)
            Text(value, color = Accent, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private val dateTimeFormat = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault())
private val dateInputFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
private fun formatTime(epochMillis: Long): String = dateTimeFormat.format(Instant.ofEpochMilli(epochMillis))
private fun formatDateInput(epochMillis: Long): String = dateInputFormat.format(
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime(),
)
private fun parseDateInput(value: String): Long? = runCatching {
    java.time.LocalDateTime.parse(value.trim(), dateInputFormat).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()

private fun eventLabel(event: AgentEvent): String = when (event.type.name) {
    "MESSAGE_RECEIVED" -> "Mensagem recebida"
    "MESSAGE_CREATED", "MESSAGE_PERSISTED" -> "Mensagem salva"
    "CONTEXT_RESOLVING" -> "Contexto sendo analisado"
    "MEMORY_SEARCH_STARTED" -> "Busca na memória iniciada"
    "MEMORY_FOUND" -> "Memória encontrada"
    "MEMORY_CREATED" -> "Memória criada"
    "MEMORY_UPDATED" -> "Memória atualizada"
    "MEMORY_DELETED" -> "Memória excluída"
    "ROUTE_SELECTED" -> "Ação escolhida"
    "RESPONSE_CREATED" -> "Resposta criada"
    "TASK_CREATED" -> "Tarefa criada"
    "TASK_STARTED" -> "Tarefa iniciada"
    "TASK_COMPLETED" -> "Tarefa concluída"
    "TASK_FAILED" -> "Tarefa falhou"
    "REMINDER_CREATED" -> "Lembrete criado"
    "REMINDER_TRIGGERED" -> "Lembrete disparado"
    "REMINDER_UPDATED" -> "Lembrete atualizado"
    "REMINDER_DELETED" -> "Lembrete excluído"
    "DEVICE_CONNECTED" -> "Dispositivo conectado"
    "DEVICE_DISCONNECTED" -> "Dispositivo desconectado"
    "SYNC_STARTED" -> "Sincronização iniciada"
    "SYNC_COMPLETED" -> "Sincronização concluída"
    "SYNC_FAILED" -> "Falha na sincronização"
    else -> event.type.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
}
