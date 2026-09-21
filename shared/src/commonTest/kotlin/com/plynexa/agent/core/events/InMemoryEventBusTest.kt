package com.plynexa.agent.core.events

import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryEventBusTest {
    @Test
    fun publishesRealEventToSubscriber() = runTest {
        val bus = InMemoryEventBus()
        val received = async(start = CoroutineStart.UNDISPATCHED) { bus.events.first() }
        val event = AgentEvent(
            id = "event-1",
            type = AgentEventType.TASK_CREATED,
            timestampEpochMillis = 1,
            source = "test",
        )

        bus.publish(event)

        assertEquals(event, received.await())
    }
}
