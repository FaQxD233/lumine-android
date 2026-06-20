package com.moi.lumine

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.moi.lumine.repository.ConfigRepository
import mobile.Mobile

class LumineTileService : TileService() {
    private val repository by lazy { ConfigRepository(applicationContext) }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        if (Mobile.isRunning() || repository.shouldVpnBeRunning()) {
            VpnServiceController.stop(this)
            updateTile(false)
            return
        }

        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            val openApp = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    android.app.PendingIntent.getActivity(
                        this,
                        0,
                        openApp,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(openApp)
            }
            return
        }

        VpnRuntimeState.setStatus("starting", "正在从快捷开关启动")
        VpnServiceController.start(this, repository.getSelectedConfigName())
        updateTile(true)
    }

    private fun updateTile(active: Boolean = Mobile.isRunning() || repository.shouldVpnBeRunning()) {
        qsTile?.apply {
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "Lumine"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = if (active) "代理运行中" else "点按启动"
            }
            updateTile()
        }
    }
}
