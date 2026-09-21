package com.plynexa.agent.core.project

import com.plynexa.agent.core.model.Project
import com.plynexa.agent.core.model.ProjectRelation

interface ProjectRepository {
    fun save(project: Project)
    fun project(id: String): Project?
    fun projectByName(name: String): Project?
    fun projects(): List<Project>
    fun saveRelation(relation: ProjectRelation)
    fun relations(projectId: String): List<ProjectRelation>
}
