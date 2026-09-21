package com.plynexa.agent.core.permission

import com.plynexa.agent.core.model.PermissionLevel
import com.plynexa.agent.core.model.SkillPermission

enum class PermissionDecision { ALLOWED, CONFIRMATION_REQUIRED, DENIED }

data class PermissionGrant(
    val skillId: String,
    val permission: SkillPermission,
    val level: PermissionLevel,
)

class PermissionManager(initial: Collection<PermissionGrant> = emptyList()) {
    private val grants = initial.associateBy { it.skillId to it.permission }.toMutableMap()

    fun set(grant: PermissionGrant) {
        grants[grant.skillId to grant.permission] = grant
    }

    fun check(skillId: String, permission: SkillPermission): PermissionDecision =
        when (grants[skillId to permission]?.level ?: defaultLevel(permission)) {
            PermissionLevel.SAFE -> PermissionDecision.ALLOWED
            PermissionLevel.CONFIRMATION_REQUIRED -> PermissionDecision.CONFIRMATION_REQUIRED
            PermissionLevel.RESTRICTED -> PermissionDecision.DENIED
        }

    fun requireAllowed(skillId: String, permissions: Set<SkillPermission>) {
        val nonAllowed = permissions.map { it to check(skillId, it) }
            .firstOrNull { it.second != PermissionDecision.ALLOWED }
        require(nonAllowed == null) {
            "Permission ${nonAllowed?.first} for $skillId is ${nonAllowed?.second}"
        }
    }

    private fun defaultLevel(permission: SkillPermission) = when (permission) {
        SkillPermission.READ_MEMORY -> PermissionLevel.SAFE
        SkillPermission.WRITE_MEMORY,
        SkillPermission.READ_FILE,
        SkillPermission.NOTIFICATIONS,
        SkillPermission.MICROPHONE -> PermissionLevel.CONFIRMATION_REQUIRED
        SkillPermission.WRITE_FILE,
        SkillPermission.NETWORK,
        SkillPermission.EXECUTE_COMMAND,
        SkillPermission.MODIFY_PROJECT,
        SkillPermission.SEND_MESSAGE,
        SkillPermission.DEVICE_CONTROL -> PermissionLevel.RESTRICTED
    }
}
