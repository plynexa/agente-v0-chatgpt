package com.plynexa.agent.core.device

import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.db.AgentDatabase
import com.plynexa.agent.db.Devices

class SqlDelightDeviceRepository(
    private val database: AgentDatabase,
    private val idGenerator: IdGenerator,
) : DeviceRepository {
    private val queries get() = database.agentQueries

    override fun save(device: StoredDevice) = with(device) {
        queries.upsertDevice(
            this.device.id, this.device.name, this.device.type, this.device.lastSeen,
            this.device.status, this.device.capabilities.sorted().joinToString("|"),
            this.device.pairedAt, tokenHash, metadata,
        )
    }

    override fun byTokenHash(hash: String): StoredDevice? =
        queries.selectDeviceByTokenHash(hash).executeAsOneOrNull()?.toDomain()

    override fun devices(): List<StoredDevice> = queries.selectDevices().executeAsList().map { it.toDomain() }
    override fun markSeen(id: String, at: Long) = queries.updateDeviceSeen(at, id)
    override fun revoke(id: String) = queries.revokeDevice(id)

    override fun audit(action: String, deviceId: String, at: Long, result: String) {
        queries.insertAuditLog(
            idGenerator.nextId("audit"), at, action, "SYSTEM", deviceId,
            "DEVICE", deviceId, result, null,
        )
    }

    private fun Devices.toDomain() = StoredDevice(
        PairedDevice(
            id, name, type, last_seen, status,
            capabilities.split('|').filter(String::isNotBlank).toSet(), paired_at,
        ),
        token_hash,
        metadata,
    )
}
