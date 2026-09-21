package com.plynexa.agent.core.provider

import kotlinx.coroutines.delay

class MockAIProvider(
    private val responseText: String = "mock-response",
    private val delayMillis: Long = 0,
    private val failure: Throwable? = null,
) : AIProvider {
    override val id = "mock-ai"
    override val name = "Mock AI Provider"
    override val availability = ProviderAvailability.AVAILABLE
    override val capabilities = setOf(ProviderCapability.TEXT, ProviderCapability.CODE)
    override val estimatedCost = 0.0
    override val health = ProviderHealth.HEALTHY

    override suspend fun execute(request: ProviderRequest): ProviderResponse {
        if (delayMillis > 0) delay(delayMillis)
        failure?.let { throw it }
        return ProviderResponse(
            providerId = id,
            content = responseText.replace("{prompt}", request.prompt),
            metadata = mapOf("mock" to "true"),
        )
    }
}
