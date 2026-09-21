package com.plynexa.agent.core.agent

import com.plynexa.agent.core.events.AgentEvent
import com.plynexa.agent.core.events.AgentEventType
import com.plynexa.agent.core.events.EventBus
import com.plynexa.agent.core.model.AgentState
import com.plynexa.agent.core.model.AgentStatus
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AgentStateStore(
    private val eventBus: EventBus,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
) {
    private val mutex = Mutex()
    private val mutableStatus = MutableStateFlow(AgentStatus(state = AgentState.STANDBY))
    val status: StateFlow<AgentStatus> = mutableStatus.asStateFlow()

    suspend fun transitionTo(state: AgentState) {
        val changed = mutex.withLock {
            if (mutableStatus.value.state == state) false
            else {
                mutableStatus.value = mutableStatus.value.copy(state = state)
                true
            }
        }
        if (changed) {
            eventBus.publish(
                AgentEvent(
                    id = idGenerator.nextId("event"),
                    type = AgentEventType.AGENT_STATE_CHANGED,
                    timestampEpochMillis = timeProvider.nowEpochMillis(),
                    source = "AgentStateStore",
                    metadata = mapOf("state" to state.name),
                ),
            )
        }
    }

    fun update(transform: (AgentStatus) -> AgentStatus) {
        mutableStatus.value = transform(mutableStatus.value)
    }
}
