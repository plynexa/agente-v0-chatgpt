package com.plynexa.agent.core.memory

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.plynexa.agent.core.events.InMemoryEventBus
import com.plynexa.agent.core.model.MemoryType
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider
import com.plynexa.agent.db.AgentDatabase
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemoryPersistenceTest {
    @Test
    fun yasminSemanticMemorySurvivesRestartAndIsSearchable() = runTest {
        val file = Files.createTempFile("agent-v0-yasmin", ".db").toFile()
        file.delete()
        val url = "jdbc:sqlite:${file.absolutePath}"
        val ids = sequentialIds()
        val clock = TimeProvider { 1_900_000_000_000L }

        val firstDriver = JdbcSqliteDriver(url)
        AgentDatabase.Schema.create(firstDriver)
        val first = MemoryManager(
            SqlDelightMemoryRepository(AgentDatabase(firstDriver)), InMemoryEventBus(), ids, clock,
        )
        first.remember("Yasmin é minha modelo YA.", MemoryType.SEMANTIC, importance = 0.9)
        val yasmin = first.createEntity("Yasmin", "model", listOf("YA"))
        val role = first.createEntity("modelo YA", "role")
        first.relate(yasmin, "role", role)
        firstDriver.close()

        val reopenedDriver = JdbcSqliteDriver(url)
        val reopened = MemoryManager(
            SqlDelightMemoryRepository(AgentDatabase(reopenedDriver)), InMemoryEventBus(), ids, clock,
        )
        val results = reopened.search("Yasmin")
        val persistedEntity = reopened.entity("Yasmin")

        assertEquals("Yasmin é minha modelo YA.", results.single().memory.content)
        assertTrue(results.single().score > 0.5)
        assertEquals("model", persistedEntity?.type)
        assertEquals(1, reopened.entityRelations(persistedEntity!!.id).size)
        reopenedDriver.close()
        file.delete()
    }

    private fun sequentialIds(): IdGenerator {
        var value = 0
        return IdGenerator { prefix -> "$prefix-${value++}" }
    }
}
