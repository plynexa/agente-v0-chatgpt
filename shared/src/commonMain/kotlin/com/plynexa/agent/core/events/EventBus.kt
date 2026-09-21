package com.plynexa.agent.core.events

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

interface EventBus {
    val events: Flow<AgentEvent>
    suspend fun publish(event: AgentEvent)
    fun tryPublish(event: AgentEvent): Boolean
}

class InMemoryEventBus(bufferCapacity: Int = 256) : EventBus {
    private val stream = MutableSharedFlow<AgentEvent>(
        replay = 0,
        extraBufferCapacity = bufferCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val events: Flow<AgentEvent> = stream.asSharedFlow()

    override suspend fun publish(event: AgentEvent) {
        stream.emit(event)
    }

    override fun tryPublish(event: AgentEvent): Boolean = stream.tryEmit(event)
}
