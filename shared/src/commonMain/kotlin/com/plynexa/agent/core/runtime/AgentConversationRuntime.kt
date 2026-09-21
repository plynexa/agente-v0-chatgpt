package com.plynexa.agent.core.runtime

import com.plynexa.agent.core.agent.AgentCore
import com.plynexa.agent.core.context.ContextManager
import com.plynexa.agent.core.events.AgentEvent
import com.plynexa.agent.core.events.AgentEventType
import com.plynexa.agent.core.events.EventBus
import com.plynexa.agent.core.memory.MemoryManager
import com.plynexa.agent.core.model.AgentState
import com.plynexa.agent.core.model.ContextSnapshot
import com.plynexa.agent.core.model.Entity
import com.plynexa.agent.core.model.MemoryType
import com.plynexa.agent.core.model.Message
import com.plynexa.agent.core.model.MessageSource
import com.plynexa.agent.core.model.Project
import com.plynexa.agent.core.project.ProjectManager
import com.plynexa.agent.core.reminder.ReminderManager
import com.plynexa.agent.core.router.ActionRoute
import com.plynexa.agent.core.router.ActionRouter
import com.plynexa.agent.core.router.RouteDecision
import com.plynexa.agent.core.task.TaskManager
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

data class ConversationRuntimeResult(
    val userMessage: Message,
    val replyMessage: Message,
    val route: RouteDecision,
    val context: ContextSnapshot,
)

/**
 * Authoritative text pipeline used by chat, Android and transcribed voice.
 * Interfaces submit input here; they do not implement their own response logic.
 */
