package com.plynexa.agent.core.api

import com.plynexa.agent.core.agent.AgentCore
import com.plynexa.agent.core.backup.BackupProvider
import com.plynexa.agent.core.device.PairingManager
import com.plynexa.agent.core.events.AgentEvent
import com.plynexa.agent.core.events.EventBus
import com.plynexa.agent.core.memory.MemoryManager
import com.plynexa.agent.core.model.AgentTask
import com.plynexa.agent.core.model.Conversation
import com.plynexa.agent.core.model.MemoryType
import com.plynexa.agent.core.model.Message
import com.plynexa.agent.core.model.Project
import com.plynexa.agent.core.model.RankedMemory
import com.plynexa.agent.core.model.Reminder
import com.plynexa.agent.core.reminder.ReminderManager
import com.plynexa.agent.core.project.ProjectManager
import com.plynexa.agent.core.provider.ProviderConfigurationStore
import com.plynexa.agent.core.provider.SaveProviderConfigurationRequest
import com.plynexa.agent.core.runtime.AgentConversationRuntime
import com.plynexa.agent.core.skill.SkillDefinition
import com.plynexa.agent.core.task.TaskManager
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class HealthResponse(val status: String = "ok", val version: String = "v0.3")

@Serializable
data class ChatRequest(val conversationId: String, val content: String, val deviceId: String? = null)

@Serializable
data class ChatResponse(
    val accepted: Boolean,
    val message: Message? = null,
    val reply: Message? = null,
    val error: String? = null,
)

@Serializable
data class CreateConversationRequest(val title: String? = null)

@Serializable
data class UpdateConversationRequest(val title: String)

@Serializable
data class MemorySearchRequest(val query: String, val limit: Int = 20)

@Serializable
data class CreateMemoryRequest(
    val content: String,
    val type: MemoryType = MemoryType.SEMANTIC,
    val importance: Double = 0.5,
    val projectId: String? = null,
    val sourceMessageId: String? = null,
)

@Serializable
data class UpdateMemoryRequest(
    val content: String,
    val type: MemoryType = MemoryType.SEMANTIC,
    val importance: Double = 0.5,
    val projectId: String? = null,
    val metadata: String? = null,
)

@Serializable
data class CreateReminderRequest(val text: String, val triggerAt: Long, val conversationId: String? = null)

@Serializable
data class UpdateReminderRequest(
    val text: String,
    val triggerAt: Long,
    val status: com.plynexa.agent.core.model.ReminderStatus = com.plynexa.agent.core.model.ReminderStatus.SCHEDULED,
    val conversationId: String? = null,
)

@Serializable
data class IdResponse(val id: String)

@Serializable
data class DeviceSummary(
    val id: String,
    val name: String,
    val type: String,
    val status: String,
    val capabilities: Set<String>,
)

@Serializable
data class PairDeviceRequest(
    val code: String,
    val deviceName: String,
    val clientId: String? = null,
    val deviceType: String = "WINDOWS_CLIENT",
    val capabilities: Set<String> = setOf("CHAT", "DASHBOARD", "EVENTS"),
)

@Serializable
data class PairDeviceResponse(val device: DeviceSummary, val token: String)

@Serializable
data class PairingStatusResponse(val enabled: Boolean, val expiresAt: Long? = null)

data class HostApiServices(
    val core: AgentCore,
    val conversationRuntime: AgentConversationRuntime,
    val eventBus: EventBus,
    val memoryManager: MemoryManager,
    val taskManager: TaskManager,
    val reminderManager: ReminderManager,
    val projectManager: ProjectManager? = null,
    val skills: List<SkillDefinition> = emptyList(),
    val pairingManager: PairingManager? = null,
    val backupProvider: BackupProvider? = null,
    val providerConfigurations: ProviderConfigurationStore? = null,
)

fun Application.configureAgentApi(core: AgentCore, conversationRuntime: AgentConversationRuntime) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }
    routing {
        route("/v1") {
            get("/health") { call.respond(HealthResponse()) }
            get("/status") { call.respond(core.stateStore.status.value) }
            post("/chat") {
                val request = call.receive<ChatRequest>()
                if (request.content.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ChatResponse(false, error = "content is required"))
                    return@post
                }
                runCatching {
                    conversationRuntime.process(
                        request.conversationId, request.content,
                        com.plynexa.agent.core.model.MessageSource.API, request.deviceId,
                    )
                }.onSuccess { result ->
                    call.respond(HttpStatusCode.Accepted, ChatResponse(
                        true, message = result.userMessage, reply = result.replyMessage,
                    ))
                }.onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, ChatResponse(false, error = error.message))
                }
            }
        }
    }
}

