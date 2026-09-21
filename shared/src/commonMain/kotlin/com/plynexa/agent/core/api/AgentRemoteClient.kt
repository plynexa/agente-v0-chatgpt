package com.plynexa.agent.core.api

import com.plynexa.agent.core.events.AgentEvent
import com.plynexa.agent.core.model.AgentStatus
import com.plynexa.agent.core.model.AgentTask
import com.plynexa.agent.core.model.BackupRecord
import com.plynexa.agent.core.model.Conversation
import com.plynexa.agent.core.model.Memory
import com.plynexa.agent.core.model.Project
import com.plynexa.agent.core.model.RankedMemory
import com.plynexa.agent.core.model.Reminder
import com.plynexa.agent.core.provider.ProviderConfiguration
import com.plynexa.agent.core.provider.SaveProviderConfigurationRequest
import com.plynexa.agent.core.skill.SkillDefinition
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json

enum class RemoteConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }

class AgentRemoteClient(
    private val http: HttpClient,
    baseUrl: String,
    private val deviceId: String = "windows-client",
    private val tokenProvider: () -> String? = { null },
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    private val base = baseUrl.trimEnd('/')
    private val mutableConnection = MutableStateFlow(RemoteConnectionState.DISCONNECTED)
    val connection: StateFlow<RemoteConnectionState> = mutableConnection.asStateFlow()

    suspend fun health(): HealthResponse = http.get(url("/v1/health"), ::authorize).body()
    suspend fun pairingStatus(): PairingStatusResponse = http.get(url("/v1/pairing/status")).body()
    suspend fun pair(code: String, deviceName: String = "Windows Client"): PairDeviceResponse =
        http.post(url("/v1/pair")) {
            contentType(ContentType.Application.Json)
            setBody(PairDeviceRequest(code, deviceName, deviceId))
        }.body()
    suspend fun status(): AgentStatus = http.get(url("/v1/status"), ::authorize).body()
    suspend fun conversations(): List<Conversation> = http.get(url("/v1/conversations"), ::authorize).body()
    suspend fun createConversation(title: String?): Conversation = http.post(url("/v1/conversations")) {
        authorize(this); contentType(ContentType.Application.Json); setBody(CreateConversationRequest(title))
    }.body()
    suspend fun updateConversation(id: String, title: String): Conversation = http.put(url("/v1/conversations/$id")) {
        authorize(this); contentType(ContentType.Application.Json); setBody(UpdateConversationRequest(title))
    }.body()
    suspend fun deleteConversation(id: String) { http.delete(url("/v1/conversations/$id"), ::authorize) }
    suspend fun conversation(id: String): ConversationWithMessages =
        http.get(url("/v1/conversations/$id"), ::authorize).body()
    suspend fun chat(conversationId: String, content: String): ChatResponse = http.post(url("/v1/chat")) {
        authorize(this); contentType(ContentType.Application.Json)
        setBody(ChatRequest(conversationId, content, deviceId))
    }.body()
    suspend fun memories(query: String = ""): List<RankedMemory> =
        http.get(url("/v1/memories${if (query.isBlank()) "" else "?q=$query"}"), ::authorize).body()
    suspend fun createMemory(request: CreateMemoryRequest): Memory = http.post(url("/v1/memories")) {
        authorize(this); contentType(ContentType.Application.Json); setBody(request)
    }.body()
    suspend fun updateMemory(id: String, request: UpdateMemoryRequest): Memory = http.put(url("/v1/memories/$id")) {
        authorize(this); contentType(ContentType.Application.Json); setBody(request)
    }.body()
    suspend fun deleteMemory(id: String) { http.delete(url("/v1/memories/$id"), ::authorize) }
    suspend fun tasks(): List<AgentTask> = http.get(url("/v1/tasks"), ::authorize).body()
    suspend fun cancelTask(id: String) { http.delete(url("/v1/tasks/$id"), ::authorize) }
    suspend fun reminders(): List<Reminder> = http.get(url("/v1/reminders"), ::authorize).body()
    suspend fun createReminder(request: CreateReminderRequest): IdResponse = http.post(url("/v1/reminders")) {
        authorize(this); contentType(ContentType.Application.Json); setBody(request)
    }.body()
    suspend fun updateReminder(id: String, request: UpdateReminderRequest): Reminder =
        http.put(url("/v1/reminders/$id")) {
            authorize(this); contentType(ContentType.Application.Json); setBody(request)
        }.body()
    suspend fun deleteReminder(id: String) { http.delete(url("/v1/reminders/$id"), ::authorize) }
    suspend fun projects(): List<Project> = http.get(url("/v1/projects"), ::authorize).body()
    suspend fun skills(): List<SkillDefinition> = http.get(url("/v1/skills"), ::authorize).body()
    suspend fun providers(): List<ProviderConfiguration> = http.get(url("/v1/providers"), ::authorize).body()
    suspend fun saveProvider(request: SaveProviderConfigurationRequest): ProviderConfiguration =
        http.post(url("/v1/providers")) {
            authorize(this); contentType(ContentType.Application.Json); setBody(request)
        }.body()
    suspend fun deleteProvider(id: String) { http.delete(url("/v1/providers/$id"), ::authorize) }
    suspend fun devices(): List<DeviceSummary> = http.get(url("/v1/devices"), ::authorize).body()
    suspend fun revokeDevice(id: String) { http.delete(url("/v1/devices/$id"), ::authorize) }
    suspend fun backups(): List<BackupRecord> = http.get(url("/v1/backups"), ::authorize).body()
    suspend fun createBackup(): BackupRecord = http.post(url("/v1/backups")) { authorize(this) }.body()
    suspend fun restoreBackup(id: String): IdResponse = http.post(url("/v1/backups/$id/restore")) {
        authorize(this)
    }.body()

    fun events(retryDelayMillis: Long = 1_000): Flow<AgentEvent> = flow {
        var firstAttempt = true
        while (currentCoroutineContext().isActive) {
            mutableConnection.value = if (firstAttempt) RemoteConnectionState.CONNECTING else RemoteConnectionState.RECONNECTING
            try {
                http.webSocket(urlString = webSocketUrl("/v1/events"), request = { authorize(this) }) {
                    mutableConnection.value = RemoteConnectionState.CONNECTED
                    firstAttempt = false
                    for (frame in incoming) if (frame is Frame.Text) {
                        emit(json.decodeFromString<AgentEvent>(frame.readText()))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                mutableConnection.value = RemoteConnectionState.RECONNECTING
            }
            delay(retryDelayMillis)
        }
        mutableConnection.value = RemoteConnectionState.DISCONNECTED
    }

    private fun authorize(builder: HttpRequestBuilder) {
        tokenProvider()?.takeIf(String::isNotBlank)?.let { builder.header(HttpHeaders.Authorization, "Bearer $it") }
    }

    private fun url(path: String) = "$base$path"

    private fun webSocketUrl(path: String): String = when {
        base.startsWith("https://") -> "wss://${base.removePrefix("https://")}$path"
        base.startsWith("http://") -> "ws://${base.removePrefix("http://")}$path"
        else -> "$base$path"
    }
}
