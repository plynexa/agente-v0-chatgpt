package com.plynexa.agent.core.runtime

import com.plynexa.agent.core.agent.AgentCore
import com.plynexa.agent.core.context.ContextManager
import com.plynexa.agent.core.events.EventBus
import com.plynexa.agent.core.memory.MemoryManager
import com.plynexa.agent.core.project.ProjectManager
import com.plynexa.agent.core.reminder.ReminderManager
import com.plynexa.agent.core.router.ActionRouter
import com.plynexa.agent.core.task.TaskManager
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider
import kotlinx.datetime.TimeZone

object DefaultConversationRuntime {
    fun create(
        core: AgentCore,
        contextManager: ContextManager,
        memoryManager: MemoryManager,
        projectManager: ProjectManager,
        taskManager: TaskManager,
        reminderManager: ReminderManager,
        eventBus: EventBus,
        idGenerator: IdGenerator,
        timeProvider: TimeProvider,
    ): AgentConversationRuntime = create(
        core, contextManager, memoryManager, projectManager, taskManager, reminderManager,
        eventBus, idGenerator, timeProvider, TimeZone.currentSystemDefault(),
    )

    fun create(
        core: AgentCore,
        contextManager: ContextManager,
        memoryManager: MemoryManager,
        projectManager: ProjectManager,
        taskManager: TaskManager,
        reminderManager: ReminderManager,
        eventBus: EventBus,
        idGenerator: IdGenerator,
        timeProvider: TimeProvider,
        timeZone: TimeZone,
    ): AgentConversationRuntime = AgentConversationRuntime(
        core, contextManager, memoryManager, projectManager, taskManager, reminderManager,
        ActionRouter(), eventBus, idGenerator, timeProvider, timeZone,
    )
}
