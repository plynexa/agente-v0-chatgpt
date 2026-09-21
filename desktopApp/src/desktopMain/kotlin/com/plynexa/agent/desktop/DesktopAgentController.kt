package com.plynexa.agent.desktop

import com.plynexa.agent.core.api.AgentRemoteClient
import com.plynexa.agent.core.api.CreateMemoryRequest
import com.plynexa.agent.core.api.CreateReminderRequest
import com.plynexa.agent.core.api.UpdateMemoryRequest
import com.plynexa.agent.core.api.UpdateReminderRequest
import com.plynexa.agent.core.api.DeviceSummary
import com.plynexa.agent.core.api.RemoteConnectionState
import com.plynexa.agent.core.events.AgentEvent
import com.plynexa.agent.core.model.AgentStatus
import com.plynexa.agent.core.model.AgentTask
import com.plynexa.agent.core.model.Conversation
import com.plynexa.agent.core.model.Memory
import com.plynexa.agent.core.model.Message
import com.plynexa.agent.core.model.Project
import com.plynexa.agent.core.model.Reminder
import com.plynexa.agent.core.skill.SkillDefinition
import com.plynexa.agent.core.provider.ProviderConfiguration
import com.plynexa.agent.core.provider.SaveProviderConfigurationRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import java.util.prefs.Preferences
import java.util.UUID
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

internal data class DesktopState(
    val endpoint: String = "http://127.0.0.1:8787",
    val connection: RemoteConnectionState = RemoteConnectionState.DISCONNECTED,
    val status: AgentStatus? = null,
    val coreVersion: String? = null,
    val conversations: List<Conversation> = emptyList(),
    val activeConversationId: String? = null,
    val messages: List<Message> = emptyList(),
    val memories: List<Memory> = emptyList(),
    val projects: List<Project> = emptyList(),
    val tasks: List<AgentTask> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val skills: List<SkillDefinition> = emptyList(),
    val devices: List<DeviceSummary> = emptyList(),
    val providers: List<ProviderConfiguration> = emptyList(),
    val events: List<AgentEvent> = emptyList(),
    val pairingEnabled: Boolean = false,
    val discovering: Boolean = false,
    val error: String? = null,
)

