package com.plynexa.agent.core.util

import kotlinx.datetime.Clock
import kotlin.random.Random

fun interface TimeProvider {
    fun nowEpochMillis(): Long
}

object SystemTimeProvider : TimeProvider {
    override fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
}

fun interface IdGenerator {
    fun nextId(prefix: String): String
}

class RandomIdGenerator(
    private val timeProvider: TimeProvider = SystemTimeProvider,
) : IdGenerator {
    override fun nextId(prefix: String): String {
        val random = Random.nextBytes(12).joinToString("") { byte ->
            byte.toUByte().toString(16).padStart(2, '0')
        }
        return "$prefix-${timeProvider.nowEpochMillis()}-$random"
    }
}
