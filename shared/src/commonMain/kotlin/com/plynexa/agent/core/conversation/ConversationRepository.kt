package com.plynexa.agent.core.conversation

import com.plynexa.agent.core.model.Conversation
import com.plynexa.agent.core.model.Message

interface ConversationRepository {
    fun createConversation(conversation: Conversation)
    fun findConversation(id: String): Conversation?
    fun listConversations(): List<Conversation>
    fun updateConversation(conversation: Conversation)
    fun deleteConversation(id: String)
    fun addMessage(message: Message)
    fun findMessage(id: String): Message?
    fun listMessages(conversationId: String): List<Message>
    fun recentMessages(conversationId: String, limit: Long): List<Message>
    fun searchMessages(query: String, limit: Long = 50): List<Message>
}