internal class DesktopAgentController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferences = Preferences.userRoot().node("com/plynexa/agent-v0")
    private val tokenStore = DesktopTokenStore()
    private val clientId = preferences.get("client_id", null) ?: "windows-${UUID.randomUUID()}".also {
        preferences.put("client_id", it)
    }
    private val http = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
        install(WebSockets)
    }
    private val mutableState = MutableStateFlow(
        DesktopState(endpoint = preferences.get("endpoint", "http://127.0.0.1:8787")),
    )
    val state: StateFlow<DesktopState> = mutableState.asStateFlow()
    private var remote: AgentRemoteClient? = null
    private var eventJob: Job? = null
    private var connectionJob: Job? = null
    private var conversationRefreshJob: Job? = null
    private var memoryRefreshJob: Job? = null
    private var taskRefreshJob: Job? = null
    private var reminderRefreshJob: Job? = null

    fun connect(endpoint: String) {
        val normalized = endpoint.trim().trimEnd('/').let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
        }
        preferences.put("endpoint", normalized)
        eventJob?.cancel()
        connectionJob?.cancel()
        val client = AgentRemoteClient(
            http, normalized, deviceId = clientId, tokenProvider = { tokenStore.get(normalized) },
        )
        remote = client
        mutableState.update { it.copy(endpoint = normalized, error = null) }
        connectionJob = scope.launch {
            client.connection.collectLatest { connection ->
                mutableState.update { it.copy(connection = connection) }
            }
        }
        scope.launch {
            request { client.pairingStatus() }?.let { pairing ->
                mutableState.update { it.copy(pairingEnabled = pairing.enabled) }
            }
            refresh(client)
        }
        eventJob = scope.launch {
            client.events().collect { event ->
                mutableState.update { it.copy(events = (listOf(event) + it.events).take(200)) }
                when {
                    event.type.name.startsWith("MESSAGE_") || event.type.name.startsWith("CONVERSATION_") -> {
                        conversationRefreshJob?.cancel()
                        conversationRefreshJob = scope.launch { delay(120); refreshConversation(client, true) }
                    }
                    event.type.name.startsWith("MEMORY_") -> {
                        memoryRefreshJob?.cancel()
                        memoryRefreshJob = scope.launch { delay(120); refreshMemories(client) }
                    }
                    event.type.name.startsWith("TASK_") -> {
                        taskRefreshJob?.cancel()
                        taskRefreshJob = scope.launch { delay(120); refreshTasks(client) }
                    }
                    event.type.name.startsWith("REMINDER_") -> {
                        reminderRefreshJob?.cancel()
                        reminderRefreshJob = scope.launch { delay(120); refreshReminders(client) }
                    }
                }
            }
        }
    }

    fun selectConversation(id: String) {
        mutableState.update { it.copy(activeConversationId = id) }
        preferences.put("active_conversation", id)
        remote?.let { scope.launch { refreshConversation(it) } }
    }

    fun createConversation(title: String) {
        val client = remote ?: return
        scope.launch {
            request { client.createConversation(title.trim().ifBlank { "Nova conversa" }) }?.let { created ->
                preferences.put("active_conversation", created.id)
                mutableState.update {
                    it.copy(conversations = listOf(created) + it.conversations, activeConversationId = created.id, messages = emptyList())
                }
            }
        }
    }

    fun renameConversation(id: String, title: String) {
        val client = remote ?: return
        if (title.isBlank()) return
        scope.launch {
            request { client.updateConversation(id, title.trim()) }?.let { updated ->
                mutableState.update { state -> state.copy(
                    conversations = state.conversations.map { if (it.id == id) updated else it },
                ) }
            }
        }
    }

    fun deleteConversation(id: String) {
        val client = remote ?: return
        scope.launch {
            request { client.deleteConversation(id) } ?: return@launch
            refresh(client)
        }
    }

    fun sendChat(content: String) {
        val client = remote ?: return
        val conversationId = state.value.activeConversationId ?: return
        if (content.isBlank()) return
        scope.launch {
            request { client.chat(conversationId, content.trim()) }?.let { response ->
                val additions = listOfNotNull(response.message, response.reply)
                mutableState.update { state -> state.copy(
                    messages = (state.messages + additions).distinctBy { it.id }.sortedBy { it.timestamp },
                ) }
            }
        }
    }

    fun createMemory(content: String) {
        val client = remote ?: return
        if (content.isBlank()) return
        scope.launch {
            request { client.createMemory(CreateMemoryRequest(content.trim())) }
            refreshMemories(client)
        }
    }

    fun searchMemories(query: String) {
        val client = remote ?: return
        scope.launch {
            request { client.memories(query).map { it.memory } }
                ?.let { found -> mutableState.update { it.copy(memories = found) } }
        }
    }

    fun updateMemory(memory: Memory, content: String) {
        val client = remote ?: return
        if (content.isBlank()) return
        scope.launch {
            request { client.updateMemory(memory.id, UpdateMemoryRequest(
                content.trim(), memory.type, memory.importance, memory.projectId, memory.metadata,
            )) }
            refreshMemories(client)
        }
    }

    fun deleteMemory(id: String) {
        remote?.let { client -> scope.launch { request { client.deleteMemory(id) }; refreshMemories(client) } }
    }

    fun createReminder(text: String, triggerAt: Long) {
        val client = remote ?: return
        if (text.isBlank()) return
        scope.launch {
            request {
                client.createReminder(CreateReminderRequest(
                    text.trim(), triggerAt,
                    state.value.activeConversationId,
                ))
            }
            refreshReminders(client)
        }
    }

    fun updateReminder(reminder: Reminder, text: String, triggerAt: Long) {
        val client = remote ?: return
        if (text.isBlank()) return
        scope.launch {
            request { client.updateReminder(reminder.id, UpdateReminderRequest(
                text.trim(), triggerAt, reminder.status, reminder.conversationId,
            )) }
            refreshReminders(client)
        }
    }

    fun deleteReminder(id: String) {
        remote?.let { client -> scope.launch { request { client.deleteReminder(id) }; refreshReminders(client) } }
    }

    fun revokeDevice(id: String) {
        remote?.let { client -> scope.launch {
            request { client.revokeDevice(id) }
            request { client.devices() }?.let { devices -> mutableState.update { it.copy(devices = devices) } }
        } }
    }

    fun saveProvider(request: SaveProviderConfigurationRequest) {
        remote?.let { client -> scope.launch {
            request { client.saveProvider(request) }
            refreshProviders(client)
        } }
    }

    fun deleteProvider(id: String) {
        remote?.let { client -> scope.launch { request { client.deleteProvider(id) }; refreshProviders(client) } }
    }

    fun cancelTask(id: String) {
        remote?.let { client -> scope.launch { request { client.cancelTask(id) }; refreshTasks(client) } }
    }

    fun pair(code: String, deviceName: String) {
        val client = remote ?: return
        if (code.isBlank()) return
        scope.launch {
            val response = request { client.pair(code.trim(), deviceName.trim().ifBlank { "Windows Client" }) }
            if (response != null) {
                tokenStore.put(state.value.endpoint, response.token)
                mutableState.update { it.copy(pairingEnabled = false, error = null) }
                connect(state.value.endpoint)
            }
        }
    }

    fun forgetPairing() {
        tokenStore.remove(state.value.endpoint)
        connect(state.value.endpoint)
    }

    fun discoverHost() {
        if (state.value.discovering) return
        scope.launch {
            mutableState.update { it.copy(discovering = true, error = null) }
            val found = discoverAddress()
            mutableState.update { it.copy(discovering = false) }
            if (found == null) mutableState.update { it.copy(error = "Galaxy S10 não encontrado nesta rede local.") }
            else connect("http://$found:8787")
        }
    }

    fun refresh() { remote?.let { scope.launch { refresh(it) } } }

    fun close() {
        eventJob?.cancel()
        connectionJob?.cancel()
        http.close()
        scope.cancel()
    }

    private suspend fun refresh(client: AgentRemoteClient) {
        try {
            val health = client.health()
            var conversations = client.conversations()
            if (conversations.isEmpty()) conversations = listOf(client.createConversation("Conversa principal"))
            val preferred = state.value.activeConversationId ?: preferences.get("active_conversation", null)
            val activeId = preferred?.takeIf { id -> conversations.any { it.id == id } }
                ?: conversations.first().id
            preferences.put("active_conversation", activeId)
            coroutineScope {
                val detail = async { client.conversation(activeId) }
                val status = async { client.status() }
                val memories = async { client.memories().map { ranked -> ranked.memory } }
                val projects = async { client.projects() }
                val tasks = async { client.tasks() }
                val reminders = async { client.reminders() }
                val skills = async { client.skills() }
                val devices = async { client.devices() }
                val providers = async { client.providers() }
            mutableState.update {
                it.copy(
                    status = status.await(), coreVersion = health.version, conversations = conversations,
                    activeConversationId = activeId, messages = detail.await().messages,
                    memories = memories.await(), projects = projects.await(), tasks = tasks.await(),
                    reminders = reminders.await(), skills = skills.await(), devices = devices.await(),
                    providers = providers.await(), error = null,
                )
            }
            }
        } catch (error: Throwable) {
            mutableState.update { it.copy(error = error.message ?: error::class.simpleName) }
        }
    }

    private suspend fun refreshConversation(client: AgentRemoteClient, refreshList: Boolean = false) {
        if (refreshList) request { client.conversations() }?.let { conversations ->
            mutableState.update { it.copy(conversations = conversations) }
        }
        state.value.activeConversationId?.let { id ->
            request { client.conversation(id) }?.let { detail ->
                mutableState.update { it.copy(messages = detail.messages) }
            }
        }
    }

    private suspend fun refreshMemories(client: AgentRemoteClient) {
        request { client.memories().map { it.memory } }?.let { value -> mutableState.update { it.copy(memories = value) } }
    }

    private suspend fun refreshTasks(client: AgentRemoteClient) {
        request { client.tasks() }?.let { value -> mutableState.update { it.copy(tasks = value) } }
    }

    private suspend fun refreshReminders(client: AgentRemoteClient) {
        request { client.reminders() }?.let { value -> mutableState.update { it.copy(reminders = value) } }
    }

    private suspend fun refreshProviders(client: AgentRemoteClient) {
        request { client.providers() }?.let { value -> mutableState.update { it.copy(providers = value) } }
    }

    private suspend fun <T> request(block: suspend () -> T): T? = try {
        block().also { mutableState.update { state -> state.copy(error = null) } }
    } catch (error: Throwable) {
        mutableState.update { it.copy(error = error.message ?: error::class.simpleName) }
        null
    }

    private suspend fun discoverAddress(): String? = coroutineScope {
        val prefixes = buildSet {
            Regex("(?:https?://)?(\\d+\\.\\d+\\.\\d+)\\.\\d+").find(state.value.endpoint)
                ?.groupValues?.getOrNull(1)?.let(::add)
            NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>().filterNot { it.isLoopbackAddress }
                .mapNotNull { it.hostAddress?.substringBeforeLast('.') }.forEach(::add)
        }
        prefixes.asSequence().flatMap { prefix -> (1..254).asSequence().map { "$prefix.$it" } }
            .chunked(32).forEach { batch ->
                val found = batch.map { address -> async(Dispatchers.IO) {
                    address.takeIf {
                        runCatching { Socket().use { socket -> socket.connect(InetSocketAddress(address, 8787), 180) } }.isSuccess
                    }
                } }.mapNotNull { it.await() }.firstOrNull()
                if (found != null) return@coroutineScope found
            }
        null
    }
}
