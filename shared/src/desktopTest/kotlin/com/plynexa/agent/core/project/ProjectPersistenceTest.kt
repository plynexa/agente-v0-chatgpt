package com.plynexa.agent.core.project

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider
import com.plynexa.agent.db.AgentDatabase
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ProjectPersistenceTest {
    @Test
    fun airfryBelongsToTrendoRelationSurvivesRestart() {
        val file = Files.createTempFile("agent-v0-projects", ".db").toFile()
        file.delete()
        val url = "jdbc:sqlite:${file.absolutePath}"
        var sequence = 0
        val ids = IdGenerator { prefix -> "$prefix-${sequence++}" }
        val clock = TimeProvider { 1_900_000_000_000L + sequence }

        val firstDriver = JdbcSqliteDriver(url)
        AgentDatabase.Schema.create(firstDriver)
        val first = ProjectManager(SqlDelightProjectRepository(AgentDatabase(firstDriver)), ids, clock)
        val trendo = first.create("Trendo")
        val airfry = first.create("Airfry")
        first.relate(airfry, "belongs_to", trendo)
        firstDriver.close()

        val reopenedDriver = JdbcSqliteDriver(url)
        val reopened = ProjectManager(SqlDelightProjectRepository(AgentDatabase(reopenedDriver)), ids, clock)
        val savedAirfry = reopened.project("Airfry")
        val relation = reopened.relations(savedAirfry!!.id).single()

        assertNotNull(reopened.project("Trendo"))
        assertEquals("belongs_to", relation.relationType)
        assertEquals("Trendo", reopened.relatedProjects(savedAirfry.id).single().name)
        reopenedDriver.close()
        file.delete()
    }
}
