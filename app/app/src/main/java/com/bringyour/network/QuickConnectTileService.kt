package com.bringyour.network

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.bringyour.sdk.Sub

/**
 * Quick Settings tile that toggles the URnetwork connection on and off.
 *
 * The tile runs in the app process, so it reads and drives the SDK [com.bringyour.sdk.DeviceLocal]
 * directly (no IPC) through [QuickConnect], the same path as the launcher shortcuts, the widgets'
 * button and the notification action: tile state mirrors `device.connectEnabled`, and a tap opens
 * a short-lived ConnectViewController to connect/disconnect exactly as the connect screen does.
 * Flipping `connectEnabled` fires the app's own connect-change listener, which starts or stops the
 * VPN service. The first-ever connect needs the system VPN consent dialog, which only an activity
 * can present, so that case hands off to the app (which completes the start via `vpnRequestStart`).
 *
 * It is an active, toggleable tile: the app pushes state with [requestListeningState] whenever the
 * connection changes, and accessibility reads it as a switch. The icon is the solid connector
 * mark in both states, as on iOS; the system tints the active tile, which is what carries the
 * state (Android 16 QPR1 tiles snap to 1x1, icon only). It toggles from the lock screen without
 * unlocking, like the system's own connectivity tiles (product decision, 2026-09-01).
 */
class QuickConnectTileService : TileService() {

    companion object {
        private const val PREFS = "quick_connect_tile"
        private const val KEY_ADDED = "added"

        /** Whether the user has the tile in their Quick Settings (tracked from onTileAdded/Removed). */
        fun isAdded(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ADDED, false)

        fun setAdded(context: Context, added: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ADDED, added).apply()
        }

        /** Ask the system to bind the tile so it re-renders (an active tile is not bound otherwise). */
        fun requestUpdate(context: Context) {
            runCatching {
                requestListeningState(context, ComponentName(context, QuickConnectTileService::class.java))
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var connectSub: Sub? = null

    private val app: MainApplication
        get() = applicationContext as MainApplication

    override fun onTileAdded() {
        super.onTileAdded()
        setAdded(this, true)
        // an active tile shows the manifest defaults until it is bound once
        requestUpdate(this)
    }

    override fun onTileRemoved() {
        setAdded(this, false)
        super.onTileRemoved()
    }

    override fun onStartListening() {
        super.onStartListening()
        // keep the tile live while the shade is open
        connectSub?.close()
        connectSub = app.device?.addConnectChangeListener {
            mainHandler.post { render() }
        }
        render()
    }

    override fun onStopListening() {
        connectSub?.close()
        connectSub = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        when (QuickConnect.toggle(app, source = "tile")) {
            QuickConnect.Result.APPLIED -> render()
            QuickConnect.Result.NEEDS_CONSENT -> {
                render()
                openApp()
            }
            QuickConnect.Result.NEEDS_APP -> openApp()
        }
    }

    private fun render() {
        val tile = qsTile ?: return
        val configured = QuickConnect.isConfigured(app)
        val connected = configured && QuickConnect.isConnected(app)
        tile.state = if (connected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_quick_on)
        tile.label = getString(R.string.app_name)
        val subtitle = getString(
            when {
                !configured -> R.string.widget_not_signed_in
                connected -> R.string.tile_status_connected
                else -> R.string.tile_status_disconnected
            },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tile.stateDescription = subtitle
        }
        tile.updateTile()
    }

    private fun openApp() {
        val launch = QuickConnect.launchAppIntent(this) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // startActivityAndCollapse(Intent) throws on API 34+; the PendingIntent overload is required
            val pending = PendingIntent.getActivity(
                this,
                0,
                launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launch)
        }
    }
}
