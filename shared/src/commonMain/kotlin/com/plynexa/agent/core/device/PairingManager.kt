package com.plynexa.agent.core.device

import com.plynexa.agent.core.events.AgentEvent
import com.plynexa.agent.core.events.AgentEventType
import com.plynexa.agent.core.events.EventBus
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
data class PairedDevice(
    val id: String,
    val name: String,
    val type: String,
    val lastSeen: Long?,
    val status: String,
    val capabilities: Set<String>,
    val pairedAt: Long?,
)

data class StoredDevice(
    val device: PairedDevice,
    val tokenHash: String?,
    val metadata: String? = null,
)

interface DeviceRepository {
    fun save(device: StoredDevice)
    fun byTokenHash(hash: String): StoredDevice?
    fun devices(): List<StoredDevice>
    fun markSeen(id: String, at: Long)
    fun revoke(id: String)
    fun audit(action: String, deviceId: String, at: Long, result: String)
}

interface TokenHasher {
    fun hash(serverSecret: String, token: String): String
}

interface SecureTokenGenerator {
    fun token(bytes: Int = 32): String
    fun numericCode(digits: Int = 6): String
}

data class PairingSession(val code: String, val expiresAt: Long)
data class PairingResult(val device: PairedDevice, val token: String)

class PairingManager(
    private val repository: DeviceRepository,
    private val hasher: TokenHasher,
    private val generator: SecureTokenGenerator,
    private val serverSecret: String,
    private val eventBus: EventBus,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
    private val onPaired: suspend (PairedDevice) -> Unit = {},
) {
    private val mutex = Mutex()
    private var session: PairingSession? = null
    private var failedAttempts = 0

    suspend fun beginPairing(ttlMillis: Long = 5 * 60_000): PairingSession = mutex.withLock {
        require(ttlMillis in 30_000..15 * 60_000) { "Pairing TTL must be between 30 seconds and 15 minutes" }
        failedAttempts = 0
        PairingSession(generator.numericCode(), timeProvider.nowEpochMillis() + ttlMillis).also { session = it }
    }

    suspend fun pairingStatus(): PairingSession? = mutex.withLock {
        session?.takeIf { it.expiresAt > timeProvider.nowEpochMillis() } ?: run { session = null; null }
    }

    suspend fun pair(
        code: String,
        deviceName: String,
        deviceType: String,
        capabilities: Set<String>,
        requestedDeviceId: String? = null,
    ): PairingResult {
        require(deviceName.isNotBlank()) { "deviceName is required" }
        val now = timeProvider.nowEpochMillis()
        val valid = mutex.withLock {
            val active = session
            if (active == null || active.expiresAt <= now) {
                session = null
                false
            } else if (!constantTimeEquals(active.code, code.trim())) {
                failedAttempts += 1
                if (failedAttempts >= MAX_ATTEMPTS) session = null
                false
            } else {
                session = null
                failedAttempts = 0
                true
            }
        }
        require(valid) { "Pairing code is invalid or expired" }
        val token = generator.token()
        val stableId = requestedDeviceId?.trim()?.takeIf {
            it.length in 8..100 && it.all { char -> char.isLetterOrDigit() || char in "-_" }
        }
        val device = PairedDevice(
            stableId ?: idGenerator.nextId("device"), deviceName.trim(), deviceType.trim().ifBlank { "DESKTOP_CLIENT" },
            now, "ONLINE", capabilities, now,
        )
        repository.save(StoredDevice(device, hasher.hash(serverSecret, token)))
        repository.audit("DEVICE_PAIRED", device.id, now, "SUCCESS")
        eventBus.publish(AgentEvent(
            idGenerator.nextId("event"), AgentEventType.DEVICE_CONNECTED, now,
            device.id, "PairingManager", mapOf("deviceId" to device.id, "deviceType" to device.type),
        ))
        onPaired(device)
        return PairingResult(device, token)
    }

    fun authenticate(token: String): PairedDevice? {
        if (token.isBlank()) return null
        val now = timeProvider.nowEpochMillis()
        val stored = repository.byTokenHash(hasher.hash(serverSecret, token)) ?: return null
        if (stored.device.status == "REVOKED") return null
        repository.markSeen(stored.device.id, now)
        return stored.device.copy(lastSeen = now, status = "ONLINE")
    }

    fun devices(): List<PairedDevice> = repository.devices().map(StoredDevice::device)
        .filterNot { it.status == "REVOKED" }

    fun revoke(deviceId: String) {
        repository.revoke(deviceId)
        repository.audit("DEVICE_REVOKED", deviceId, timeProvider.nowEpochMillis(), "SUCCESS")
    }

    private fun constantTimeEquals(expected: String, supplied: String): Boolean {
        if (expected.length != supplied.length) return false
        var difference = 0
        expected.indices.forEach { difference = difference or (expected[it].code xor supplied[it].code) }
        return difference == 0
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
