package com.plynexa.agent.core.task

import com.plynexa.agent.core.model.AgentTask
import com.plynexa.agent.core.model.TaskEventRecord
import com.plynexa.agent.core.model.TaskStatus

interface TaskRepository {
    fun save(task: AgentTask)
    fun task(id: String): AgentTask?
    fun tasks(): List<AgentTask>
    fun tasks(status: TaskStatus): List<AgentTask>
    fun update(task: AgentTask)
    fun appendEvent(event: TaskEventRecord)
    fun events(taskId: String): List<TaskEventRecord>
}
