package com.plynexa.agent.core.project

import com.plynexa.agent.core.model.Project
import com.plynexa.agent.core.model.ProjectRelation
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider

class ProjectManager(
    private val repository: ProjectRepository,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
) {
    fun create(name: String, description: String? = null, parentProjectId: String? = null): Project {
        require(name.isNotBlank())
        val now = timeProvider.nowEpochMillis()
        return Project(
            idGenerator.nextId("project"), name.trim(), description, "ACTIVE",
            parentProjectId, now, now,
        ).also(repository::save)
    }

    fun relate(child: Project, relationType: String, parent: Project): ProjectRelation {
        require(repository.project(child.id) != null)
        require(repository.project(parent.id) != null)
        return ProjectRelation(
            idGenerator.nextId("project-relation"), child.id,
            relationType, parent.id, timeProvider.nowEpochMillis(),
        ).also(repository::saveRelation)
    }

    fun project(name: String): Project? = repository.projectByName(name.trim())
    fun projectById(id: String): Project? = repository.project(id)
    fun projects(): List<Project> = repository.projects()
    fun relations(projectId: String): List<ProjectRelation> = repository.relations(projectId)
    fun relatedProjects(projectId: String): List<Project> = relations(projectId)
        .flatMap { listOf(it.fromProjectId, it.toProjectId) }
        .filterNot { it == projectId }
        .distinct()
        .mapNotNull(repository::project)
}
