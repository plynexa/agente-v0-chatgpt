package com.plynexa.agent.android.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AndroidSecretStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "agent-secure-settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun put(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    fun get(key: String): String? = preferences.getString(key, null)

    fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }
}
