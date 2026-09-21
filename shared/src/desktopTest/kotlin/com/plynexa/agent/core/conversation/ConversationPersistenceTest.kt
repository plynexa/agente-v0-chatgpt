package com.plynexa.agent.core.conversation

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.plynexa.agent.core.events.InMemoryEventBus
import com.plynexa.agent.core.model.MessageSource
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider
import com.plynexa.agent.db.AgentDatabase
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest

class ConversationPersistenceTest {
    @Test
    fun conversationAndOriginalMessagesSurviveDatabaseReopen() = runTest {
        val file = Files.createTempFile("agent-v0-conversation", ".db").toFile()
        file.delete()
        val url = "jdbc:sqlite:${file.absolutePath}"

        val firstDriver = JdbcSqliteDriver(url)
        AgentDatabase.Schema.create(firstDriver)
        val firstManager = manager(AgentDatabase(firstDriver))
        val conversation = firstManager.createConversation("Airfry")
        val original = firstManager.appendUserMessage(
            conversation.id,
            "Quando falei sobre checkout da Airfry?",
            MessageSource.CHAT,
            "windows-test",
        )
        firstDriver.close()

        val reopenedDriver = JdbcSqliteDriver(url)
        val reopened = manager(AgentDatabase(reopenedDriver))
        val persisted = reopened.messages(conversation.id)

        assertNotNull(reopened.conversation(conversation.id))
        assertEquals(1, persisted.size)
        assertEquals(original.content, persisted.single().content)
        assertEquals(MessageSource.CHAT, persisted.single().source)
        assertEquals(original.content, reopened.searchMessages("checkout").single().content)

        reopenedDriver.close()
        file.delete()
    }

    private fun manager(database: AgentDatabase): ConversationManager {
        var sequence = 0
        val idGenerator = IdGenerator { prefix -> "$prefix-${sequence++}" }
        val timeProvider = TimeProvider { 1_800_000_000_000L + sequence }
        return ConversationManager(
            repository = SqlDelightConversationRepository(database),
            eventBus = InMemoryEventBus(),
            idGenerator = idGenerator,
            timeProvider = timeProvider,
        )
    }
}
