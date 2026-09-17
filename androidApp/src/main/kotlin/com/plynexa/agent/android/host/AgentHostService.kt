package com.plynexa.agent.android.host

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.plynexa.agent.android.MainActivity
import com.plynexa.agent.android.backup.AndroidBackupFiles
import com.plynexa.agent.android.backup.AndroidLocalBackupProvider
import com.plynexa.agent.android.security.AndroidSecretStore
import com.plynexa.agent.core.model.VoiceState
import com.plynexa.agent.core.model.BackupRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class AgentHostService : Service() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Default)
    private var runtime: AndroidAgentRuntime? = null
    private var stateJob: Job? = null
    private lateinit var secretStore: AndroidSecretStore

    override fun onCreate() {
        super.onCreate()
        secretStore = AndroidSecretStore(this)
        createNotificationChannel()
        startForegroundFor(VoiceState.MIC_MUTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_START -> ensureRuntime()
            ACTION_ENABLE_VOICE -> scope.launch {
                ensureRuntime()
                runCatching { runtime?.enableVoice() }
                    .onFailure(::reportError)
                startForegroundFor(AgentHostState.state.value.voiceState)
            }
            ACTION_STANDBY -> scope.launch {
                ensureRuntime()
                runCatching { runtime?.standby() }.onFailure(::reportError)
            }
            ACTION_MUTE -> scope.launch {
                runtime?.mute()
                startForegroundFor(VoiceState.MIC_MUTED)
            }
            ACTION_ENABLE_PAIRING -> scope.launch {
                ensureRuntime()
                runCatching { runtime?.enablePairing() }.onFailure(::reportError)
            }
            ACTION_DISABLE_LAN -> scope.launch {
                runtime?.disableLan()
            }
            ACTION_CREATE_BACKUP -> scope.launch {
                ensureRuntime()
                runCatching { runtime?.createBackup() }.onFailure(::reportError)
            }
            ACTION_RESTORE_LATEST -> scope.launch {
                ensureRuntime()
                runCatching { runtime?.restoreLatestBackup() }.onFailure(::reportError)
            }
            ACTION_STOP -> stopAgent()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stateJob?.cancel()
        runBlocking {
            withTimeoutOrNull(4_000) { runtime?.close() }
        }
        runtime = null
        scope.cancel("AgentHostService destroyed")
        super.onDestroy()
    }

    private fun ensureRuntime() {
        if (runtime != null) return
        val lanEnabled = secretStore.get(KEY_LAN_ENABLED)?.toBooleanStrictOrNull() ?: false
        runtime = createRuntime(lanEnabled)
        stateJob = scope.launch {
            AgentHostState.state.collectLatest { state ->
                if (state.running) notificationManager().notify(NOTIFICATION_ID, notification(state))
            }
        }
        scope.launch {
            runCatching { runtime?.start() }
                .onFailure(::reportError)
        }
    }

    private fun createRuntime(lanEnabled: Boolean) = AndroidAgentRuntime(this, lanEnabled) { backup ->
        scope.launch {
            delay(750) // Let an authenticated API restore response leave the socket first.
            performRestore(backup)
        }
    }

    private suspend fun performRestore(backup: BackupRecord) {
        val current = runtime
        stateJob?.cancel()
        current?.close()
        runtime = null
        val restored = runCatching {
            AndroidBackupFiles.restore(getDatabasePath(AndroidLocalBackupProvider.DATABASE_NAME), backup)
        }.isSuccess
        ensureRuntime()
        runtime?.recordRestoreResult(backup, restored)
        if (!restored) reportError(IllegalStateException("Backup restore failed; original database was recovered"))
    }

    private fun stopAgent() {
        scope.launch {
            runtime?.close()
            runtime = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun reportError(error: Throwable) {
        AgentHostState.update {
            it.copy(running = runtime != null, statusText = "Erro no Agent Host", error = error.message)
        }
        notificationManager().notify(NOTIFICATION_ID, notification(AgentHostState.state.value))
    }

    private fun startForegroundFor(state: VoiceState) {
        val types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && state != VoiceState.MIC_MUTED) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(AgentHostState.state.value), types)
    }

    private fun notification(state: HostUiState): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val mute = serviceAction(ACTION_MUTE, 1)
        val standby = serviceAction(ACTION_STANDBY, 2)
        val stop = serviceAction(ACTION_STOP, 3)
        val pair = serviceAction(ACTION_ENABLE_PAIRING, 4)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Agent V0 ChatGPT")
            .setContentText(state.statusText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "${state.statusText} • API ${state.apiHost}:${state.apiPort}",
            ))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Standby", standby)
            .addAction(0, "Silenciar", mute)
            .addAction(0, "Parear", pair)
            .addAction(0, "Parar", stop)
            .build()
    }

    private fun serviceAction(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this, requestCode, Intent(this, AgentHostService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createNotificationChannel() {
        notificationManager().createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "Agent Core", NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Mantém o Agent Core local em execução"
            setShowBadge(false)
        })
    }

    private fun notificationManager() = getSystemService(NotificationManager::class.java)

    companion object {
        const val ACTION_START = "com.plynexa.agent.action.START"
        const val ACTION_ENABLE_VOICE = "com.plynexa.agent.action.ENABLE_VOICE"
        const val ACTION_STANDBY = "com.plynexa.agent.action.STANDBY"
        const val ACTION_MUTE = "com.plynexa.agent.action.MUTE"
        const val ACTION_ENABLE_PAIRING = "com.plynexa.agent.action.ENABLE_PAIRING"
        const val ACTION_DISABLE_LAN = "com.plynexa.agent.action.DISABLE_LAN"
        const val ACTION_CREATE_BACKUP = "com.plynexa.agent.action.CREATE_BACKUP"
        const val ACTION_RESTORE_LATEST = "com.plynexa.agent.action.RESTORE_LATEST"
        const val ACTION_STOP = "com.plynexa.agent.action.STOP"
        const val KEY_LAN_ENABLED = "lan_enabled"
        private const val CHANNEL_ID = "agent-core"
        private const val NOTIFICATION_ID = 1001
    }
}
