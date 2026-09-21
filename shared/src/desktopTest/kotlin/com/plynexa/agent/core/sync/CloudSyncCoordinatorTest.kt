package com.plynexa.agent.core.sync

import com.plynexa.agent.core.events.AgentEventType
import com.plynexa.agent.core.events.InMemoryEventBus
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class CloudSyncCoordinatorTest {
    @Test
    fun pushesPullsAndAdvancesCursorWithoutCloudDependency() = runTest {
        val bus = InMemoryEventBus()
        var applied: CloudSyncSnapshot? = null
        val source = object : CloudSyncSource {
            override fun snapshot(deviceId: String, cursor: String?) = CloudSyncSnapshot(deviceId, 10, cursor)
            override fun apply(snapshot: CloudSyncSnapshot) { applied = snapshot }
        }
        val provider = object : CloudSyncProvider {
            override suspend fun push(snapshot: CloudSyncSnapshot) = CloudSyncResult("2", 1)
            override suspend fun pull(afterCursor: String?) = CloudSyncSnapshot("cloud", 11, "3")
        }
        val coordinator = CloudSyncCoordinator(
            "s10", source, provider, bus, IdGenerator { "event-1" }, TimeProvider { 12 },
        )
        val completed = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { withTimeout(1_000) {
            bus.events.first { it.type == AgentEventType.SYNC_COMPLETED }
        } }
        assertEquals(1, coordinator.syncOnce().acceptedRecords)
        assertEquals("cloud", applied?.deviceId)
        assertEquals("3", coordinator.cursor)
        assertEquals(AgentEventType.SYNC_COMPLETED, completed.await().type)
    }
}
