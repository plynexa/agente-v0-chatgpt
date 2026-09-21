package com.plynexa.agent.core.memory

import com.plynexa.agent.core.model.Entity
import com.plynexa.agent.core.model.EntityRelation
import com.plynexa.agent.core.model.Memory
import com.plynexa.agent.core.model.MemoryType
import com.plynexa.agent.db.AgentDatabase
import com.plynexa.agent.db.Entities
import com.plynexa.agent.db.Entity_relations
import com.plynexa.agent.db.Memories

class SqlDelightMemoryRepository(private val database: AgentDatabase) : MemoryRepository {
    private val queries get() = database.agentQueries

    override fun saveMemory(memory: Memory) = queries.insertMemory(
        memory.id, memory.type.name, memory.content, memory.importance,
        memory.createdAt, memory.updatedAt, memory.sourceMessageId, memory.projectId, memory.metadata,
    )

    override fun memory(id: String): Memory? = queries.selectMemoryById(id).executeAsOneOrNull()?.toDomain()
    override fun memories(): List<Memory> = queries.selectAllMemories().executeAsList().map { it.toDomain() }
    override fun searchCandidates(query: String, limit: Long): List<Memory> =
        queries.selectMemoriesByText(query, limit).executeAsList().map { it.toDomain() }
    override fun updateMemory(memory: Memory) = queries.updateMemory(
        memory.type.name, memory.content, memory.importance, memory.updatedAt,
        memory.projectId, memory.metadata, memory.id,
    )
    override fun deleteMemory(id: String) = queries.deleteMemory(id)

    override fun saveEntity(entity: Entity) = queries.insertEntity(
        entity.id, entity.name, entity.normalizedName, entity.type, entity.aliases,
        entity.createdAt, entity.updatedAt, entity.metadata,
    )

    override fun entity(id: String): Entity? = queries.selectEntityById(id).executeAsOneOrNull()?.toDomain()
    override fun entityByName(normalizedName: String): Entity? =
        queries.selectEntityByNormalizedName(normalizedName).executeAsOneOrNull()?.toDomain()
    override fun searchEntities(query: String): List<Entity> =
        queries.selectEntitiesByText(query, query).executeAsList().map { it.toDomain() }

    override fun saveEntityRelation(relation: EntityRelation) = queries.insertEntityRelation(
        relation.id, relation.fromEntityId, relation.relationType,
        relation.toEntityId, relation.createdAt, relation.metadata,
    )

    override fun entityRelations(entityId: String): List<EntityRelation> =
        queries.selectEntityRelationsForEntity(entityId, entityId).executeAsList().map { it.toDomain() }

    private fun Memories.toDomain() = Memory(
        id, MemoryType.valueOf(type), content, importance, created_at, updated_at,
        source_message_id, project_id, metadata,
    )

    private fun Entities.toDomain() = Entity(
        id, name, normalized_name, type, aliases, created_at, updated_at, metadata,
    )

    private fun Entity_relations.toDomain() = EntityRelation(
        id, from_entity_id, relation_type, to_entity_id, created_at, metadata,
    )
}
