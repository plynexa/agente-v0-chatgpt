package com.plynexa.agent.android.host

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import androidx.core.content.ContextCompat
import com.plynexa.agent.android.notification.AndroidReminderNotificationAdapter
import com.plynexa.agent.android.backup.AndroidLocalBackupProvider
import com.plynexa.agent.android.security.AndroidSecretStore
import com.plynexa.agent.android.security.AndroidProviderConfigurationStore
import com.plynexa.agent.android.voice.AndroidLocalTextToSpeechEngine
import com.plynexa.agent.android.voice.AndroidOnDeviceSpeechEngine
import com.plynexa.agent.core.agent.AgentCore
import com.plynexa.agent.core.agent.AgentStateStore
import com.plynexa.agent.core.api.HostApiServices
import com.plynexa.agent.core.api.configureHostApi
import com.plynexa.agent.core.conversation.ConversationManager
import com.plynexa.agent.core.conversation.SqlDelightConversationRepository
import com.plynexa.agent.core.context.ContextManager
import com.plynexa.agent.core.events.InMemoryEventBus
import com.plynexa.agent.core.device.PairingManager
import com.plynexa.agent.core.device.PlatformSecureTokenGenerator
import com.plynexa.agent.core.device.PlatformTokenHasher
import com.plynexa.agent.core.device.SqlDelightDeviceRepository
import com.plynexa.agent.core.memory.MemoryManager
import com.plynexa.agent.core.memory.SqlDelightMemoryRepository
import com.plynexa.agent.core.model.MessageSource
import com.plynexa.agent.core.model.BackupRecord
import com.plynexa.agent.core.model.VoiceState
import com.plynexa.agent.core.reminder.ReminderManager
import com.plynexa.agent.core.reminder.SqlDelightReminderRepository
import com.plynexa.agent.core.project.ProjectManager
import com.plynexa.agent.core.project.SqlDelightProjectRepository
import com.plynexa.agent.core.runtime.DefaultConversationRuntime
import com.plynexa.agent.core.skill.EchoSkill
import com.plynexa.agent.core.skill.MemorySkill
import com.plynexa.agent.core.skill.ReminderSkill
import com.plynexa.agent.core.skill.SystemStatusSkill
import com.plynexa.agent.core.task.SqlDelightTaskRepository
import com.plynexa.agent.core.task.TaskManager
import com.plynexa.agent.core.util.RandomIdGenerator
import com.plynexa.agent.core.util.SystemTimeProvider
import com.plynexa.agent.core.voice.VoiceManager
import com.plynexa.agent.db.AgentDatabase
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

data class HostUiState(
    val running: Boolean = false,
    val voiceState: VoiceState = VoiceState.MIC_MUTED,
    val apiHost: String = "127.0.0.1",
    val apiPort: Int = 8787,
    val lanEnabled: Boolean = false,
    val onDeviceSpeechAvailable: Boolean = false,
    val statusText: String = "Agente parado",
    val pairingCode: String? = null,
    val pairingExpiresAt: Long? = null,
    val error: String? = null,
)

object AgentHostState {
    private val mutableState = MutableStateFlow(HostUiState())
    val state: StateFlow<HostUiState> = mutableState.asStateFlow()
    internal fun update(transform: (HostUiState) -> HostUiState) {
        mutableState.value = transform(mutableState.value)
    }
}

