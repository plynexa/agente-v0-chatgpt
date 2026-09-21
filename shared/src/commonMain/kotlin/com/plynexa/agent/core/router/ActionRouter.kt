package com.plynexa.agent.core.router

import kotlinx.serialization.Serializable

@Serializable
enum class ActionRoute { LOCAL_ACTION, MEMORY, SKILL, EXTERNAL_AI, IMAGE_PROVIDER, LOCAL_MODEL, NOT_SUPPORTED }

@Serializable
data class RouteDecision(
    val route: ActionRoute,
    val skillId: String? = null,
    val confidence: Double,
    val reason: String,
)

class ActionRouter {
    fun route(input: String): RouteDecision {
        val normalized = input.trim().lowercase()
        if (normalized.isBlank()) return decision(ActionRoute.NOT_SUPPORTED, 1.0, "empty input")
        return when {
            normalized.startsWith("me lembra") || normalized.contains("lembrete") ->
                decision(ActionRoute.SKILL, 0.98, "local reminder intent", "reminder")
            normalized.startsWith("quem é") || normalized.contains("última conversa") || normalized.startsWith("quando falei") ->
                decision(ActionRoute.MEMORY, 0.92, "local memory/history intent", "memory")
            normalized.contains("qual projeto") || normalized.contains("projeto ela") ||
                normalized.contains("projeto ele") || normalized.contains("faz parte") || normalized.contains("pertence") ->
                decision(ActionRoute.LOCAL_ACTION, 0.94, "local project relation intent")
            normalized.contains("status do agente") ->
                decision(ActionRoute.SKILL, 0.99, "local system status", "system-status")
            normalized.contains("tarefas abertas") || normalized.contains("próxima tarefa") ||
                normalized.contains("minhas tarefas") || normalized == "tarefas" ->
                decision(ActionRoute.LOCAL_ACTION, 0.9, "local task query")
            normalized.contains("gere uma imagem") || normalized.contains("crie uma imagem") ->
                decision(ActionRoute.IMAGE_PROVIDER, 0.95, "image generation requires provider")
            normalized.contains("modelo local") ->
                decision(ActionRoute.LOCAL_MODEL, 0.85, "explicit local model request")
            normalized.contains("analise este código") || normalized.contains("código complexo") ->
                decision(ActionRoute.EXTERNAL_AI, 0.8, "complex analysis provider intent")
            normalized.startsWith("eco ") ->
                decision(ActionRoute.SKILL, 0.99, "explicit echo", "echo")
            else -> decision(ActionRoute.NOT_SUPPORTED, 0.4, "no deterministic local intent")
        }
    }

    private fun decision(route: ActionRoute, confidence: Double, reason: String, skillId: String? = null) =
        RouteDecision(route, skillId, confidence, reason)
}
