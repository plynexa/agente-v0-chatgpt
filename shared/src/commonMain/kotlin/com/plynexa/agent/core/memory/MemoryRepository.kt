package com.plynexa.agent.core.memory

import com.plynexa.agent.core.model.Entity
import com.plynexa.agent.core.model.EntityRelation
import com.plynexa.agent.core.model.Memory

interface MemoryRepository {
    fun saveMemory(memory: Memory)
    fun memory(id: String): Memory?
    fun memories(): List<Memory>
    fun searchCandidates(query: String, limit: Long): List<Memory>
    fun updateMemory(memory: Memory)
    fun deleteMemory(id: String)
    fun saveEntity(entity: Entity)
    fun entity(id: String): Entity?
    fun entityByName(normalizedName: String): Entity?
    fun searchEntities(query: String): List<Entity>
    fun saveEntityRelation(relation: EntityRelation)
    fun entityRelations(entityId: String): List<EntityRelation>
}
