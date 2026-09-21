package com.plynexa.agent.core.task

import com.plynexa.agent.core.events.AgentEvent
import com.plynexa.agent.core.events.AgentEventType
import com.plynexa.agent.core.events.EventBus
import com.plynexa.agent.core.model.AgentTask
import com.plynexa.agent.core.model.TaskEventRecord
import com.plynexa.agent.core.model.TaskStatus
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface TaskWork {
    suspend fun execute(context: TaskExecutionContext): String?
}

class TaskExecutionContext internal constructor(
    val taskId: String,
    private val manager: TaskManager,
) {
    suspend fun waiting(reason: String) = manager.markWaiting(taskId, reason)
    suspend fun progress(value: Double, evidence: String) = manager.reportProgress(taskId, value, evidence)
}

class TaskManager(
    private val repository: TaskRepository,
    private val eventBus: EventBus,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
    parentScope: CoroutineScope,
    workerCount: Int = 2,
) {
    private data class Pending(val task: AgentTask, val work: TaskWork)
    private val supervisor = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + supervisor)
    private val mutex = Mutex()
    private val pending = mutableListOf<Pending>()
    private val running = mutableMapOf<String, Job>()
    private val signal = Channel<Unit>(Channel.UNLIMITED)
    private val workers: List<Job>

    init {
        require(workerCount > 0)
        workers = List(workerCount) { scope.launch { workerLoop() } }
    }

    suspend fun submit(
        type: String,
        description: String,
        priority: Int = 0,
        conversationId: String? = null,
        parentTaskId: String? = null,
        metadata: String? = null,
        work: TaskWork,
    ): AgentTask {
        val task = AgentTask(
            id = idGenerator.nextId("task"), type = type, description = description,
            status = TaskStatus.QUEUED, priority = priority,
            createdAt = timeProvider.nowEpochMillis(), parentTaskId = parentTaskId,
            conversationId = conversationId, metadata = metadata,
        )
        repository.save(task)
        record(task, AgentEventType.TASK_CREATED)
        mutex.withLock {
            pending += Pending(task, work)
            pending.sortWith(compareByDescending<Pending> { it.task.priority }.thenBy { it.task.createdAt })
        }
        signal.trySend(Unit)
        return task
    }

    suspend fun cancel(taskId: String): Boolean {
        val queued = mutex.withLock {
            val index = pending.indexOfFirst { it.task.id == taskId }
            if (index >= 0) pending.removeAt(index) else null
        }
        if (queued != null) {
            finish(queued.task, TaskStatus.CANCELLED)
            return true
        }
        return mutex.withLock { running[taskId] }?.let { job ->
            job.cancel(CancellationException("Task cancelled by request"))
            true
        } ?: false
    }

    fun task(id: String): AgentTask? = repository.task(id)
    fun tasks(): List<AgentTask> = repository.tasks()
    fun events(id: String): List<TaskEventRecord> = repository.events(id)

    internal suspend fun markWaiting(taskId: String, reason: String) {
        val current = repository.task(taskId) ?: return
        if (current.status != TaskStatus.RUNNING) return
        val waiting = current.copy(status = TaskStatus.WAITING)
        repository.update(waiting)
        record(waiting, AgentEventType.TASK_WAITING, reason)
    }

    internal suspend fun reportProgress(taskId: String, value: Double, evidence: String) {
        require(value in 0.0..1.0)
        require(evidence.isNotBlank()) { "Progress requires real calculation/provider evidence" }
        val current = repository.task(taskId) ?: return
        val updated = current.copy(status = TaskStatus.RUNNING, progress = value)
        repository.update(updated)
        record(updated, AgentEventType.TASK_PROGRESS, evidence)
    }

    suspend fun close() {
        signal.close()
        workers.forEach(Job::cancel)
        mutex.withLock { running.values.toList() }.forEach(Job::cancel)
        supervisor.cancel()
        workers.joinAll()
        supervisor.join()
    }

    private suspend fun workerLoop() {
        for (ignored in signal) {
            val next = mutex.withLock { if (pending.isEmpty()) null else pending.removeAt(0) } ?: continue
            val job = scope.launch { execute(next) }
            mutex.withLock { running[next.task.id] = job }
            job.join()
            mutex.withLock { running.remove(next.task.id) }
        }
    }

    private suspend fun execute(pendingTask: Pending) {
        var task = pendingTask.task.copy(
            status = TaskStatus.RUNNING,
            startedAt = timeProvider.nowEpochMillis(),
            attempts = pendingTask.task.attempts + 1,
        )
        repository.update(task)
        record(task, AgentEventType.TASK_STARTED)
        try {
            val result = pendingTask.work.execute(TaskExecutionContext(task.id, this))
            task = repository.task(task.id) ?: task
            finish(task.copy(result = result), TaskStatus.COMPLETED)
        } catch (cancelled: CancellationException) {
            finish(repository.task(task.id) ?: task, TaskStatus.CANCELLED)
            throw cancelled
        } catch (error: Throwable) {
            finish(task.copy(error = error.message ?: error::class.simpleName), TaskStatus.FAILED)
        }
    }

    private suspend fun finish(task: AgentTask, status: TaskStatus) {
        val finished = task.copy(status = status, finishedAt = timeProvider.nowEpochMillis())
        repository.update(finished)
        val eventType = when (status) {
            TaskStatus.COMPLETED -> AgentEventType.TASK_COMPLETED
            TaskStatus.FAILED -> AgentEventType.TASK_FAILED
            TaskStatus.CANCELLED -> AgentEventType.TASK_CANCELLED
            else -> error("Invalid terminal task state: $status")
        }
        record(finished, eventType, finished.error ?: finished.result)
    }

    private suspend fun record(task: AgentTask, type: AgentEventType, details: String? = null) {
        val now = timeProvider.nowEpochMillis()
        repository.appendEvent(TaskEventRecord(idGenerator.nextId("task-event"), task.id, type.name, now, details))
        eventBus.publish(
            AgentEvent(
                idGenerator.nextId("event"), type, now, task.id, "TaskManager",
                mapOf("taskId" to task.id, "status" to task.status.name),
            ),
        )
    }
}
