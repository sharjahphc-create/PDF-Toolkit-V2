package com.example.system

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.MainActivity

class PdfToolkitQuickSettingsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Scan PDF"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = "PDF Toolkit"
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_SHORTCUT
            putExtra(EXTRA_SHORTCUT_TOOL, "doc_scanner")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        const val ACTION_SHORTCUT = "com.example.ACTION_SHORTCUT"
        const val EXTRA_SHORTCUT_TOOL = "shortcut_tool"
    }
}
