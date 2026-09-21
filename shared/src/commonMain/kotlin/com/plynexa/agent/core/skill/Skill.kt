package com.plynexa.agent.core.skill

import com.plynexa.agent.core.model.SkillPermission
import kotlinx.serialization.Serializable

@Serializable
data class SkillDefinition(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val capabilities: Set<String>,
    val permissions: Set<SkillPermission>,
    val enabled: Boolean = true,
    val configurationSchema: String? = null,
    val installedAt: Long,
)

@Serializable
data class SkillRequest(
    val action: String,
    val input: String,
    val conversationId: String? = null,
    val arguments: Map<String, String> = emptyMap(),
)

@Serializable
data class SkillResult(
    val success: Boolean,
    val content: String,
    val metadata: Map<String, String> = emptyMap(),
)

interface AgentSkill {
    val definition: SkillDefinition
    fun permissionsFor(request: SkillRequest): Set<SkillPermission> = definition.permissions
    suspend fun execute(request: SkillRequest): SkillResult
}
