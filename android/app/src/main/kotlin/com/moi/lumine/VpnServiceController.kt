package com.moi.lumine

import android.content.Context
import android.content.Intent
import android.os.Build

object VpnServiceController {
    fun start(context: Context, configName: String? = null) {
        val intent = Intent(context, LumineVpnService::class.java).apply {
            action = LumineVpnService.ACTION_START
            if (!configName.isNullOrBlank()) {
                putExtra(LumineVpnService.EXTRA_CONFIG_NAME, configName)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stop(context: Context) {
        val intent = Intent(context, LumineVpnService::class.java).apply {
            action = LumineVpnService.ACTION_STOP
        }
        context.startService(intent)
    }
}
