package com.plynexa.agent.core.provider

import com.plynexa.agent.core.events.AgentEvent
import com.plynexa.agent.core.events.AgentEventType
import com.plynexa.agent.core.events.EventBus
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider

class ProviderExecutor(
    providers: Collection<AIProvider>,
    private val eventBus: EventBus,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
) {
    private val providersById = providers.associateBy(AIProvider::id)

    fun select(capability: ProviderCapability): AIProvider? = providersById.values
        .filter { it.availability == ProviderAvailability.AVAILABLE }
        .filter { it.health != ProviderHealth.UNHEALTHY }
        .filter { capability in it.capabilities }
        .minByOrNull { it.estimatedCost ?: Double.MAX_VALUE }

    suspend fun execute(providerId: String, request: ProviderRequest): ProviderResponse {
        val provider = requireNotNull(providersById[providerId]) { "Provider not found: $providerId" }
        publish(AgentEventType.PROVIDER_SELECTED, request.correlationId, mapOf("providerId" to provider.id))
        publish(AgentEventType.PROVIDER_REQUEST, request.correlationId, mapOf("capability" to request.capability.name))
        return try {
            provider.execute(request).also {
                publish(AgentEventType.PROVIDER_RESPONSE, request.correlationId, mapOf("providerId" to provider.id))
            }
        } catch (error: Throwable) {
            publish(AgentEventType.PROVIDER_ERROR, request.correlationId, mapOf("providerId" to provider.id, "errorType" to (error::class.simpleName ?: "Error")))
            throw error
        }
    }

    private suspend fun publish(type: AgentEventType, correlationId: String?, metadata: Map<String, String>) {
        eventBus.publish(AgentEvent(idGenerator.nextId("event"), type, timeProvider.nowEpochMillis(), correlationId, "ProviderExecutor", metadata))
    }
}