class AndroidAgentRuntime(
    context: Context,
    initialLanEnabled: Boolean,
    private val scheduleRestore: (BackupRecord) -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val rootJob = SupervisorJob()
    private val scope = CoroutineScope(rootJob + Dispatchers.Default)
    private val clock = SystemTimeProvider
    private val ids = RandomIdGenerator(clock)
    private val events = InMemoryEventBus(512)
    private val driver = AndroidSqliteDriver(AgentDatabase.Schema, appContext, "agent-v0.db")
    private val database = AgentDatabase(driver)
    private val secretStore = AndroidSecretStore(appContext)
    private val providerConfigurations = AndroidProviderConfigurationStore(secretStore)
    private val tokenGenerator = PlatformSecureTokenGenerator()
    private val serverSecret = secretStore.get(KEY_SERVER_SECRET) ?: tokenGenerator.token().also {
        secretStore.put(KEY_SERVER_SECRET, it)
    }
    private var lanEnabled = initialLanEnabled
    private val conversationRepository = SqlDelightConversationRepository(database)
    private val conversations = ConversationManager(conversationRepository, events, ids, clock)
    private val stateStore = AgentStateStore(events, ids, clock)
    val core = AgentCore(stateStore, conversations, scope)
    val memories = MemoryManager(SqlDelightMemoryRepository(database), events, ids, clock)
    val projects = ProjectManager(SqlDelightProjectRepository(database), ids, clock)
    val tasks = TaskManager(SqlDelightTaskRepository(database), events, ids, clock, scope, workerCount = 2)
    val reminders = ReminderManager(
        SqlDelightReminderRepository(database), events, ids, clock,
        AndroidReminderNotificationAdapter(appContext),
    )
    val conversationRuntime = DefaultConversationRuntime.create(
        core = core,
        contextManager = ContextManager(conversationRepository),
        memoryManager = memories,
        projectManager = projects,
        taskManager = tasks,
        reminderManager = reminders,
        eventBus = events,
        idGenerator = ids,
        timeProvider = clock,
    )
    private val speech = AndroidOnDeviceSpeechEngine(appContext)
    private val tts = AndroidLocalTextToSpeechEngine(appContext)
    val voice = VoiceManager(speech, speech, tts, events, ids, clock)
    private val skills by lazy {
        listOf(
            EchoSkill(clock).definition,
            SystemStatusSkill(stateStore, clock).definition,
            MemorySkill(memories, clock).definition,
            ReminderSkill(reminders, clock).definition,
        )
    }
    private val pairing = PairingManager(
        SqlDelightDeviceRepository(database, ids), PlatformTokenHasher(), tokenGenerator,
        serverSecret, events, ids, clock,
    ) {
        lanEnabled = true
        secretStore.put(AgentHostService.KEY_LAN_ENABLED, "true")
        stateStore.update { it.copy(lanEnabled = true) }
        AgentHostState.update {
            it.copy(lanEnabled = true, apiHost = lanAddress(), pairingCode = null, pairingExpiresAt = null)
        }
    }
    val backups = AndroidLocalBackupProvider(
        appContext, database, events, ids, clock, scheduleRestore,
    )
    private var server: EmbeddedServer<*, *>? = null
    private var reminderJob: Job? = null
    private var pairingExpiryJob: Job? = null
    private var defaultConversationId: String = ""

    suspend fun start() {
        core.start()
        stateStore.update { it.copy(lanEnabled = lanEnabled) }
        defaultConversationId = conversations.conversations().firstOrNull()?.id
            ?: conversations.createConversation("Conversa principal").id
        startServer()
        reminderJob = reminders.startScheduler(scope)
        connectVoicePipeline()
        if (canUseMicrophone() && speech.isOnDeviceAvailable) {
            runCatching { voice.startStandby() }
                .onFailure { voice.fail(it::class.simpleName ?: "VoiceStartError") }
        } else {
            voice.muteMicrophone()
        }
        AgentHostState.update {
            it.copy(
                running = true,
                voiceState = voice.state.value,
                apiHost = if (lanEnabled) lanAddress() else "127.0.0.1",
                lanEnabled = lanEnabled,
                onDeviceSpeechAvailable = speech.isOnDeviceAvailable,
                statusText = statusFor(voice.state.value),
                error = null,
            )
        }
    }

    suspend fun enableVoice() {
        require(canUseMicrophone()) { "Microphone permission is not granted" }
        require(speech.isOnDeviceAvailable) { "On-device speech recognition is unavailable" }
        if (voice.state.value == VoiceState.MIC_MUTED) voice.unmuteToStandby() else voice.startStandby()
    }

    suspend fun standby() = voice.standby()
    suspend fun mute() = voice.muteMicrophone()

    suspend fun enablePairing() {
        val session = pairing.beginPairing()
        if (!lanEnabled) restartServer(bindLan = true)
        AgentHostState.update {
            it.copy(
                apiHost = lanAddress(), pairingCode = session.code,
                pairingExpiresAt = session.expiresAt,
                statusText = "Pareamento ativo — código ${session.code}", error = null,
            )
        }
        pairingExpiryJob?.cancel()
        pairingExpiryJob = scope.launch {
            delay((session.expiresAt - clock.nowEpochMillis()).coerceAtLeast(1))
            if (pairing.pairingStatus() == null) {
                if (!lanEnabled) restartServer(bindLan = false)
                AgentHostState.update {
                    it.copy(
                        apiHost = if (lanEnabled) lanAddress() else "127.0.0.1",
                        pairingCode = null, pairingExpiresAt = null,
                        statusText = statusFor(voice.state.value),
                    )
                }
            }
        }
    }

    suspend fun disableLan() {
        pairingExpiryJob?.cancel()
        lanEnabled = false
        secretStore.put(AgentHostService.KEY_LAN_ENABLED, "false")
        stateStore.update { it.copy(lanEnabled = false) }
        restartServer(bindLan = false)
        AgentHostState.update {
            it.copy(lanEnabled = false, apiHost = "127.0.0.1", pairingCode = null, pairingExpiresAt = null)
        }
    }

    suspend fun createBackup(): BackupRecord = backups.create().also { backup ->
        stateStore.update { it.copy(lastBackupAt = backup.createdAt) }
    }

    suspend fun restoreLatestBackup() {
        val backup = backups.list().firstOrNull() ?: error("No local backup is available")
        backups.restore(backup)
    }

    fun recordRestoreResult(backup: BackupRecord, success: Boolean) {
        backups.recordRestoreResult(backup, success)
        if (success) stateStore.update { it.copy(lastBackupAt = backup.createdAt) }
    }

    suspend fun close() {
        reminderJob?.cancel()
        pairingExpiryJob?.cancel()
        server?.stop(1_000, 3_000)
        tasks.close()
        runCatching { voice.muteMicrophone() }
        speech.close()
        tts.close()
        core.close()
        driver.close()
        scope.cancel("Android Agent runtime stopped")
        AgentHostState.update { HostUiState() }
    }

    private fun startServer(bindLan: Boolean = lanEnabled) {
        val services = HostApiServices(
            core, conversationRuntime, events, memories, tasks, reminders,
            projectManager = projects, skills = skills,
            pairingManager = pairing, backupProvider = backups,
            providerConfigurations = providerConfigurations,
        )
        server = embeddedServer(
            factory = CIO,
            host = if (bindLan) "0.0.0.0" else "127.0.0.1",
            port = API_PORT,
        ) { configureHostApi(services) }.start(wait = false)
    }

    private suspend fun restartServer(bindLan: Boolean) {
        server?.stop(500, 2_000)
        server = null
        startServer(bindLan)
    }

    private fun connectVoicePipeline() {
        scope.launch {
            voice.state.collectLatest { state ->
                AgentHostState.update { it.copy(voiceState = state, statusText = statusFor(state)) }
            }
        }
        scope.launch {
            speech.detections.collectLatest { wakeWord ->
                if (voice.onWakeWord(wakeWord)) voice.acknowledgeWake()
            }
        }
        scope.launch {
            speech.finalTranscripts.collectLatest(::processVoiceTranscript)
        }
        scope.launch {
            tts.isSpeaking.drop(1).distinctUntilChanged().collectLatest { speaking ->
                if (!speaking && voice.state.value == VoiceState.SPEAKING) {
                    runCatching { voice.onSpeechFinished() }
                }
            }
        }
    }

    private suspend fun processVoiceTranscript(transcript: String) {
        runCatching {
            voice.onFinalTranscript(transcript)
            if (transcript.trim().equals("Agente standby", ignoreCase = true)) {
                conversations.appendUserMessage(defaultConversationId, transcript, MessageSource.VOICE, "android-host")
                conversations.appendAgentMessage(defaultConversationId, "Entrando em standby.", MessageSource.VOICE, "android-host")
                voice.standby()
                return
            }
            voice.beginProcessing()
            val result = conversationRuntime.process(
                defaultConversationId, transcript, MessageSource.VOICE, "android-host",
            )
            voice.speak(result.replyMessage.content)
        }.onFailure { error ->
            voice.fail(error::class.simpleName ?: "ConversationRuntimeError")
            AgentHostState.update { it.copy(error = error.message, statusText = "Erro no Agent Host") }
        }
    }

    private fun canUseMicrophone(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun lanAddress(): String = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull() ?: "0.0.0.0"

    private fun statusFor(state: VoiceState): String = when (state) {
        VoiceState.STANDBY -> "Agente ativo — aguardando ‘Agente’"
        VoiceState.LISTENING -> "Agente ouvindo"
        VoiceState.THINKING -> "Agente pensando"
        VoiceState.PROCESSING -> "Agente processando"
        VoiceState.SPEAKING -> "Agente falando"
        VoiceState.MIC_MUTED -> "Agente ativo — microfone desligado"
        VoiceState.ERROR -> "Agente ativo — erro de voz"
    }

    private companion object {
        const val API_PORT = 8787
        const val KEY_SERVER_SECRET = "pairing_server_secret"
    }
}
