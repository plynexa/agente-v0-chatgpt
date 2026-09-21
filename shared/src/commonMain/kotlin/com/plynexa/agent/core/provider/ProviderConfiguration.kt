package com.plynexa.agent.core.provider

import kotlinx.serialization.Serializable

@Serializable
enum class ProviderKind { OPENAI_COMPATIBLE, LOCAL_MODEL, IMAGE }

@Serializable
data class ProviderConfiguration(
    val id: String,
    val name: String,
    val kind: ProviderKind,
    val baseUrl: String,
    val model: String,
    val enabled: Boolean = true,
    val hasApiKey: Boolean = false,
)

@Serializable
data class SaveProviderConfigurationRequest(
    val id: String,
    val name: String,
    val kind: ProviderKind = ProviderKind.OPENAI_COMPATIBLE,
    val baseUrl: String,
    val model: String,
    val apiKey: String? = null,
    val enabled: Boolean = true,
)

interface ProviderConfigurationStore {
    fun list(): List<ProviderConfiguration>
    fun save(request: SaveProviderConfigurationRequest): ProviderConfiguration
    fun delete(id: String)
}
