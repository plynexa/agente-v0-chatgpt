package com.plynexa.agent.core.memory

import com.plynexa.agent.core.events.AgentEvent
import com.plynexa.agent.core.events.AgentEventType
import com.plynexa.agent.core.events.EventBus
import com.plynexa.agent.core.model.Entity
import com.plynexa.agent.core.model.EntityRelation
import com.plynexa.agent.core.model.Memory
import com.plynexa.agent.core.model.MemoryType
import com.plynexa.agent.core.model.RankedMemory
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider

class MemoryManager(
    private val repository: MemoryRepository,
    private val eventBus: EventBus,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
) {
    suspend fun remember(
        content: String,
        type: MemoryType,
        importance: Double = 0.5,
        projectId: String? = null,
        sourceMessageId: String? = null,
    ): Memory {
        require(content.isNotBlank())
        require(importance in 0.0..1.0)
        val now = timeProvider.nowEpochMillis()
        val memory = Memory(
            idGenerator.nextId("memory"), type, content.trim(), importance,
            now, now, sourceMessageId, projectId,
        )
        repository.saveMemory(memory)
        eventBus.publish(event(AgentEventType.MEMORY_CREATED, memory.id, now))
        return memory
    }

    suspend fun search(query: String, limit: Int = 20): List<RankedMemory> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return emptyList()
        val queryTokens = tokens(normalized)
        val direct = repository.searchCandidates(normalized, (limit * 4).toLong())
        val candidates = (direct + repository.memories()).distinctBy(Memory::id)
        val ranked = candidates.map { memory ->
            val contentTokens = tokens(memory.content.lowercase())
            val overlap = queryTokens.count(contentTokens::contains).toDouble() / queryTokens.size.coerceAtLeast(1)
            val phrase = if (memory.content.lowercase().contains(normalized)) 1.0 else 0.0
            RankedMemory(memory, phrase * 0.55 + overlap * 0.35 + memory.importance * 0.10)
        }.filter { it.score >= 0.12 }.sortedByDescending(RankedMemory::score).take(limit)
        if (ranked.isNotEmpty()) {
            eventBus.publish(event(AgentEventType.MEMORY_FOUND, ranked.first().memory.id, timeProvider.nowEpochMillis()))
        }
        return ranked
    }

    fun memories(): List<Memory> = repository.memories()

    suspend fun update(
        id: String,
        content: String,
        type: MemoryType,
        importance: Double,
        projectId: String? = null,
        metadata: String? = null,
    ): Memory {
        require(content.isNotBlank())
        require(importance in 0.0..1.0)
        val current = requireNotNull(repository.memory(id)) { "Memory not found" }
        val updated = current.copy(
            content = content.trim(), type = type, importance = importance,
            updatedAt = timeProvider.nowEpochMillis(), projectId = projectId, metadata = metadata,
        )
        repository.updateMemory(updated)
        eventBus.publish(event(AgentEventType.MEMORY_UPDATED, id, updated.updatedAt))
        return updated
    }

    suspend fun delete(id: String) {
        requireNotNull(repository.memory(id)) { "Memory not found" }
        repository.deleteMemory(id)
        eventBus.publish(event(AgentEventType.MEMORY_DELETED, id, timeProvider.nowEpochMillis()))
    }

    fun createEntity(name: String, type: String, aliases: List<String> = emptyList()): Entity {
        val now = timeProvider.nowEpochMillis()
        return Entity(
            id = idGenerator.nextId("entity"), name = name.trim(),
            normalizedName = normalizeName(name), type = type,
            aliases = aliases.takeIf { it.isNotEmpty() }?.joinToString("|"),
            createdAt = now, updatedAt = now,
        ).also(repository::saveEntity)
    }

    fun relate(from: Entity, relationType: String, to: Entity): EntityRelation {
        return EntityRelation(
            idGenerator.nextId("entity-relation"), from.id, relationType, to.id,
            timeProvider.nowEpochMillis(),
        ).also(repository::saveEntityRelation)
    }

    fun entity(name: String): Entity? = repository.entityByName(normalizeName(name))
    fun findEntity(name: String): Entity? = entity(name)
    fun entityRelations(entityId: String): List<EntityRelation> = repository.entityRelations(entityId)

    private fun normalizeName(value: String) = value.trim().lowercase()
    private fun tokens(value: String) = Regex("[\\p{L}0-9]+")
        .findAll(value).map { it.value }.filter { it.length > 1 && it !in stopWords }.toSet()

    private fun event(type: AgentEventType, correlationId: String, at: Long) = AgentEvent(
        idGenerator.nextId("event"), type, at, correlationId, "MemoryManager",
    )

    private companion object {
        val stopWords = setOf(
            "a", "as", "o", "os", "de", "da", "das", "do", "dos", "e", "é", "em", "um", "uma",
            "meu", "minha", "meus", "minhas", "seu", "sua", "seus", "suas", "quem", "qual", "quais",
            "que", "sobre", "nome",
        )
    }
}