fun Application.configureHostApi(services: HostApiServices) {
    val jsonCodec = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    install(ContentNegotiation) { json(jsonCodec) }
    install(WebSockets)
    services.pairingManager?.let { pairing ->
        install(Authentication) {
            bearer("device-token") {
                authenticate { credential ->
                    pairing.authenticate(credential.token)?.let { UserIdPrincipal(it.id) }
                }
            }
        }
    }
    routing {
        route("/v1") {
            get("/health") { call.respond(HealthResponse()) }
            get("/pairing/status") {
                val session = services.pairingManager?.pairingStatus()
                call.respond(PairingStatusResponse(session != null, session?.expiresAt))
            }
            post("/pair") {
                val pairing = services.pairingManager
                if (pairing == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@post
                }
                val request = call.receive<PairDeviceRequest>()
                runCatching {
                    pairing.pair(
                        request.code, request.deviceName, request.deviceType,
                        request.capabilities, request.clientId,
                    )
                }.onSuccess { result ->
                    call.respond(HttpStatusCode.Created, PairDeviceResponse(
                        DeviceSummary(
                            result.device.id, result.device.name, result.device.type,
                            result.device.status, result.device.capabilities,
                        ),
                        result.token,
                    ))
                }.onFailure {
                    call.respond(HttpStatusCode.Unauthorized, PairingStatusResponse(false))
                }
            }
        }
        if (services.pairingManager == null) {
            route("/v1") { protectedHostRoutes(services, jsonCodec) }
        } else {
            authenticate("device-token") {
                route("/v1") { protectedHostRoutes(services, jsonCodec) }
            }
        }
    }
}

