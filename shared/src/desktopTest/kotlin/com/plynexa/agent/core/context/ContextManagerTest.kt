package com.plynexa.agent.core.context

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.plynexa.agent.core.conversation.ConversationManager
import com.plynexa.agent.core.conversation.SqlDelightConversationRepository
import com.plynexa.agent.core.events.InMemoryEventBus
import com.plynexa.agent.core.model.MessageSource
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider
import com.plynexa.agent.db.AgentDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextManagerTest {
    @Test
    fun followUpKeepsAirfryInDeterministicRecentContext() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AgentDatabase.Schema.create(driver)
        var sequence = 0
        val ids = IdGenerator { prefix -> "$prefix-${sequence++}" }
        val clock = TimeProvider { 1_900_000_000_000L + sequence }
        val repository = SqlDelightConversationRepository(AgentDatabase(driver))
        val conversations = ConversationManager(repository, InMemoryEventBus(), ids, clock)
        val conversation = conversations.createConversation()
        conversations.appendUserMessage(
            conversation.id, "Estamos falando da Airfry.", MessageSource.CHAT,
        )
        conversations.appendUserMessage(
            conversation.id, "Qual projeto é esse?", MessageSource.CHAT,
        )

        val context = ContextManager(repository).snapshot(conversation.id)

        assertEquals(2, context.recentMessages.size)
        assertTrue("Airfry" in context.inferredEntities)
        driver.close()
    }
}
