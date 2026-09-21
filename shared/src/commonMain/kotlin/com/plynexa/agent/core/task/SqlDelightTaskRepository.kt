package com.plynexa.agent.core.task

import com.plynexa.agent.core.model.AgentTask
import com.plynexa.agent.core.model.TaskEventRecord
import com.plynexa.agent.core.model.TaskStatus
import com.plynexa.agent.db.AgentDatabase
import com.plynexa.agent.db.Task_events
import com.plynexa.agent.db.Tasks

class SqlDelightTaskRepository(private val database: AgentDatabase) : TaskRepository {
    private val queries get() = database.agentQueries

    override fun save(task: AgentTask) = queries.insertTask(
        task.id, task.type, task.description, task.status.name, task.priority.toLong(), task.createdAt,
        task.startedAt, task.finishedAt, task.progress, task.result, task.error, task.attempts,
        task.parentTaskId, task.conversationId, task.metadata,
    )

    override fun task(id: String): AgentTask? = queries.selectTaskById(id).executeAsOneOrNull()?.toDomain()
    override fun tasks(): List<AgentTask> = queries.selectTasks().executeAsList().map { it.toDomain() }
    override fun tasks(status: TaskStatus): List<AgentTask> =
        queries.selectTasksByStatus(status.name).executeAsList().map { it.toDomain() }

    override fun update(task: AgentTask) = queries.updateTaskState(
        task.status.name, task.startedAt, task.finishedAt, task.progress,
        task.result, task.error, task.attempts, task.id,
    )

    override fun appendEvent(event: TaskEventRecord) = queries.insertTaskEvent(
        event.id, event.taskId, event.eventType, event.timestamp, event.details,
    )

    override fun events(taskId: String): List<TaskEventRecord> =
        queries.selectTaskEvents(taskId).executeAsList().map { it.toDomain() }

    private fun Tasks.toDomain() = AgentTask(
        id, type, description, TaskStatus.valueOf(status), priority.toInt(), created_at,
        started_at, finished_at, progress, result, error, attempts,
        parent_task_id, conversation_id, metadata,
    )

    private fun Task_events.toDomain() = TaskEventRecord(id, task_id, event_type, timestamp, details)
}
