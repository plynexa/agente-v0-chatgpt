package com.plynexa.agent.core.agent

import com.plynexa.agent.core.conversation.ConversationManager
import com.plynexa.agent.core.model.AgentState
import com.plynexa.agent.core.model.Message
import com.plynexa.agent.core.model.MessageSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class AgentCore(
    val stateStore: AgentStateStore,
    val conversationManager: ConversationManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    suspend fun start() {
        stateStore.update { it.copy(databaseAvailable = true) }
        stateStore.transitionTo(AgentState.STANDBY)
    }

    suspend fun receiveChat(
        conversationId: String,
        content: String,
        deviceId: String? = null,
    ): Message = receiveInput(conversationId, content, MessageSource.CHAT, deviceId)

    suspend fun receiveInput(
        conversationId: String,
        content: String,
        source: MessageSource,
        deviceId: String? = null,
    ): Message {
        stateStore.transitionTo(AgentState.THINKING)
        return try {
            conversationManager.appendUserMessage(
                conversationId = conversationId,
                content = content,
                source = source,
                deviceId = deviceId,
            )
        } finally {
            stateStore.transitionTo(AgentState.STANDBY)
        }
    }

    fun close() {
        scope.cancel("Agent Core closed")
    }
}
