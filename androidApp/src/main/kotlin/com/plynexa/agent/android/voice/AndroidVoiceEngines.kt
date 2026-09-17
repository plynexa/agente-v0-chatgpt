package com.plynexa.agent.android.voice

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.annotation.RequiresApi
import com.plynexa.agent.core.voice.SpeechToTextEngine
import com.plynexa.agent.core.voice.TextToSpeechEngine
import com.plynexa.agent.core.voice.WakeWordEngine
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AndroidOnDeviceSpeechEngine(
    context: Context,
) : WakeWordEngine, SpeechToTextEngine, AutoCloseable {
    private enum class Mode { IDLE, WAKE_WORD, TRANSCRIPT }

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val wakeDetections = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private val partials = MutableSharedFlow<String>(extraBufferCapacity = 16)
    private val finals = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private var recognizer: SpeechRecognizer? = null
    private var mode = Mode.IDLE
    private var wakeWord = "Agente"
    private var explicitlyStopped = true

    val isOnDeviceAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)

    override val detections: Flow<String> = wakeDetections.asSharedFlow()
    override val partialTranscripts: Flow<String> = partials.asSharedFlow()
    override val finalTranscripts: Flow<String> = finals.asSharedFlow()

    override suspend fun start(wakeWord: String) = withContext(Dispatchers.Main.immediate) {
        require(isOnDeviceAvailable) { "On-device speech recognition is unavailable" }
        this@AndroidOnDeviceSpeechEngine.wakeWord = wakeWord
        explicitlyStopped = false
        mode = Mode.WAKE_WORD
        ensureRecognizer().startListening(recognitionIntent(partial = true))
    }

    override suspend fun stop() = withContext(Dispatchers.Main.immediate) {
        explicitlyStopped = true
        mode = Mode.IDLE
        recognizer?.cancel()
        Unit
    }

    override suspend fun startListening() = withContext(Dispatchers.Main.immediate) {
        require(isOnDeviceAvailable) { "On-device speech recognition is unavailable" }
        explicitlyStopped = false
        mode = Mode.TRANSCRIPT
        ensureRecognizer().startListening(recognitionIntent(partial = true))
    }

    override suspend fun stopListening() {
        stop()
    }

    override fun close() {
        handler.post {
            explicitlyStopped = true
            recognizer?.destroy()
            recognizer = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun createOnDeviceRecognizer(): SpeechRecognizer =
        SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)

    @SuppressLint("NewApi") // Guarded immediately below; kept centralized for API 26-30 safety.
    private fun ensureRecognizer(): SpeechRecognizer {
        recognizer?.let { return it }
        check(Looper.myLooper() == Looper.getMainLooper())
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "On-device recognition requires Android 12 or newer"
        }
        return createOnDeviceRecognizer().also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
    }

    private fun recognitionIntent(partial: Boolean) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, partial)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            val text = results(partialResults).firstOrNull().orEmpty()
            if (mode == Mode.TRANSCRIPT && text.isNotBlank()) partials.tryEmit(text)
            if (mode == Mode.WAKE_WORD && containsWakeWord(text)) emitWakeAndStop()
        }

        override fun onResults(results: Bundle?) {
            val values = results(results)
            when (mode) {
                Mode.WAKE_WORD -> {
                    if (values.any(::containsWakeWord)) emitWakeAndStop() else restartWakeWord()
                }
                Mode.TRANSCRIPT -> {
                    values.firstOrNull()?.takeIf(String::isNotBlank)?.let(finals::tryEmit)
                    explicitlyStopped = true
                    mode = Mode.IDLE
                }
                Mode.IDLE -> Unit
            }
        }

        override fun onError(error: Int) {
            if (mode == Mode.WAKE_WORD && !explicitlyStopped) restartWakeWord()
            else if (mode == Mode.TRANSCRIPT) {
                explicitlyStopped = true
                mode = Mode.IDLE
            }
        }
    }

    private fun results(bundle: Bundle?): List<String> =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()

    private fun containsWakeWord(value: String): Boolean =
        value.split(Regex("\\s+")).any { it.equals(wakeWord, ignoreCase = true) }

    private fun emitWakeAndStop() {
        if (mode != Mode.WAKE_WORD) return
        wakeDetections.tryEmit(wakeWord)
        explicitlyStopped = true
        mode = Mode.IDLE
        recognizer?.cancel()
    }

    private fun restartWakeWord() {
        if (explicitlyStopped || mode != Mode.WAKE_WORD) return
        handler.postDelayed({
            if (!explicitlyStopped && mode == Mode.WAKE_WORD) {
                runCatching { ensureRecognizer().startListening(recognitionIntent(partial = true)) }
            }
        }, 350)
    }
}

class AndroidLocalTextToSpeechEngine(context: Context) : TextToSpeechEngine, AutoCloseable {
    private val ready = CompletableDeferred<Unit>()
    private val speaking = MutableStateFlow(false)
    private lateinit var engine: TextToSpeech

    override val isSpeaking: Flow<Boolean> = speaking.asStateFlow()

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine.language = Locale("pt", "BR")
                engine.voices.firstOrNull { voice ->
                    voice.locale.language == "pt" &&
                        !voice.features.contains(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS)
                }?.let { engine.voice = it }
                if (!ready.isCompleted) ready.complete(Unit)
            } else if (!ready.isCompleted) {
                ready.completeExceptionally(IllegalStateException("Android TTS initialization failed: $status"))
            }
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { speaking.value = true }
            override fun onDone(utteranceId: String?) { speaking.value = false }
            @Deprecated("Deprecated in Android")
            override fun onError(utteranceId: String?) { speaking.value = false }
        })
    }

    override suspend fun speak(text: String) {
        ready.await()
        withContext(Dispatchers.Main.immediate) {
            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "agent-${System.currentTimeMillis()}")
            check(result == TextToSpeech.SUCCESS) { "Android TTS rejected utterance" }
        }
    }

    override suspend fun stop() = withContext(Dispatchers.Main.immediate) {
        engine.stop()
        speaking.value = false
    }

    override fun close() {
        engine.stop()
        engine.shutdown()
    }
}
