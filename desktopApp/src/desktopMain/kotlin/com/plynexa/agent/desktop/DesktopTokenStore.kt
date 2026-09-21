package com.plynexa.agent.desktop

import com.sun.jna.platform.win32.Crypt32Util
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.prefs.Preferences

/** Uses Windows DPAPI in production; non-Windows storage is development-only. */
internal class DesktopTokenStore {
    private val preferences = Preferences.userRoot().node("com/plynexa/agent-v0/tokens")
    private val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

    fun put(endpoint: String, token: String) {
        val plain = token.toByteArray(StandardCharsets.UTF_8)
        val protected = if (isWindows) Crypt32Util.cryptProtectData(plain) else plain
        val prefix = if (isWindows) "dpapi:" else "development-only:"
        val stored = prefix + Base64.getEncoder().encodeToString(protected)
        preferences.put(key(endpoint), stored)
        preferences.put(LAST_TOKEN, stored)
    }

    fun get(endpoint: String): String? {
        val stored = preferences.get(key(endpoint), null) ?: preferences.get(LAST_TOKEN, null) ?: return null
        return runCatching {
            val encoded = stored.substringAfter(':')
            val bytes = Base64.getDecoder().decode(encoded)
            val plain = when {
                stored.startsWith("dpapi:") && isWindows -> Crypt32Util.cryptUnprotectData(bytes)
                stored.startsWith("development-only:") && !isWindows -> bytes
                else -> return null
            }
            String(plain, StandardCharsets.UTF_8)
        }.getOrNull()
    }

    fun remove(endpoint: String) {
        preferences.remove(key(endpoint))
        preferences.remove(LAST_TOKEN)
    }

    private fun key(endpoint: String): String = MessageDigest.getInstance("SHA-256")
        .digest(endpoint.toByteArray(StandardCharsets.UTF_8))
        .take(12)
        .joinToString("") { "%02x".format(it) }

    private companion object { const val LAST_TOKEN = "last_host_token" }
}
