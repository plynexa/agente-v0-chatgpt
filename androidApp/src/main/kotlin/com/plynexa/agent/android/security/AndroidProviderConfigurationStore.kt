package com.plynexa.agent.android.security

import com.plynexa.agent.core.provider.ProviderConfiguration
import com.plynexa.agent.core.provider.ProviderConfigurationStore
import com.plynexa.agent.core.provider.SaveProviderConfigurationRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AndroidProviderConfigurationStore(
    private val secrets: AndroidSecretStore,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : ProviderConfigurationStore {
    override fun list(): List<ProviderConfiguration> = ids().mapNotNull(::configuration)

    override fun save(request: SaveProviderConfigurationRequest): ProviderConfiguration {
        val id = request.id.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "-")
        require(id.isNotBlank()) { "Provider id is required" }
        require(request.name.isNotBlank()) { "Provider name is required" }
        require(request.baseUrl.startsWith("http://") || request.baseUrl.startsWith("https://")) {
            "Provider URL must start with http:// or https://"
        }
        require(request.model.isNotBlank()) { "Model is required" }
        request.apiKey?.takeIf(String::isNotBlank)?.let { secrets.put(keySecret(id), it) }
        val value = ProviderConfiguration(
            id, request.name.trim(), request.kind, request.baseUrl.trim().trimEnd('/'),
            request.model.trim(), request.enabled, secrets.get(keySecret(id)) != null,
        )
        secrets.put(keyConfig(id), json.encodeToString(value))
        secrets.put(KEY_IDS, (ids() + id).distinct().joinToString("|"))
        return value
    }

    override fun delete(id: String) {
        secrets.remove(keyConfig(id))
        secrets.remove(keySecret(id))
        secrets.put(KEY_IDS, ids().filterNot { it == id }.joinToString("|"))
    }

    private fun ids() = secrets.get(KEY_IDS).orEmpty().split('|').filter(String::isNotBlank)
    private fun configuration(id: String) = secrets.get(keyConfig(id))?.let {
        runCatching { json.decodeFromString<ProviderConfiguration>(it) }.getOrNull()
    }
    private fun keyConfig(id: String) = "provider_config_$id"
    private fun keySecret(id: String) = "provider_secret_$id"

    private companion object { const val KEY_IDS = "provider_ids" }
}
