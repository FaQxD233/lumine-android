package com.moi.lumine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

object LumineRecoveryScheduler {
    const val ACTION_RECOVER = "com.moi.lumine.action.RECOVER_VPN"
    private const val REQUEST_RECOVER = 2301
    private const val INTERVAL_MS = 60_000L

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS
        val intent = recoveryIntent(appContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, intent)
        } else {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, intent)
        }
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(recoveryIntent(appContext))
    }

    private fun recoveryIntent(context: Context): PendingIntent {
        val intent = Intent(context, LumineRecoveryReceiver::class.java).apply {
            action = ACTION_RECOVER
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_RECOVER,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
