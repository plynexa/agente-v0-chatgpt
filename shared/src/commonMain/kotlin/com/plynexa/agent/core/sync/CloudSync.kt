package com.plynexa.agent.core.sync

import com.plynexa.agent.core.events.AgentEvent
import com.plynexa.agent.core.events.AgentEventType
import com.plynexa.agent.core.events.EventBus
import com.plynexa.agent.core.model.Conversation
import com.plynexa.agent.core.model.Memory
import com.plynexa.agent.core.model.Message
import com.plynexa.agent.core.model.Project
import com.plynexa.agent.core.model.Reminder
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider
import kotlinx.serialization.Serializable

@Serializable
data class CloudSyncSnapshot(
    val deviceId: String,
    val createdAt: Long,
    val cursor: String? = null,
    val conversations: List<Conversation> = emptyList(),
    val messages: List<Message> = emptyList(),
    val memories: List<Memory> = emptyList(),
    val projects: List<Project> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
)

@Serializable
data class CloudSyncResult(val cursor: String, val acceptedRecords: Int)

interface CloudSyncProvider {
    suspend fun push(snapshot: CloudSyncSnapshot): CloudSyncResult
    suspend fun pull(afterCursor: String?): CloudSyncSnapshot
}

interface CloudSyncSource {
    fun snapshot(deviceId: String, cursor: String?): CloudSyncSnapshot
    fun apply(snapshot: CloudSyncSnapshot)
}

/** Provider-neutral two-way sync coordinator. A cloud endpoint is optional. */
class CloudSyncCoordinator(
    private val deviceId: String,
    private val source: CloudSyncSource,
    private val provider: CloudSyncProvider,
    private val events: EventBus,
    private val ids: IdGenerator,
    private val clock: TimeProvider,
) {
    var cursor: String? = null
        private set

    suspend fun syncOnce(): CloudSyncResult {
        publish(AgentEventType.SYNC_STARTED)
        return try {
            val pushed = provider.push(source.snapshot(deviceId, cursor))
            val remote = provider.pull(cursor)
            source.apply(remote)
            cursor = remote.cursor ?: pushed.cursor
            publish(AgentEventType.SYNC_COMPLETED)
            pushed
        } catch (error: Throwable) {
            publish(AgentEventType.SYNC_FAILED, error::class.simpleName.orEmpty())
            throw error
        }
    }

    private suspend fun publish(type: AgentEventType, error: String = "") {
        events.publish(AgentEvent(
            ids.nextId("event"), type, clock.nowEpochMillis(), source = "CloudSyncCoordinator",
            metadata = if (error.isBlank()) emptyMap() else mapOf("errorType" to error),
        ))
    }
}
