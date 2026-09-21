package com.plynexa.agent.core.voice

import kotlinx.coroutines.flow.Flow

interface WakeWordEngine {
    val detections: Flow<String>
    suspend fun start(wakeWord: String = "Agente")
    suspend fun stop()
}

interface SpeechToTextEngine {
    val partialTranscripts: Flow<String>
    val finalTranscripts: Flow<String>
    suspend fun startListening()
    suspend fun stopListening()
}

interface TextToSpeechEngine {
    val isSpeaking: Flow<Boolean>
    suspend fun speak(text: String)
    suspend fun stop()
}

data class OfflineVoiceModelRequirement(
    val engineId: String,
    val modelName: String,
    val expectedInstallLocation: String,
    val downloadUrl: String? = null,
)
