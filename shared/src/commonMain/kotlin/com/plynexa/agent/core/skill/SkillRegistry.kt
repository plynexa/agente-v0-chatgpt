package com.plynexa.agent.core.skill

import com.plynexa.agent.core.events.AgentEvent
import com.plynexa.agent.core.events.AgentEventType
import com.plynexa.agent.core.events.EventBus
import com.plynexa.agent.core.permission.PermissionManager
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider

class SkillRegistry(
    skills: Collection<AgentSkill>,
    private val permissionManager: PermissionManager,
    private val eventBus: EventBus,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
) {
    private val installed = skills.associateBy { it.definition.id }.toMutableMap()

    fun list(): List<SkillDefinition> = installed.values.map(AgentSkill::definition)
    fun skill(id: String): AgentSkill? = installed[id]

    suspend fun execute(skillId: String, request: SkillRequest): SkillResult {
        val skill = requireNotNull(installed[skillId]) { "Skill not installed: $skillId" }
        require(skill.definition.enabled) { "Skill disabled: $skillId" }
        permissionManager.requireAllowed(skillId, skill.permissionsFor(request))
        return try {
            skill.execute(request).also { publish(AgentEventType.SKILL_EXECUTED, skillId, it.success) }
        } catch (error: Throwable) {
            publish(AgentEventType.SKILL_FAILED, skillId, false)
            throw error
        }
    }

    private suspend fun publish(type: AgentEventType, skillId: String, success: Boolean) {
        eventBus.publish(AgentEvent(
            idGenerator.nextId("event"), type, timeProvider.nowEpochMillis(), skillId,
            "SkillRegistry", mapOf("skillId" to skillId, "success" to success.toString()),
        ))
    }
}
