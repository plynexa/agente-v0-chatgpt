package com.plynexa.agent.core.context

import com.plynexa.agent.core.conversation.ConversationRepository
import com.plynexa.agent.core.model.ContextSnapshot

class ContextManager(
    private val conversationRepository: ConversationRepository,
) {
    private val stopWords = setOf(
        "para", "essa", "esse", "isso", "aquela", "aquele", "qual", "quais",
        "como", "com", "uma", "uns", "das", "dos", "que", "por", "mais",
    )

    fun snapshot(conversationId: String, recentLimit: Long = 20): ContextSnapshot {
        val conversation = requireNotNull(conversationRepository.findConversation(conversationId)) {
            "Conversation not found"
        }
        val recent = conversationRepository.recentMessages(conversationId, recentLimit)
        val entities = recent.asReversed().asSequence()
            .flatMap { entityCandidates(it.content).asSequence() }
            .distinct()
            .take(12)
            .toList()
        return ContextSnapshot(
            conversationId = conversationId,
            recentMessages = recent,
            inferredEntities = entities,
            activeProjectId = conversation.activeProjectId,
        )
    }

    private fun entityCandidates(content: String): List<String> =
        Regex("[\\p{L}][\\p{L}0-9_-]{2,}")
            .findAll(content)
            .map { it.value.trim() }
            .filter { token -> token.lowercase() !in stopWords }
            .filter { token -> token.first().isUpperCase() || token.any(Char::isDigit) }
            .toList()
}
