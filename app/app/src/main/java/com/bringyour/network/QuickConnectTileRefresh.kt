package com.bringyour.network

import com.bringyour.network.ui.connect.QuickConnectStatus

/**
 * Asks the system to show the Quick Settings tile again whenever what it
 * says may have changed (QuickConnectTileService.follow).
 *
 * The tile is active (META_DATA_ACTIVE_TILE): the system binds it only when
 * the app asks (requestListeningState) or on a tap, never when the shade
 * opens, and it stops listening right after the update that answers a
 * request (SystemUI's TileServiceManager.setLastUpdate). So the listeners
 * the tile adds while listening hear almost nothing, and the tile kept what
 * it last said: "Connecting to providers" after the window filled and the
 * tunnel came up, "Connected" after the tunnel went down, and the signed-in
 * state after a sign out. Mullvad's tile showed the same symptom after a
 * remote sign out (mullvadvpn-app#10843), from another cause.
 *
 * [changed] reports the window or the tunnel, which can move the status;
 * [deviceChanged] a new device: a sign in, a sign out, another device
 * signing this one out, or the first device of the process. A burst folds
 * into one check on the main thread ([post]). A device change always asks.
 * Otherwise the check asks only when the status differs from what the tile
 * last said, and only for a tile that is placed: one that has spoken in
 * this process, or that the app saw added ([placed]). A tile nobody placed
 * is then not asked once per report of the window.
 */
internal class QuickConnectTileRefresh(
    post: (Runnable) -> Unit,
    /** What the tile would say now (QuickConnect.status). */
    private val status: () -> QuickConnectStatus,
    /** What the tile last said in this process, null before it said anything. */
    private val rendered: () -> QuickConnectStatus?,
    /** Whether the user added the tile (QuickConnectTileService.isAdded). */
    private val placed: () -> Boolean,
    /** Asks the system to bind the tile (QuickConnectTileService.requestUpdate). */
    private val requestUpdate: () -> Unit,
) {
    // main thread only: set by deviceChanged, read by the check it posts
    private var always = false

    private val check = CoalescedPost(post = post) {
        val said = rendered()
        val ask = always || (status() != said && (said != null || placed()))
        always = false
        if (ask) {
            requestUpdate()
        }
    }

    /** The window or the tunnel changed. Any thread. */
    fun changed() = check.request()

    /** The device changed. Main thread. */
    fun deviceChanged() {
        always = true
        check.request()
    }
}
