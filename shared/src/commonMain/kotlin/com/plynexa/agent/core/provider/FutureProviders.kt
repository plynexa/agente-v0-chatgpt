package com.plynexa.agent.core.provider

/** Configuration contract only. No key is persisted by this model. */
data class OpenAICompatibleConfiguration(
    val baseUrl: String,
    val model: String,
    val apiKeyReference: String,
)

abstract class UnconfiguredProvider(
    override val id: String,
    override val name: String,
    override val capabilities: Set<ProviderCapability>,
) : AIProvider {
    override val availability = ProviderAvailability.UNAVAILABLE
    override val estimatedCost: Double? = null
    override val health = ProviderHealth.UNKNOWN
    override suspend fun execute(request: ProviderRequest): ProviderResponse =
        error("$name is not configured")
}

class OpenAIProvider : UnconfiguredProvider("openai", "OpenAI", setOf(ProviderCapability.TEXT, ProviderCapability.CODE))
class AnthropicProvider : UnconfiguredProvider("anthropic", "Anthropic", setOf(ProviderCapability.TEXT, ProviderCapability.CODE))
class GeminiProvider : UnconfiguredProvider("gemini", "Gemini", setOf(ProviderCapability.TEXT, ProviderCapability.CODE))

class UnconfiguredLocalModelProvider : UnconfiguredProvider(
    "local-model", "Local Model", setOf(ProviderCapability.TEXT, ProviderCapability.LOCAL_MODEL),
), LocalModelProvider

class UnconfiguredImageProvider : UnconfiguredProvider(
    "image-provider", "Image Provider", setOf(ProviderCapability.IMAGE),
), ImageProvider
