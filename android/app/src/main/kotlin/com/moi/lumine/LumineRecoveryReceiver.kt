package com.moi.lumine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.moi.lumine.repository.ConfigRepository
import mobile.Mobile

class LumineRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != LumineRecoveryScheduler.ACTION_RECOVER) {
            return
        }

        val repository = ConfigRepository(context.applicationContext)
        if (!repository.shouldVpnBeRunning()) {
            LumineRecoveryScheduler.cancel(context)
            return
        }

        if (Mobile.isRunning()) {
            LumineRecoveryScheduler.schedule(context)
            return
        }

        if (VpnService.prepare(context) != null) {
            Log.w("LumineRecovery", "VPN permission is missing; recovery skipped")
            LumineRecoveryScheduler.schedule(context)
            return
        }

        runCatching {
            VpnServiceController.start(context, repository.getLastRunningConfigName())
        }.onFailure {
            Log.w("LumineRecovery", "Failed to recover VPN", it)
            LumineRecoveryScheduler.schedule(context)
        }
    }
}
