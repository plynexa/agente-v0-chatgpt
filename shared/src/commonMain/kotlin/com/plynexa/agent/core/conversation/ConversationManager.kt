package com.plynexa.agent.core.conversation

import com.plynexa.agent.core.events.AgentEvent
import com.plynexa.agent.core.events.AgentEventType
import com.plynexa.agent.core.events.EventBus
import com.plynexa.agent.core.model.Conversation
import com.plynexa.agent.core.model.Message
import com.plynexa.agent.core.model.MessageRole
import com.plynexa.agent.core.model.MessageSource
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider

class ConversationManager(
    private val repository: ConversationRepository,
    private val eventBus: EventBus,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
) {
    suspend fun createConversation(title: String? = null): Conversation {
        val now = timeProvider.nowEpochMillis()
        return Conversation(
            id = idGenerator.nextId("conversation"),
            title = title,
            createdAt = now,
            updatedAt = now,
        ).also(repository::createConversation)
    }

    suspend fun appendUserMessage(
        conversationId: String,
        content: String,
        source: MessageSource,
        deviceId: String? = null,
        metadata: String? = null,
    ): Message = appendMessage(conversationId, MessageRole.USER, content, source, deviceId, metadata)

    suspend fun appendAgentMessage(
        conversationId: String,
        content: String,
        source: MessageSource,
        deviceId: String? = null,
        metadata: String? = null,
    ): Message = appendMessage(conversationId, MessageRole.AGENT, content, source, deviceId, metadata)

    fun conversation(id: String): Conversation? = repository.findConversation(id)
    fun conversations(): List<Conversation> = repository.listConversations()
    suspend fun renameConversation(id: String, title: String): Conversation {
        require(title.isNotBlank()) { "Conversation title cannot be blank" }
        val current = requireNotNull(repository.findConversation(id)) { "Conversation not found" }
        val updated = current.copy(title = title.trim(), updatedAt = timeProvider.nowEpochMillis())
        repository.updateConversation(updated)
        eventBus.publish(AgentEvent(
            idGenerator.nextId("event"), AgentEventType.CONVERSATION_UPDATED, updated.updatedAt,
            id, "ConversationManager", mapOf("conversationId" to id),
        ))
        return updated
    }

    suspend fun deleteConversation(id: String) {
        requireNotNull(repository.findConversation(id)) { "Conversation not found" }
        repository.deleteConversation(id)
        eventBus.publish(AgentEvent(
            idGenerator.nextId("event"), AgentEventType.CONVERSATION_DELETED,
            timeProvider.nowEpochMillis(), id, "ConversationManager", mapOf("conversationId" to id),
        ))
    }
    fun messages(conversationId: String): List<Message> = repository.listMessages(conversationId)
    fun recentMessages(conversationId: String, limit: Long = 20): List<Message> =
        repository.recentMessages(conversationId, limit)
    fun searchMessages(query: String, limit: Long = 50): List<Message> =
        repository.searchMessages(query.trim(), limit)

    private suspend fun appendMessage(
        conversationId: String,
        role: MessageRole,
        content: String,
        source: MessageSource,
        deviceId: String?,
        metadata: String?,
    ): Message {
        require(content.isNotBlank()) { "Message content cannot be blank" }
        require(repository.findConversation(conversationId) != null) { "Conversation not found" }
        val message = Message(
            id = idGenerator.nextId("message"),
            conversationId = conversationId,
            role = role,
            content = content.trim(),
            timestamp = timeProvider.nowEpochMillis(),
            source = source,
            deviceId = deviceId,
            metadata = metadata,
        )
        if (role == MessageRole.USER) {
            eventBus.publish(
                AgentEvent(
                    id = idGenerator.nextId("event"),
                    type = AgentEventType.MESSAGE_RECEIVED,
                    timestampEpochMillis = message.timestamp,
                    correlationId = message.id,
                    source = source.name,
                    metadata = mapOf("conversationId" to conversationId, "role" to role.name),
                ),
            )
        }
        repository.addMessage(message)
        eventBus.publish(
            AgentEvent(
                id = idGenerator.nextId("event"),
                type = AgentEventType.MESSAGE_PERSISTED,
                timestampEpochMillis = message.timestamp,
                correlationId = message.id,
                source = "ConversationManager",
                metadata = mapOf("conversationId" to conversationId, "role" to role.name),
            ),
        )
        eventBus.publish(
            AgentEvent(
                id = idGenerator.nextId("event"),
                type = AgentEventType.MESSAGE_CREATED,
                timestampEpochMillis = message.timestamp,
                correlationId = message.id,
                source = "ConversationManager",
                metadata = mapOf("conversationId" to conversationId, "role" to role.name),
            ),
        )
        return message
    }
}
