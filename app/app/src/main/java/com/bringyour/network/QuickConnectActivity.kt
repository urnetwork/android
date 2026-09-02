package com.bringyour.network

import android.app.Activity
import android.os.Bundle

/**
 * A no-UI trampoline behind the launcher shortcuts ("Connect", "Disconnect"):
 * applies the request through [QuickConnect] and finishes at once. Only when
 * the app is needed — logged out, or the first-ever connect that has to show
 * the system VPN consent dialog — does it open the app instead. Declared with
 * Theme.NoDisplay, so it must finish inside onCreate.
 */
class QuickConnectActivity : Activity() {

    companion object {
        const val ACTION_CONNECT = "com.bringyour.network.action.QUICK_CONNECT"
        const val ACTION_DISCONNECT = "com.bringyour.network.action.QUICK_DISCONNECT"
        const val ACTION_TOGGLE = "com.bringyour.network.action.QUICK_TOGGLE"

        /** The widgets pass the action as an extra (Glance starts activities by component, not intent action). */
        const val EXTRA_ACTION = "quick_action"
        val ACTION_PARAMETER = androidx.glance.action.ActionParameters.Key<String>(EXTRA_ACTION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext as MainApplication
        app.ensureApplicationStateInitialized()
        val action = intent?.action ?: intent?.getStringExtra(EXTRA_ACTION)
        val result = when (action) {
            ACTION_CONNECT -> QuickConnect.setConnected(app, connect = true, source = "shortcut")
            ACTION_DISCONNECT -> QuickConnect.setConnected(app, connect = false, source = "shortcut")
            else -> QuickConnect.toggle(app, source = "shortcut")
        }
        if (result != QuickConnect.Result.APPLIED) {
            QuickConnect.launchAppIntent(this)?.let { startActivity(it) }
        }
        finish()
    }
}
