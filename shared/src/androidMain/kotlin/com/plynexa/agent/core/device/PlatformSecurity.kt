package com.plynexa.agent.core.device

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

class PlatformTokenHasher : TokenHasher {
    override fun hash(serverSecret: String, token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$serverSecret:$token".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

class PlatformSecureTokenGenerator : SecureTokenGenerator {
    private val random = SecureRandom()
    override fun token(bytes: Int): String = ByteArray(bytes).also(random::nextBytes).let {
        Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
    override fun numericCode(digits: Int): String = buildString(digits) {
        repeat(digits) { append(random.nextInt(10)) }
    }
}
