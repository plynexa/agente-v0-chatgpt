package com.plynexa.agent.core.skill

import com.plynexa.agent.core.agent.AgentStateStore
import com.plynexa.agent.core.memory.MemoryManager
import com.plynexa.agent.core.model.MemoryType
import com.plynexa.agent.core.model.SkillPermission
import com.plynexa.agent.core.util.TimeProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class EchoSkill(timeProvider: TimeProvider) : AgentSkill {
    override val definition = SkillDefinition(
        "echo", "Echo", "1.0.0", "Returns input without an external provider.",
        setOf("echo"), emptySet(), installedAt = timeProvider.nowEpochMillis(),
    )
    override suspend fun execute(request: SkillRequest) = SkillResult(true, request.input)
}

class SystemStatusSkill(
    private val stateStore: AgentStateStore,
    timeProvider: TimeProvider,
) : AgentSkill {
    override val definition = SkillDefinition(
        "system-status", "System Status", "1.0.0", "Reads local Agent Core status.",
        setOf("status"), emptySet(), installedAt = timeProvider.nowEpochMillis(),
    )
    override suspend fun execute(request: SkillRequest) = SkillResult(
        true, Json.encodeToString(stateStore.status.value), mapOf("local" to "true"),
    )
}

class MemorySkill(
    private val memoryManager: MemoryManager,
    timeProvider: TimeProvider,
) : AgentSkill {
    override val definition = SkillDefinition(
        "memory", "Memory", "1.0.0", "Reads and writes local typed memory.",
        setOf("search", "remember"), setOf(SkillPermission.READ_MEMORY, SkillPermission.WRITE_MEMORY),
        installedAt = timeProvider.nowEpochMillis(),
    )

    override fun permissionsFor(request: SkillRequest): Set<SkillPermission> = when (request.action) {
        "remember" -> setOf(SkillPermission.WRITE_MEMORY)
        else -> setOf(SkillPermission.READ_MEMORY)
    }

    override suspend fun execute(request: SkillRequest): SkillResult = when (request.action) {
        "search" -> {
            val matches = memoryManager.search(request.input)
            SkillResult(true, matches.joinToString("\n") { it.memory.content }, mapOf("count" to matches.size.toString()))
        }
        "remember" -> {
            val type = request.arguments["type"]?.let(MemoryType::valueOf) ?: MemoryType.SEMANTIC
            val memory = memoryManager.remember(request.input, type)
            SkillResult(true, memory.content, mapOf("memoryId" to memory.id))
        }
        else -> SkillResult(false, "Unsupported memory action: ${request.action}")
    }
}

interface ReminderPort {
    suspend fun create(text: String, triggerAt: Long, conversationId: String?): String
    fun listOpen(): List<String>
}

class ReminderSkill(
    private val reminders: ReminderPort,
    timeProvider: TimeProvider,
) : AgentSkill {
    override val definition = SkillDefinition(
        "reminder", "Reminder", "1.0.0", "Creates and lists durable local reminders.",
        setOf("create", "list"), setOf(SkillPermission.NOTIFICATIONS),
        installedAt = timeProvider.nowEpochMillis(),
    )
    override suspend fun execute(request: SkillRequest): SkillResult = when (request.action) {
        "create" -> {
            val triggerAt = requireNotNull(request.arguments["triggerAt"]?.toLongOrNull()) { "triggerAt is required" }
            SkillResult(true, reminders.create(request.input, triggerAt, request.conversationId))
        }
        "list" -> SkillResult(true, reminders.listOpen().joinToString("\n"))
        else -> SkillResult(false, "Unsupported reminder action: ${request.action}")
    }
}
