package com.plynexa.agent.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AgentState { STANDBY, LISTENING, THINKING, PROCESSING, SPEAKING, MIC_MUTED, ERROR }

@Serializable
enum class MessageRole { USER, AGENT, SYSTEM, TOOL }

@Serializable
enum class MessageSource { CHAT, VOICE, SKILL, API, SYSTEM }

@Serializable
enum class MemoryType { WORKING, EPISODIC, SEMANTIC, PROJECT, PREFERENCE, DECISION, LESSON }

@Serializable
enum class TaskStatus { QUEUED, RUNNING, WAITING, COMPLETED, FAILED, CANCELLED }

@Serializable
enum class VoiceState { STANDBY, LISTENING, THINKING, PROCESSING, SPEAKING, MIC_MUTED, ERROR }

@Serializable
enum class PermissionLevel { SAFE, CONFIRMATION_REQUIRED, RESTRICTED }

@Serializable
enum class ReminderStatus { SCHEDULED, TRIGGERED, COMPLETED, CANCELLED }

@Serializable
enum class SkillPermission {
    READ_MEMORY, WRITE_MEMORY, READ_FILE, WRITE_FILE, NETWORK, MICROPHONE,
    NOTIFICATIONS, EXECUTE_COMMAND, MODIFY_PROJECT, SEND_MESSAGE, DEVICE_CONTROL
}

@Serializable
data class AgentStatus(
    val state: AgentState,
    val activeTasks: Int = 0,
    val queuedTasks: Int = 0,
    val databaseAvailable: Boolean = false,
    val lanEnabled: Boolean = false,
    val providerId: String? = null,
    val lastBackupAt: Long? = null,
)

@Serializable
data class Conversation(
    val id: String,
    val title: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val activeProjectId: String? = null,
    val metadata: String? = null,
)

@Serializable
data class Message(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long,
    val source: MessageSource,
    val deviceId: String? = null,
    val metadata: String? = null,
)

@Serializable
data class ContextSnapshot(
    val conversationId: String,
    val recentMessages: List<Message>,
    val inferredEntities: List<String>,
    val activeProjectId: String? = null,
)

@Serializable
data class Memory(
    val id: String,
    val type: MemoryType,
    val content: String,
    val importance: Double = 0.5,
    val createdAt: Long,
    val updatedAt: Long,
    val sourceMessageId: String? = null,
    val projectId: String? = null,
    val metadata: String? = null,
)

@Serializable
data class RankedMemory(val memory: Memory, val score: Double)

@Serializable
data class Entity(
    val id: String,
    val name: String,
    val normalizedName: String,
    val type: String,
    val aliases: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val metadata: String? = null,
)

@Serializable
data class EntityRelation(
    val id: String,
    val fromEntityId: String,
    val relationType: String,
    val toEntityId: String,
    val createdAt: Long,
    val metadata: String? = null,
)

@Serializable
data class Project(
    val id: String,
    val name: String,
    val description: String? = null,
    val status: String = "ACTIVE",
    val parentProjectId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val metadata: String? = null,
)

@Serializable
data class ProjectRelation(
    val id: String,
    val fromProjectId: String,
    val relationType: String,
    val toProjectId: String,
    val createdAt: Long,
)

@Serializable
data class AgentTask(
    val id: String,
    val type: String,
    val description: String,
    val status: TaskStatus,
    val priority: Int,
    val createdAt: Long,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val progress: Double? = null,
    val result: String? = null,
    val error: String? = null,
    val attempts: Long = 0,
    val parentTaskId: String? = null,
    val conversationId: String? = null,
    val metadata: String? = null,
)

@Serializable
data class TaskEventRecord(
    val id: String,
    val taskId: String,
    val eventType: String,
    val timestamp: Long,
    val details: String? = null,
)

@Serializable
data class Reminder(
    val id: String,
    val text: String,
    val triggerAt: Long,
    val status: ReminderStatus,
    val conversationId: String? = null,
    val createdAt: Long,
    val source: String,
    val metadata: String? = null,
)

@Serializable
data class BackupRecord(
    val id: String,
    val provider: String,
    val path: String,
    val createdAt: Long,
    val sizeBytes: Long,
    val checksum: String,
    val status: String,
    val schemaVersion: Long,
    val metadata: String? = null,
)
