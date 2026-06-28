package com.moi.lumine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.moi.lumine.model.AppProxyMode
import com.moi.lumine.repository.ConfigRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mobile.Mobile // This will be available after gomobile bind

class LumineVpnService : VpnService() {

    @Volatile private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile private var coreTunFd: Int? = null
    private var configName: String = "config" // Default config name
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository by lazy { ConfigRepository(applicationContext) }
    private val transitionLock = Any()
    private var logPumpJob: Job? = null
    private var statsPumpJob: Job? = null
    private var watchdogJob: Job? = null
    @Volatile private var isStarting = false
    @Volatile private var isStopping = false
    @Volatile private var coreStarted = false
    @Volatile private var coreOwnsTunFd = false
    @Volatile private var pendingStopRequested = false
    @Volatile private var coreStopIssued = false
    @Volatile private var lastWatchdogRecoveryAt = 0L
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            repository.setVpnShouldRun(false)
            LumineRecoveryScheduler.cancel(applicationContext)
            VpnRuntimeState.setStatus("stopping", "正在停止代理")
            stopVpn()
            return START_NOT_STICKY
        }

        val requestedConfig = intent?.getStringExtra(EXTRA_CONFIG_NAME)?.takeIf { it.isNotBlank() }
        val shouldRecover = requestedConfig == null && repository.shouldVpnBeRunning()
        val targetConfig = requestedConfig ?: if (shouldRecover) repository.getLastRunningConfigName() else null

        if (targetConfig == null) {
            Log.i("LumineVpn", "Ignoring sticky restart without persisted running state")
            if (!Mobile.isRunning() && VpnRuntimeState.status.value.phase != "error") {
                VpnRuntimeState.setActive(false)
                VpnRuntimeState.setStatus("idle", "点此启动服务")
            }
            return START_STICKY
        }

        configName = targetConfig
        repository.setSelectedConfigName(configName)
        repository.setVpnShouldRun(true, configName)
        if (shouldRecover) {
            VpnRuntimeState.setStatus("starting", "正在恢复代理")
            Log.i("LumineVpn", "Recovering VPN after service restart with config: $configName")
        }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        synchronized(transitionLock) {
            if (isStarting || isStopping || coreStarted || vpnInterface != null || coreTunFd != null) {
                Log.i("LumineVpn", "Ignoring duplicate start request")
                return
            }
            isStarting = true
            pendingStopRequested = false
            coreStopIssued = false
        }

        try {
            VpnRuntimeState.clearLogs()
            VpnRuntimeState.setActive(false)
            VpnRuntimeState.setStatus("starting", "正在建立 VPN")
            startForeground(NOTIFICATION_ID, buildNotification("正在启动代理"))

            serviceScope.launch {
                val startedAt = SystemClock.elapsedRealtime()
                try {
                    ensureConfigFile(configName)
                    if (consumePendingStopRequest()) {
                        VpnRuntimeState.setActive(false)
                        VpnRuntimeState.setStatus("idle", "点此启动服务")
                        return@launch
                    }

                    val builder = Builder()
                        .setSession("Lumine")
                        .setMtu(1500)
                        .addAddress("172.19.0.1", 30)
                        .addAddress("fd66:6c75:6d69::1", 64)
                        .addDnsServer("172.19.0.2")
                        .addRoute("0.0.0.0", 0)
                        .addRoute("::", 0)

                    applyAppProxyRules(builder)
                    vpnInterface = builder.establish()
                    val tun = vpnInterface
                    if (tun == null) {
                        VpnRuntimeState.setActive(false)
                        VpnRuntimeState.setStatus("error", "VPN 建立失败")
                        repository.setVpnShouldRun(false)
                        LumineRecoveryScheduler.cancel(applicationContext)
                        stopServiceShell()
                        return@launch
                    }

                    val fd = tun.detachFd()
                    vpnInterface = null
                    coreTunFd = fd
                    Log.i("LumineVpn", "Established TUN FD: $fd")
                    VpnRuntimeState.setStatus("starting", "VPN 已建立，正在启动核心")

                    Mobile.setWorkingDir(filesDir.absolutePath)
                    Mobile.setBlockQuic(repository.isBlockQuicEnabled())
                    synchronized(transitionLock) {
                        if (pendingStopRequested) {
                            closePendingTunFd()
                            VpnRuntimeState.setActive(false)
                            VpnRuntimeState.setStatus("idle", "点此启动服务")
                            return@launch
                        }
                        coreOwnsTunFd = true
                    }

                    val error = Mobile.startLumine(fd.toLong(), configName)
                    if (error.isNotEmpty()) {
                        coreOwnsTunFd = false
                        closePendingTunFd()
                        Log.e("LumineVpn", "Go core failed: $error")
                        updateNotification("启动失败: $error")
                        VpnRuntimeState.setActive(false)
                        VpnRuntimeState.setStatus("error", "启动失败: $error")
                        repository.setVpnShouldRun(false)
                        LumineRecoveryScheduler.cancel(applicationContext)
                        stopVpn()
                        return@launch
                    }

                    val elapsed = SystemClock.elapsedRealtime() - startedAt
                    coreStarted = true
                    acquireRuntimeWakeLock()
                    LumineRecoveryScheduler.schedule(applicationContext)
                    Log.i("LumineVpn", "Lumine started successfully in ${elapsed}ms")
                    VpnRuntimeState.setActive(true)
                    VpnRuntimeState.setStatus("running", "代理运行中 (${elapsed}ms)")
                    updateNotification("代理运行中")
                    startLogPump()
                    startStatsPump()
                    startWatchdog()
                    if (consumePendingStopRequest()) {
                        stopVpn()
                    }
                } catch (e: Exception) {
                    Log.e("LumineVpn", "Failed to initialize Go core", e)
                    val errorMessage = e.message
                        ?.takeIf { it.isNotBlank() }
                        ?.let { "核心初始化失败: ${it.take(160)}" }
                        ?: "核心初始化失败"
                    updateNotification(errorMessage)
                    VpnRuntimeState.setActive(false)
                    VpnRuntimeState.setStatus("error", errorMessage)
                    repository.setVpnShouldRun(false)
                    LumineRecoveryScheduler.cancel(applicationContext)
                    stopVpn()
                } finally {
                    isStarting = false
                }
            }
        } catch (e: Exception) {
            Log.e("LumineVpn", "Failed to start VPN", e)
            isStarting = false
            VpnRuntimeState.setActive(false)
            VpnRuntimeState.setStatus("error", "启动 VPN 失败")
            repository.setVpnShouldRun(false)
            LumineRecoveryScheduler.cancel(applicationContext)
            serviceScope.launch {
                stopServiceShell()
            }
        }
    }

    private fun stopVpn() {
        var keepPendingStopForStarter = false
        val stopShellImmediately = synchronized(transitionLock) {
            if (isStopping) {
                Log.i("LumineVpn", "Ignoring duplicate stop request")
                return@synchronized false
            }
            if (!isStarting && !coreStarted && vpnInterface == null && coreTunFd == null) {
                true
            } else {
                pendingStopRequested = true
                keepPendingStopForStarter = isStarting
                isStopping = true
                false
            }
        }
        if (stopShellImmediately) {
            serviceScope.launch {
                stopWatchdog()
                stopServiceShell()
            }
            return
        }
        serviceScope.launch {
            try {
                stopWatchdog()
                stopLogPump()
                stopStatsPump()
                performCoreShutdownIfNeeded()
                releaseRuntimeWakeLock()
                LumineRecoveryScheduler.cancel(applicationContext)
                if (!keepPendingStopForStarter) {
                    pendingStopRequested = false
                }
                VpnRuntimeState.setActive(false)

                val tun = vpnInterface
                vpnInterface = null
                runCatching { tun?.close() }

                withContext(Dispatchers.Main) {
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                        } else {
                            @Suppress("DEPRECATION")
                            stopForeground(true)
                        }
                    }
                    stopSelf()
                    if (VpnRuntimeState.status.value.phase != "error") {
                        VpnRuntimeState.setStatus("idle", "点此启动服务")
                    }
                }
            } finally {
                isStarting = false
                isStopping = false
            }
        }
    }

    override fun onDestroy() {
        stopWatchdog()
        stopLogPump()
        stopStatsPump()
        performCoreShutdownIfNeeded()
        releaseRuntimeWakeLock()
        if (!repository.shouldVpnBeRunning()) {
            LumineRecoveryScheduler.cancel(applicationContext)
        }
        serviceScope.cancel()
        pendingStopRequested = false
        VpnRuntimeState.setActive(false)
        runCatching { vpnInterface?.close() }
        vpnInterface = null
        isStarting = false
        isStopping = false
        if (VpnRuntimeState.status.value.phase != "error") {
            VpnRuntimeState.setStatus("idle", "点此启动服务")
        }
        super.onDestroy()
    }

    private fun ensureConfigFile(name: String) {
        val target = File(filesDir, "$name.json")
        if (target.exists()) {
            return
        }

        if (name == "config") {
            assets.open("config_default.json").use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.i("LumineVpn", "Created default config at ${target.absolutePath}")
            return
        }

        throw IllegalStateException("Config file not found: $name.json")
    }

    private fun applyAppProxyRules(builder: Builder) {
        val mode = repository.getAppProxyMode()
        val selectedPackages = repository.getSelectedAppPackages()
            .filter { it.isNotBlank() && it != packageName }
            .toSet()

        when (mode) {
            AppProxyMode.All -> {
                addDisallowedAppSafely(builder, packageName)
            }
            AppProxyMode.BypassSelected -> {
                (selectedPackages + packageName).forEach { addDisallowedAppSafely(builder, it) }
            }
            AppProxyMode.OnlySelected -> {
                if (selectedPackages.isEmpty()) {
                    throw IllegalStateException("仅选中模式至少需要选择一个应用")
                }
                selectedPackages.forEach { addAllowedAppSafely(builder, it) }
            }
        }
    }

    private fun addAllowedAppSafely(builder: Builder, packageName: String) {
        runCatching { builder.addAllowedApplication(packageName) }
            .onFailure { Log.w("LumineVpn", "Skip allowed app $packageName", it) }
    }

    private fun addDisallowedAppSafely(builder: Builder, packageName: String) {
        runCatching { builder.addDisallowedApplication(packageName) }
            .onFailure { Log.w("LumineVpn", "Skip disallowed app $packageName", it) }
    }

    private fun startLogPump() {
        if (logPumpJob?.isActive == true) {
            return
        }
        logPumpJob = serviceScope.launch {
            while (isActive) {
                publishPendingLogs()
                delay(300)
            }
        }
    }

    private fun stopLogPump() {
        logPumpJob?.cancel()
        logPumpJob = null
    }

    private fun startStatsPump() {
        if (statsPumpJob?.isActive == true) {
            return
        }
        statsPumpJob = serviceScope.launch {
            while (isActive) {
                publishStats()
                delay(1_000)
            }
        }
    }

    private fun stopStatsPump() {
        statsPumpJob?.cancel()
        statsPumpJob = null
    }

    private fun startWatchdog() {
        if (watchdogJob?.isActive == true) {
            return
        }
        watchdogJob = serviceScope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)

                if (!repository.shouldVpnBeRunning() || isStarting || isStopping || pendingStopRequested) {
                    continue
                }

                val coreRunning = runCatching { Mobile.isRunning() }.getOrDefault(false)
                if (coreRunning) {
                    continue
                }

                val now = SystemClock.elapsedRealtime()
                if (now - lastWatchdogRecoveryAt < WATCHDOG_RECOVERY_COOLDOWN_MS) {
                    continue
                }
                lastWatchdogRecoveryAt = now

                Log.w("LumineVpn", "Watchdog detected core/service desync, restarting VPN")
                VpnRuntimeState.setActive(false)
                VpnRuntimeState.setStatus("starting", "检测到核心退出，正在恢复")
                recoverVpnFromWatchdog()
            }
        }
    }

    private fun stopWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    private fun publishPendingLogs() {
        val newLogs = Mobile.getLogs()
        if (newLogs.isBlank()) {
            return
        }
        val lines = newLogs
            .lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .toList()
        VpnRuntimeState.appendLogs(lines)
    }

    private fun publishStats() {
        VpnRuntimeState.updateStatsFromJson(Mobile.getStats())
    }

    private fun consumePendingStopRequest(): Boolean {
        synchronized(transitionLock) {
            if (!pendingStopRequested) {
                return false
            }
            pendingStopRequested = false
            return true
        }
    }

    private fun closePendingTunFd() {
        val fd = coreTunFd ?: return
        coreTunFd = null
        runCatching { ParcelFileDescriptor.adoptFd(fd).close() }
    }

    private suspend fun recoverVpnFromWatchdog() {
        val claimed = synchronized(transitionLock) {
            if (isStarting || isStopping || pendingStopRequested) {
                false
            } else {
                isStopping = true
                true
            }
        }
        if (!claimed) {
            return
        }

        try {
            stopLogPump()
            stopStatsPump()
            performCoreShutdownIfNeeded()
            releaseRuntimeWakeLock()

            val tun = vpnInterface
            vpnInterface = null
            runCatching { tun?.close() }

            closePendingTunFd()
            coreStarted = false
            coreOwnsTunFd = false
            pendingStopRequested = false
        } finally {
            isStarting = false
            isStopping = false
        }

        if (!repository.shouldVpnBeRunning()) {
            stopServiceShell()
            return
        }

        startVpn()
    }

    private fun performCoreShutdownIfNeeded() {
        val shouldStopCore = synchronized(transitionLock) {
            if (coreStopIssued) {
                return@synchronized false
            }
            coreStopIssued = true
            coreOwnsTunFd || coreStarted
        }

        if (shouldStopCore) {
            runCatching { Mobile.stopLumine() }
        } else {
            closePendingTunFd()
        }

        publishPendingLogs()
        coreStarted = false
        coreOwnsTunFd = false
        coreTunFd = null
    }

    private suspend fun stopServiceShell() {
        VpnRuntimeState.setActive(false)
        releaseRuntimeWakeLock()
        if (!repository.shouldVpnBeRunning()) {
            LumineRecoveryScheduler.cancel(applicationContext)
        }
        withContext(Dispatchers.Main) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            }
            stopSelf()
            if (VpnRuntimeState.status.value.phase != "error") {
                VpnRuntimeState.setStatus("idle", "点此启动服务")
            }
        }
    }

    private fun buildNotification(contentText: String): Notification {
        createNotificationChannel()

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LumineVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        builder
            .setContentTitle("Lumine")
            .setContentText(contentText)
            .setSubText(configName)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openPendingIntent)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(Notification.PRIORITY_HIGH)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Lumine VPN",
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.setShowBadge(false)
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        manager.createNotificationChannel(channel)
    }

    private fun acquireRuntimeWakeLock() {
        if (wakeLock?.isHeld == true) {
            return
        }

        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:lumine-vpn")
            .apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun releaseRuntimeWakeLock() {
        val lock = wakeLock
        wakeLock = null
        if (lock?.isHeld == true) {
            runCatching { lock.release() }
        }
    }

    companion object {
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val EXTRA_CONFIG_NAME = "CONFIG_NAME"
        private const val NOTIFICATION_CHANNEL_ID = "lumine_vpn"
        private const val NOTIFICATION_ID = 1001
        private const val WATCHDOG_INTERVAL_MS = 5_000L
        private const val WATCHDOG_RECOVERY_COOLDOWN_MS = 15_000L
    }
}
