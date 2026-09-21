package com.plynexa.agent.core.project

import com.plynexa.agent.core.model.Project
import com.plynexa.agent.core.model.ProjectRelation
import com.plynexa.agent.db.AgentDatabase
import com.plynexa.agent.db.Project_relations
import com.plynexa.agent.db.Projects

class SqlDelightProjectRepository(private val database: AgentDatabase) : ProjectRepository {
    private val queries get() = database.agentQueries

    override fun save(project: Project) = queries.insertProject(
        project.id, project.name, project.description, project.status,
        project.parentProjectId, project.createdAt, project.updatedAt, project.metadata,
    )

    override fun project(id: String): Project? = queries.selectProjectById(id).executeAsOneOrNull()?.toDomain()
    override fun projectByName(name: String): Project? = queries.selectProjectByName(name).executeAsOneOrNull()?.toDomain()
    override fun projects(): List<Project> = queries.selectProjects().executeAsList().map { it.toDomain() }

    override fun saveRelation(relation: ProjectRelation) = queries.insertProjectRelation(
        relation.id, relation.fromProjectId, relation.relationType, relation.toProjectId, relation.createdAt,
    )

    override fun relations(projectId: String): List<ProjectRelation> =
        queries.selectProjectRelationsForProject(projectId, projectId).executeAsList().map { it.toDomain() }

    private fun Projects.toDomain() = Project(
        id, name, description, status, parent_project_id, created_at, updated_at, metadata,
    )

    private fun Project_relations.toDomain() = ProjectRelation(
        id, from_project_id, relation_type, to_project_id, created_at,
    )
}
