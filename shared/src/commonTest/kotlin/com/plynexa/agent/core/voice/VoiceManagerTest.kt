package com.plynexa.agent.core.voice

import com.plynexa.agent.core.events.InMemoryEventBus
import com.plynexa.agent.core.model.VoiceState
import com.plynexa.agent.core.util.IdGenerator
import com.plynexa.agent.core.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceManagerTest {
    @Test
    fun requiredStateFlowAndMicrophoneMuteAreEnforced() = runTest {
        val wake = FakeWakeWord()
        val stt = FakeStt()
        val tts = FakeTts()
        val manager = manager(wake, stt, tts)

        assertEquals(VoiceState.STANDBY, manager.state.value)
        assertTrue(manager.onWakeWord("Agente"))
        assertEquals(VoiceState.LISTENING, manager.state.value)
        manager.onFinalTranscript("Qual era o preço da Airfry?")
        assertEquals(VoiceState.THINKING, manager.state.value)
        manager.speak("O último preço registrado é R$ 29,90.")
        assertEquals(VoiceState.SPEAKING, manager.state.value)
        manager.standby()
        assertEquals(VoiceState.STANDBY, manager.state.value)

        manager.muteMicrophone()
        assertEquals(VoiceState.MIC_MUTED, manager.state.value)
        assertFalse(wake.running)
        assertFalse(stt.listening)
        assertFalse(manager.onWakeWord("Agente"))
        manager.unmuteToStandby()
        assertEquals(VoiceState.STANDBY, manager.state.value)
        assertTrue(wake.running)
    }

    @Test
    fun bargeInStopsTtsAndReturnsToListening() = runTest {
        val wake = FakeWakeWord()
        val stt = FakeStt()
        val tts = FakeTts()
        val manager = manager(wake, stt, tts)
        manager.onWakeWord("Agente")
        manager.onFinalTranscript("Fale")
        manager.beginProcessing()
        manager.speak("Resposta longa")

        assertTrue(manager.onUserSpeechDetected())
        assertTrue(tts.stopped)
        assertEquals(VoiceState.LISTENING, manager.state.value)
        assertTrue(stt.listening)
    }

    private fun manager(wake: FakeWakeWord, stt: FakeStt, tts: FakeTts): VoiceManager {
        var sequence = 0
        return VoiceManager(
            wake, stt, tts, InMemoryEventBus(),
            IdGenerator { prefix -> "$prefix-${sequence++}" }, TimeProvider { 1L + sequence },
        )
    }

    private class FakeWakeWord : WakeWordEngine {
        override val detections: Flow<String> = MutableSharedFlow()
        var running = false
        override suspend fun start(wakeWord: String) { running = true }
        override suspend fun stop() { running = false }
    }

    private class FakeStt : SpeechToTextEngine {
        override val partialTranscripts: Flow<String> = MutableSharedFlow()
        override val finalTranscripts: Flow<String> = MutableSharedFlow()
        var listening = false
        override suspend fun startListening() { listening = true }
        override suspend fun stopListening() { listening = false }
    }

    private class FakeTts : TextToSpeechEngine {
        override val isSpeaking: Flow<Boolean> = MutableStateFlow(false)
        var stopped = false
        override suspend fun speak(text: String) { stopped = false }
        override suspend fun stop() { stopped = true }
    }
}
