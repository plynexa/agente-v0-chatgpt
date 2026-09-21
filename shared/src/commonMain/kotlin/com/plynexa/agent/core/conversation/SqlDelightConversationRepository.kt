package com.plynexa.agent.core.conversation

import com.plynexa.agent.core.model.Conversation
import com.plynexa.agent.core.model.Message
import com.plynexa.agent.core.model.MessageRole
import com.plynexa.agent.core.model.MessageSource
import com.plynexa.agent.db.AgentDatabase
import com.plynexa.agent.db.Conversations
import com.plynexa.agent.db.Messages

class SqlDelightConversationRepository(
    private val database: AgentDatabase,
) : ConversationRepository {
    private val queries get() = database.agentQueries

    override fun createConversation(conversation: Conversation) {
        queries.insertConversation(
            id = conversation.id,
            title = conversation.title,
            created_at = conversation.createdAt,
            updated_at = conversation.updatedAt,
            active_project_id = conversation.activeProjectId,
            metadata = conversation.metadata,
        )
    }

    override fun findConversation(id: String): Conversation? =
        queries.selectConversationById(id).executeAsOneOrNull()?.toDomain()

    override fun listConversations(): List<Conversation> =
        queries.selectConversations().executeAsList().map { it.toDomain() }

    override fun updateConversation(conversation: Conversation) = queries.updateConversation(
        conversation.title, conversation.updatedAt, conversation.metadata, conversation.id,
    )

    override fun deleteConversation(id: String) = queries.deleteConversation(id)

    override fun addMessage(message: Message) {
        database.transaction {
            queries.insertMessage(
                id = message.id,
                conversation_id = message.conversationId,
                role = message.role.name,
                content = message.content,
                timestamp = message.timestamp,
                source = message.source.name,
                device_id = message.deviceId,
                metadata = message.metadata,
            )
            queries.touchConversation(message.timestamp, message.conversationId)
        }
    }

    override fun findMessage(id: String): Message? =
        queries.selectMessageById(id).executeAsOneOrNull()?.toDomain()

    override fun listMessages(conversationId: String): List<Message> =
        queries.selectMessagesForConversation(conversationId).executeAsList().map { it.toDomain() }

    override fun recentMessages(conversationId: String, limit: Long): List<Message> =
        queries.selectRecentMessagesForConversation(conversationId, limit)
            .executeAsList().asReversed().map { it.toDomain() }

    override fun searchMessages(query: String, limit: Long): List<Message> =
        queries.searchMessages(query, limit).executeAsList().map { it.toDomain() }

    private fun Conversations.toDomain() = Conversation(
        id = id,
        title = title,
        createdAt = created_at,
        updatedAt = updated_at,
        activeProjectId = active_project_id,
        metadata = metadata,
    )

    private fun Messages.toDomain() = Message(
        id = id,
        conversationId = conversation_id,
        role = MessageRole.valueOf(role),
        content = content,
        timestamp = timestamp,
        source = MessageSource.valueOf(source),
        deviceId = device_id,
        metadata = metadata,
    )
}