private fun Route.protectedHostRoutes(services: HostApiServices, jsonCodec: Json) {
    get("/status") { call.respond(services.core.stateStore.status.value) }
    post("/chat") {
        val request = call.receive<ChatRequest>()
        runCatching {
            val deviceId = call.principal<UserIdPrincipal>()?.name ?: request.deviceId
            services.conversationRuntime.process(
                request.conversationId, request.content,
                com.plynexa.agent.core.model.MessageSource.API, deviceId,
            )
        }.onSuccess { result ->
            call.respond(HttpStatusCode.Accepted, ChatResponse(
                true, message = result.userMessage, reply = result.replyMessage,
            ))
        }.onFailure { call.respond(HttpStatusCode.BadRequest, ChatResponse(false, error = it.message)) }
    }
    get("/conversations") { call.respond(services.core.conversationManager.conversations()) }
    post("/conversations") {
        val request = call.receive<CreateConversationRequest>()
        call.respond(HttpStatusCode.Created, services.core.conversationManager.createConversation(request.title))
    }
    get("/conversations/{id}") {
        val id = call.parameters["id"]
        val conversation = id?.let(services.core.conversationManager::conversation)
        if (conversation == null) call.respond(HttpStatusCode.NotFound)
        else call.respond(ConversationWithMessages(conversation, services.core.conversationManager.messages(conversation.id)))
    }
    put("/conversations/{id}") {
        val id = call.parameters["id"].orEmpty()
        val request = call.receive<UpdateConversationRequest>()
        runCatching { services.core.conversationManager.renameConversation(id, request.title) }
            .onSuccess { call.respond(it) }
            .onFailure { call.respond(HttpStatusCode.NotFound) }
    }
    delete("/conversations/{id}") {
        val id = call.parameters["id"].orEmpty()
        runCatching { services.core.conversationManager.deleteConversation(id) }
            .onSuccess { call.respond(HttpStatusCode.Accepted, IdResponse(id)) }
            .onFailure { call.respond(HttpStatusCode.NotFound) }
    }
    get("/memories") {
        val query = call.request.queryParameters["q"].orEmpty()
        call.respond(
            if (query.isBlank()) services.memoryManager.memories().map { RankedMemory(it, 1.0) }
            else services.memoryManager.search(query),
        )
    }
    post("/memories") {
        val request = call.receive<CreateMemoryRequest>()
        call.respond(HttpStatusCode.Created, services.memoryManager.remember(
            request.content, request.type, request.importance, request.projectId, request.sourceMessageId,
        ))
    }
    put("/memories/{id}") {
        val id = call.parameters["id"].orEmpty()
        val request = call.receive<UpdateMemoryRequest>()
        runCatching { services.memoryManager.update(
            id, request.content, request.type, request.importance, request.projectId, request.metadata,
        ) }.onSuccess { call.respond(it) }
            .onFailure { call.respond(HttpStatusCode.NotFound) }
    }
    delete("/memories/{id}") {
        val id = call.parameters["id"].orEmpty()
        runCatching { services.memoryManager.delete(id) }
            .onSuccess { call.respond(HttpStatusCode.Accepted, IdResponse(id)) }
            .onFailure { call.respond(HttpStatusCode.NotFound) }
    }
    post("/memories/search") {
        val request = call.receive<MemorySearchRequest>()
        call.respond(services.memoryManager.search(request.query, request.limit))
    }
    get("/tasks") { call.respond(services.taskManager.tasks()) }
    get("/tasks/{id}") {
        services.taskManager.task(call.parameters["id"].orEmpty())?.let { call.respond(it) }
            ?: call.respond(HttpStatusCode.NotFound)
    }
    delete("/tasks/{id}") {
        val cancelled = services.taskManager.cancel(call.parameters["id"].orEmpty())
        call.respond(if (cancelled) HttpStatusCode.Accepted else HttpStatusCode.NotFound)
    }
    get("/reminders") { call.respond(services.reminderManager.reminders()) }
    post("/reminders") {
        val request = call.receive<CreateReminderRequest>()
        call.respond(HttpStatusCode.Created, IdResponse(services.reminderManager.create(
            request.text, request.triggerAt, request.conversationId,
        )))
    }
    put("/reminders/{id}") {
        val id = call.parameters["id"].orEmpty()
        val request = call.receive<UpdateReminderRequest>()
        runCatching { services.reminderManager.update(
            id, request.text, request.triggerAt, request.status, request.conversationId,
        ) }.onSuccess { call.respond(it) }
            .onFailure { call.respond(HttpStatusCode.BadRequest, it.message.orEmpty()) }
    }
    delete("/reminders/{id}") {
        val id = call.parameters["id"].orEmpty()
        runCatching { services.reminderManager.delete(id) }
            .onSuccess { call.respond(HttpStatusCode.Accepted, IdResponse(id)) }
            .onFailure { call.respond(HttpStatusCode.NotFound) }
    }
    get("/projects") { call.respond(services.projectManager?.projects().orEmpty()) }
    get("/skills") { call.respond(services.skills) }
    get("/providers") { call.respond(services.providerConfigurations?.list().orEmpty()) }
    post("/providers") {
        val store = services.providerConfigurations
        if (store == null) call.respond(HttpStatusCode.NotImplemented)
        else {
            val request = call.receive<SaveProviderConfigurationRequest>()
            runCatching { store.save(request) }
                .onSuccess { call.respond(HttpStatusCode.Created, it) }
                .onFailure { call.respond(HttpStatusCode.BadRequest, it.message.orEmpty()) }
        }
    }
    delete("/providers/{id}") {
        val store = services.providerConfigurations
        if (store == null) call.respond(HttpStatusCode.NotImplemented)
        else {
            store.delete(call.parameters["id"].orEmpty())
            call.respond(HttpStatusCode.Accepted)
        }
    }
    get("/devices") {
        val paired = services.pairingManager?.devices().orEmpty().map {
            DeviceSummary(it.id, it.name, it.type, it.status, it.capabilities)
        }
        call.respond(listOf(DeviceSummary(
            id = "android-host", name = "Galaxy S10 Host", type = "ANDROID_HOST", status = "ONLINE",
            capabilities = setOf("CORE", "SQLITE", "REST", "WEBSOCKET", "VOICE", "REMINDERS"),
        )) + paired)
    }
    get("/backups") { call.respond(services.backupProvider?.list().orEmpty()) }
    post("/backups") {
        val provider = services.backupProvider
        if (provider == null) call.respond(HttpStatusCode.NotImplemented)
        else call.respond(HttpStatusCode.Created, provider.create())
    }
    post("/backups/{id}/restore") {
        val provider = services.backupProvider
        val backup = provider?.list()?.firstOrNull { it.id == call.parameters["id"] }
        if (provider == null) call.respond(HttpStatusCode.NotImplemented)
        else if (backup == null) call.respond(HttpStatusCode.NotFound)
        else {
            provider.restore(backup)
            call.respond(HttpStatusCode.Accepted, IdResponse(backup.id))
        }
    }
    delete("/devices/{id}") {
        services.pairingManager?.revoke(call.parameters["id"].orEmpty())
        call.respond(HttpStatusCode.Accepted)
    }
    webSocket("/events") {
        services.eventBus.events.collect { event: AgentEvent ->
            send(Frame.Text(jsonCodec.encodeToString(event)))
        }
    }
}

@Serializable
data class ConversationWithMessages(val conversation: Conversation, val messages: List<Message>)