class AgentConversationRuntime(
    private val core: AgentCore,
    private val contextManager: ContextManager,
    private val memoryManager: MemoryManager,
    private val projectManager: ProjectManager,
    private val taskManager: TaskManager,
    private val reminderManager: ReminderManager,
    private val router: ActionRouter,
    private val eventBus: EventBus,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend fun process(
        conversationId: String,
        content: String,
        source: MessageSource,
        deviceId: String? = null,
    ): ConversationRuntimeResult {
        require(content.isNotBlank()) { "Message content cannot be blank" }
        val user = core.receiveInput(conversationId, content, source, deviceId)
        core.stateStore.transitionTo(AgentState.PROCESSING)
        return try {
            publish(AgentEventType.CONTEXT_RESOLVING, user.id, mapOf("conversationId" to conversationId))
            val capture = captureLocalKnowledge(content, user.id)
            val context = contextManager.snapshot(conversationId)
            val route = router.route(content)
            publish(
                AgentEventType.ROUTE_SELECTED,
                user.id,
                mapOf("route" to route.route.name, "reason" to route.reason),
            )
            val answer = resolve(content, conversationId, context, route, capture)
            publish(AgentEventType.RESPONSE_CREATED, user.id, mapOf("route" to route.route.name))
            val metadata = "route=${route.route.name};contextEntities=${context.inferredEntities.joinToString("|")}"
            val reply = core.conversationManager.appendAgentMessage(
                conversationId = conversationId,
                content = answer,
                source = source,
                deviceId = "android-host",
                metadata = metadata,
            )
            ConversationRuntimeResult(user, reply, route, context)
        } finally {
            core.stateStore.transitionTo(AgentState.STANDBY)
        }
    }

    private suspend fun resolve(
        input: String,
        conversationId: String,
        context: ContextSnapshot,
        route: RouteDecision,
        capture: KnowledgeCapture,
    ): String {
        val normalized = normalize(input)
        if (normalized == "agente") return "Estou aqui."
        if (capture.relationSaved) return "Entendido, salvei essa relação."
        if (capture.memorySaved) return "Entendido, salvei na memória."

        return when (route.route) {
            ActionRoute.MEMORY -> answerFromMemory(input, context)
            ActionRoute.LOCAL_ACTION -> when {
                isProjectQuestion(normalized) -> answerProjectQuestion(input, context)
                normalized.contains("tarefa") -> {
                    val open = taskManager.tasks().count { it.finishedAt == null }
                    "Você tem $open tarefas abertas."
                }
                else -> usefulFallback(input, context)
            }
            ActionRoute.SKILL -> when (route.skillId) {
                "reminder" -> createReminder(input, conversationId)
                "system-status" -> "O agente está funcionando localmente."
                "memory" -> answerFromMemory(input, context)
                "echo" -> input.trim().substringAfter(' ', "")
                else -> usefulFallback(input, context)
            }
            ActionRoute.IMAGE_PROVIDER ->
                "Essa ação exige um gerador de imagens que ainda não está configurado."
            ActionRoute.EXTERNAL_AI ->
                "Essa ação exige um provedor de IA externo que ainda não está configurado."
            ActionRoute.LOCAL_MODEL ->
                "Essa ação exige um modelo local que ainda não está instalado."
            ActionRoute.NOT_SUPPORTED -> usefulFallback(input, context)
        }
    }

    private suspend fun answerFromMemory(input: String, context: ContextSnapshot): String {
        val subject = explicitSubject(input) ?: resolveEntity(context)?.name
        val query = subject ?: input
        publish(AgentEventType.MEMORY_SEARCH_STARTED, context.conversationId, mapOf("query" to query))
        val result = memoryManager.search(query, 1).firstOrNull()
            ?: return "Não encontrei essa informação na memória local."
        return toUserPerspective(result.memory.content)
    }

    private fun answerProjectQuestion(input: String, context: ContextSnapshot): String {
        val project = resolveProject(input, context)
            ?: return "Não encontrei o projeto mencionado no contexto local."
        val parentRelation = projectManager.relations(project.id)
            .firstOrNull { it.fromProjectId == project.id && it.relationType.equals("belongs_to", true) }
            ?: return "Não encontrei uma relação de projeto para ${project.name}."
        val parent = projectManager.projectById(parentRelation.toProjectId)
            ?: return "A relação de ${project.name} existe, mas o projeto relacionado não foi encontrado."
        return "A ${project.name} pertence à ${parent.name}."
    }

    private suspend fun createReminder(input: String, conversationId: String): String {
        val triggerAt = parseReminderTime(input)
            ?: return "Não consegui identificar quando o lembrete deve disparar."
        val text = Regex("(?i)\\s+de\\s+").split(input, limit = 2).getOrNull(1)
            ?.trim()?.removeSuffix(".") ?: input.trim()
        val id = reminderManager.create(text, triggerAt, conversationId)
        publish(AgentEventType.SKILL_EXECUTED, id, mapOf("skillId" to "reminder"))
        return "Lembrete criado."
    }

    private suspend fun usefulFallback(input: String, context: ContextSnapshot): String {
        if (isProjectQuestion(normalize(input))) return answerProjectQuestion(input, context)
        val entity = resolveEntity(context)
        val query = entity?.name ?: explicitSubject(input) ?: input
        publish(AgentEventType.MEMORY_SEARCH_STARTED, context.conversationId, mapOf("query" to query))
        val memory = memoryManager.search(query, 1).firstOrNull()
        if (memory != null && memory.score >= 0.12) return toUserPerspective(memory.memory.content)
        return "Ainda não tenho informação ou capacidade local suficiente para responder a isso."
    }

    private suspend fun captureLocalKnowledge(input: String, sourceMessageId: String): KnowledgeCapture {
        var memorySaved = false
        var relationSaved = false
        val trimmed = input.trim()
        knowledgePattern.matchEntire(input.trim())?.let { match ->
            val name = match.groupValues[1].trim()
            val description = match.groupValues[2].trim().removeSuffix(".")
            if (name.split(' ').size <= 4 && !name.equals("quem", true)) {
                val alias = description.split(' ').lastOrNull()?.takeIf {
                    it.length in 2..8 && it.all { char -> char.isUpperCase() || char.isDigit() }
                }
                if (memoryManager.findEntity(name) == null) {
                    memoryManager.createEntity(name, description.substringBefore(' ').lowercase(), listOfNotNull(alias))
                }
                if (memoryManager.search(name, 20).none { it.memory.content.equals(input.trim(), true) }) {
                    memoryManager.remember(input.trim(), MemoryType.SEMANTIC, 0.8, sourceMessageId = sourceMessageId)
                    memorySaved = true
                }
            }
        }
        projectRelationPattern.matchEntire(trimmed)?.let { match ->
            val child = findOrCreateProject(match.groupValues[1].trim())
            val parent = findOrCreateProject(match.groupValues[2].trim().removeSuffix("."))
            val exists = projectManager.relations(child.id).any {
                it.fromProjectId == child.id && it.toProjectId == parent.id && it.relationType == "belongs_to"
            }
            if (!exists) {
                projectManager.relate(child, "belongs_to", parent)
                relationSaved = true
            }
        }
        val explicit = memoryCommandPattern.matchEntire(trimmed)?.groupValues?.get(1)?.trim()
        val fact = explicit ?: trimmed.takeIf(::isNaturalFact)
        if (fact != null && !isKnowledgeStatement(trimmed) && !isProjectRelationStatement(trimmed)) {
            val normalizedFact = fact.removeSuffix(".").trim()
            if (normalizedFact.isNotBlank() && memoryManager.search(normalizedFact, 20)
                    .none { it.memory.content.equals(normalizedFact, true) }) {
                memoryManager.remember(normalizedFact, MemoryType.SEMANTIC, 0.75, sourceMessageId = sourceMessageId)
                memorySaved = true
            }
            extractNames(normalizedFact).forEach { name ->
                if (memoryManager.findEntity(name) == null) memoryManager.createEntity(name, "person_or_pet")
            }
        }
        return KnowledgeCapture(memorySaved, relationSaved)
    }

    private fun resolveProject(input: String, context: ContextSnapshot): Project? {
        val projects = projectManager.projects()
        projects.firstOrNull { containsName(input, it.name) }?.let { return it }
        context.recentMessages.asReversed().drop(1).forEach { message ->
            projects.firstOrNull { containsName(message.content, it.name) }?.let { return it }
        }
        return context.activeProjectId?.let(projectManager::projectById)
    }

    private fun resolveEntity(context: ContextSnapshot): Entity? {
        context.inferredEntities.forEach { candidate ->
            memoryManager.findEntity(candidate)?.let { return it }
        }
        return null
    }

    private fun findOrCreateProject(name: String): Project =
        projectManager.project(name) ?: projectManager.create(name)

    private fun parseReminderTime(input: String): Long? {
        val normalized = normalize(input)
        if (!normalized.contains("amanhã") && !normalized.contains("amanha")) return null
        val now = Instant.fromEpochMilliseconds(timeProvider.nowEpochMillis()).toLocalDateTime(timeZone)
        val date = now.date.plus(1, DateTimeUnit.DAY)
        val match = Regex("(?i)(?:às|as)?\\s*(\\d{1,2})(?::(\\d{2})|h(?:oras?)?)?").find(input)
        val hour = match?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..23 } ?: 9
        val minute = match?.groupValues?.get(2)?.toIntOrNull()?.takeIf { it in 0..59 } ?: 0
        return LocalDateTime(date, LocalTime(hour, minute)).toInstant(timeZone).toEpochMilliseconds()
    }

    private fun explicitSubject(input: String): String? =
        Regex("(?i)^\\s*quem\\s+é\\s+(.+?)[?.!]*\\s*$").find(input)
            ?.groupValues?.get(1)?.trim()?.takeUnless { it in pronouns }

    private fun isKnowledgeStatement(input: String) = knowledgePattern.matches(input.trim())
    private fun isProjectRelationStatement(input: String) = projectRelationPattern.matches(input.trim())
    private fun isProjectQuestion(normalized: String) =
        normalized.contains("projeto") || normalized.contains("faz parte") || normalized.contains("pertence")

    private fun containsName(text: String, name: String) =
        Regex("(?i)(^|[^\\p{L}0-9])${Regex.escape(name)}([^\\p{L}0-9]|$)").containsMatchIn(text)

    private fun normalize(value: String) = value.trim().lowercase()

    private fun isNaturalFact(value: String): Boolean {
        val normalized = normalize(value)
        if (value.endsWith("?") || questionPrefixes.any(normalized::startsWith)) return false
        return value.length <= 300 && (
            Regex("(?i)\\b(eu tenho|minha|minhas|meu|meus)\\b").containsMatchIn(value) ||
                Regex("(?i)\\b(é|são|fica|custa|pertence)\\b").containsMatchIn(value)
            )
    }

    private fun extractNames(value: String): List<String> = Regex("\\b[\\p{Lu}][\\p{L}0-9_-]{2,}\\b")
        .findAll(value).map { it.value }.filterNot { it in ignoredCapitalized }.distinct().toList()

    private fun toUserPerspective(value: String): String = value
        .replace(Regex("(?i)\\beu tenho\\b"), "Você tem")
        .replace(Regex("(?i)\\bminhas\\b"), "suas")
        .replace(Regex("(?i)\\bmeus\\b"), "seus")
        .replace(Regex("(?i)\\bminha\\b"), "sua")
        .replace(Regex("(?i)\\bmeu\\b"), "seu")

    private suspend fun publish(type: AgentEventType, correlationId: String?, metadata: Map<String, String>) {
        eventBus.publish(AgentEvent(
            idGenerator.nextId("event"), type, timeProvider.nowEpochMillis(), correlationId,
            "AgentConversationRuntime", metadata,
        ))
    }

    private companion object {
        val knowledgePattern = Regex("(?i)^([\\p{L}][\\p{L}0-9_-]*(?:\\s+[\\p{L}0-9_-]+){0,3})\\s+é\\s+(?:minha|meu)\\s+(.+?)[.!]?$" )
        val projectRelationPattern = Regex("(?i)^([\\p{L}0-9_-]+)\\s+(?:pertence\\s+(?:à|a|ao)|belongs_to)\\s+([\\p{L}0-9_-]+)[.!]?$" )
        val memoryCommandPattern = Regex(
            "(?i)^(?:lembre(?:-se)?|grave|salve)(?:\\s+(?:na memória|isso))?(?:\\s+de)?\\s+(?:que\\s+)?(.+?)[.!]?$",
        )
        val pronouns = setOf("ela", "ele", "isso", "essa", "esse")
        val questionPrefixes = listOf("quem", "qual", "quais", "quando", "onde", "como", "por que", "o que")
        val ignoredCapitalized = setOf("Eu", "Minha", "Minhas", "Meu", "Meus", "Lembre", "Grave", "Salve")
    }

    private data class KnowledgeCapture(val memorySaved: Boolean, val relationSaved: Boolean)
}
