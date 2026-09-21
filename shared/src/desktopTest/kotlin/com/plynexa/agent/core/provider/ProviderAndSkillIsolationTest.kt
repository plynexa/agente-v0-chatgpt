package com.plynexa.agent.core.provider

import com.plynexa.agent.core.events.AgentEventType
import com.plynexa.agent.core.events.InMemoryEventBus
import com.plynexa.agent.core.permission.PermissionManager
import com.plynexa.agent.core.skill.EchoSkill
import com.plynexa.agent.core.skill.SkillRegistry
import com.plynexa.agent.core.skill.SkillRequest
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProviderAndSkillIsolationTest {
    @Test
    fun mockProviderFailureDoesNotBreakLocalSkill() = runTest {
        var sequence = 0
        val ids = IdGenerator { prefix -> "$prefix-${sequence++}" }
        val clock = TimeProvider { 2_000_000_000_000L + sequence }
        val bus = InMemoryEventBus()
        val provider = MockAIProvider(failure = IllegalStateException("forced mock failure"))
        val executor = ProviderExecutor(listOf(provider), bus, ids, clock)
        val events = async(start = CoroutineStart.UNDISPATCHED) { bus.events.take(3).toList() }

        assertFailsWith<IllegalStateException> {
            executor.execute(provider.id, ProviderRequest("complex request", correlationId = "request-1"))
        }
        assertEquals(
            listOf(AgentEventType.PROVIDER_SELECTED, AgentEventType.PROVIDER_REQUEST, AgentEventType.PROVIDER_ERROR),
            events.await().map { it.type },
        )

        val skills = SkillRegistry(
            listOf(EchoSkill(clock)), PermissionManager(), bus, ids, clock,
        )
        val localResult = skills.execute("echo", SkillRequest("echo", "Agent Core still alive"))
        assertEquals("Agent Core still alive", localResult.content)
    }

    @Test
    fun mockProviderIsFunctionalAndSelectableWithoutKeys() = runTest {
        var sequence = 0
        val ids = IdGenerator { prefix -> "$prefix-${sequence++}" }
        val clock = TimeProvider { 2_000_000_000_000L + sequence }
        val bus = InMemoryEventBus()
        val provider = MockAIProvider(responseText = "received: {prompt}")
        val executor = ProviderExecutor(listOf(provider), bus, ids, clock)

        assertEquals("mock-ai", executor.select(ProviderCapability.TEXT)?.id)
        assertEquals(
            "received: hello",
            executor.execute(provider.id, ProviderRequest("hello")).content,
        )
    }
}
