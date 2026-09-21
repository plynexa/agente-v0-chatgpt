package com.plynexa.agent.core.provider

import kotlinx.serialization.Serializable

@Serializable
enum class ProviderAvailability { AVAILABLE, UNAVAILABLE, DEGRADED }

@Serializable
enum class ProviderHealth { HEALTHY, UNHEALTHY, UNKNOWN }

@Serializable
enum class ProviderCapability { TEXT, CODE, STRUCTURED_OUTPUT, IMAGE, EMBEDDINGS, LOCAL_MODEL }

@Serializable
data class ProviderRequest(
    val prompt: String,
    val capability: ProviderCapability = ProviderCapability.TEXT,
    val correlationId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class ProviderResponse(
    val providerId: String,
    val content: String,
    val metadata: Map<String, String> = emptyMap(),
)

interface AIProvider {
    val id: String
    val name: String
    val availability: ProviderAvailability
    val capabilities: Set<ProviderCapability>
    val estimatedCost: Double?
    val health: ProviderHealth
    suspend fun execute(request: ProviderRequest): ProviderResponse
}

interface LocalModelProvider : AIProvider

interface ImageProvider : AIProvider
