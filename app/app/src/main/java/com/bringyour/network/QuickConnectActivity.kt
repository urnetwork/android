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

        /** Open the app on a screen (a widget tap): extra [EXTRA_ROUTE] names it. */
        const val ACTION_OPEN = "com.bringyour.network.action.OPEN"

        /** The widgets pass the action as an extra (Glance starts activities by component, not intent action). */
        const val EXTRA_ACTION = "quick_action"
        const val EXTRA_ROUTE = "route"
        val ACTION_PARAMETER = androidx.glance.action.ActionParameters.Key<String>(EXTRA_ACTION)
        val ROUTE_PARAMETER = androidx.glance.action.ActionParameters.Key<String>(EXTRA_ROUTE)

        const val ROUTE_CONNECT = "connect"
        const val ROUTE_PROVIDER_LOCATIONS = "provider_locations"
        const val ROUTE_CONTRACT_STATS = "contract_stats"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext as MainApplication
        app.ensureApplicationStateInitialized()
        val action = intent?.action ?: intent?.getStringExtra(EXTRA_ACTION)
        if (action == ACTION_OPEN) {
            // a widget tap: hand the screen to the app and open it through its
            // normal entry (LoginActivity decides between sign-in and MainActivity)
            app.widgetRoute.value = intent?.getStringExtra(EXTRA_ROUTE) ?: ROUTE_CONNECT
            QuickConnect.launchAppIntent(this)?.let { startActivity(it) }
            finish()
            return
        }
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
