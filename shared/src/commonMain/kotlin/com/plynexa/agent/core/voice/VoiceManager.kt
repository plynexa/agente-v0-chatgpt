package com.plynexa.agent.core.voice

import com.plynexa.agent.core.events.AgentEvent
import com.plynexa.agent.core.events.AgentEventType
import com.plynexa.agent.core.events.EventBus
import com.plynexa.agent.core.model.VoiceState
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class VoiceManager(
    private val wakeWordEngine: WakeWordEngine,
    private val speechToText: SpeechToTextEngine,
    private val textToSpeech: TextToSpeechEngine,
    private val eventBus: EventBus,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(VoiceState.STANDBY)
    val state: StateFlow<VoiceState> = mutableState.asStateFlow()

    suspend fun startStandby() {
        if (state.value == VoiceState.MIC_MUTED) return
        speechToText.stopListening()
        textToSpeech.stop()
        wakeWordEngine.start("Agente")
        transition(VoiceState.STANDBY)
    }

    suspend fun onWakeWord(word: String): Boolean {
        if (state.value != VoiceState.STANDBY || !word.equals("Agente", ignoreCase = true)) return false
        wakeWordEngine.stop()
        transition(VoiceState.LISTENING)
        speechToText.startListening()
        publish(AgentEventType.VOICE_WAKE, mapOf("wakeWord" to "Agente"))
        return true
    }

    suspend fun acknowledgeWake(text: String = "Estou aqui.") {
        require(state.value == VoiceState.LISTENING)
        speechToText.stopListening()
        transition(VoiceState.SPEAKING)
        publish(AgentEventType.VOICE_STARTED, mapOf("wakeAcknowledgement" to "true"))
        textToSpeech.speak(text)
    }

    suspend fun onFinalTranscript(text: String) {
        require(state.value == VoiceState.LISTENING) { "Transcript requires LISTENING state" }
        speechToText.stopListening()
        transition(VoiceState.THINKING)
        publish(AgentEventType.VOICE_TRANSCRIPT, mapOf("length" to text.length.toString()))
    }

    suspend fun beginProcessing() {
        require(state.value == VoiceState.THINKING)
        transition(VoiceState.PROCESSING)
    }

    suspend fun speak(text: String) {
        require(state.value == VoiceState.THINKING || state.value == VoiceState.PROCESSING)
        require(text.isNotBlank())
        transition(VoiceState.SPEAKING)
        publish(AgentEventType.VOICE_STARTED)
        textToSpeech.speak(text)
    }

    suspend fun onSpeechFinished() {
        require(state.value == VoiceState.SPEAKING)
        publish(AgentEventType.VOICE_FINISHED)
        transition(VoiceState.LISTENING)
        speechToText.startListening()
    }

    /** Barge-in: a real platform audio detector calls this while TTS is active. */
    suspend fun onUserSpeechDetected(): Boolean {
        if (state.value != VoiceState.SPEAKING) return false
        textToSpeech.stop()
        publish(AgentEventType.VOICE_FINISHED, mapOf("interrupted" to "true"))
        transition(VoiceState.LISTENING)
        speechToText.startListening()
        return true
    }

    suspend fun standby() {
        if (state.value == VoiceState.MIC_MUTED) return
        speechToText.stopListening()
        textToSpeech.stop()
        wakeWordEngine.start("Agente")
        transition(VoiceState.STANDBY)
    }

    suspend fun muteMicrophone() {
        wakeWordEngine.stop()
        speechToText.stopListening()
        textToSpeech.stop()
        transition(VoiceState.MIC_MUTED)
    }

    suspend fun unmuteToStandby() {
        require(state.value == VoiceState.MIC_MUTED)
        transition(VoiceState.STANDBY)
        wakeWordEngine.start("Agente")
    }

    suspend fun fail(errorType: String) {
        wakeWordEngine.stop()
        speechToText.stopListening()
        textToSpeech.stop()
        transition(VoiceState.ERROR, mapOf("errorType" to errorType))
    }

    private suspend fun transition(next: VoiceState, metadata: Map<String, String> = emptyMap()) {
        mutex.withLock { mutableState.value = next }
        publish(AgentEventType.AGENT_STATE_CHANGED, metadata + ("voiceState" to next.name))
    }

    private suspend fun publish(type: AgentEventType, metadata: Map<String, String> = emptyMap()) {
        eventBus.publish(AgentEvent(
            idGenerator.nextId("event"), type, timeProvider.nowEpochMillis(),
            source = "VoiceManager", metadata = metadata,
        ))
    }
}
