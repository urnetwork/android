package com.bringyour.network.ui.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The parts of the sdk's ClientSessionViewController that Account -> Sessions
 * drives (REVOKE-UI-FINAL.md §2). The controller fetches, polls every 30
 * seconds while visible, and owns operation ids, retries and status recovery;
 * it reads the whole list, groups and counts it, and hands over only the rows
 * on screen. The screen only forwards its lifecycle and actions.
 */
internal interface SessionsController {
    // the controller's current snapshot, copied off it
    fun snapshot(): SessionsSnapshot

    fun start()

    fun setVisible(visible: Boolean)

    // the app's lifecycle: the foreground loads the list again, the background
    // changes nothing (a pending sign out goes on to its outcome)
    fun setForeground(foreground: Boolean)

    fun refresh()

    fun revokeSession(sessionId: String)

    fun revokeOtherSessions()

    // opens a client group to its first rows, or closes it; presentation only
    fun setGroupExpanded(kind: String, expanded: Boolean)

    // another page of the device sign-ins (Sdk.ClientSessionGroupSignIns) or
    // of an open group; presentation only
    fun showMoreSessions(kind: String)

    // removes the change listener, then closes the controller
    fun close()
}

/** The sign out a confirmation dialog asks about (§4). */
sealed interface SessionsConfirmation {
    // the row as it was when the user chose it; the current session's warns
    // that this app signs out
    data class SignOut(val row: SessionRowUi) : SessionsConfirmation

    data object SignOutOthers : SessionsConfirmation
}

/**
 * Account -> Sessions: owns the screen's session controller and its state.
 * Main-thread confined; SessionsViewModel binds it to the device, the process
 * lifecycle and the screen.
 *
 * One controller per device's account, open from the device until another
 * device replaces it (an account switch or a sign out, which also clears the
 * list) or the screen goes: the background never closes it, since a pending
 * sign out's operation id lives in it until the controller confirms the
 * outcome (§6: foreground and background go to SetForeground, and only the
 * dispose closes). The screen's visibility starts it and sets SetVisible,
 * which runs the controller's polling.
 *
 * The controller's change listener fires on an sdk thread. [post] hands each
 * change to the main thread, which then reads the controller's latest snapshot:
 * deliveries that race across sdk threads cannot apply an older snapshot over
 * a newer one, and a change of a controller that has since closed (the screen
 * gone, another device) is dropped.
 */
internal class SessionsScreenModel<D : Any>(
    private val openController: (device: D, onChange: () -> Unit) -> SessionsController,
    private val post: (() -> Unit) -> Unit,
    private val clock: () -> Long,
) {
    var ui by mutableStateOf(SessionsUi.initial(clock()))
        private set

    var confirmation by mutableStateOf<SessionsConfirmation?>(null)
        private set

    private var device: D? = null

    // the open controller, and a count that changes whenever one opens or closes
    private var controller: SessionsController? = null
    private var controllerGeneration = 0L

    private var visible = false
    private var foreground = false
    // the screen is gone: nothing opens again
    private var closed = false

    /** The device whose account api the controller opens on; another device closes the former one's. */
    fun setDevice(nextDevice: D?) {
        if (closed || device === nextDevice) {
            return
        }
        device = nextDevice
        controller?.let { close(it) }
        val opened = nextDevice?.let { open(it) } ?: return
        if (visible) {
            appear(opened)
        }
    }

    /** Whether the app is in the foreground, forwarded to the controller, which stays open. */
    fun setForeground(nextForeground: Boolean) {
        if (closed || foreground == nextForeground) {
            return
        }
        foreground = nextForeground
        controller?.setForeground(nextForeground)
    }

    /** Whether the screen shows. Hidden stops the polling and keeps the controller and its list. */
    fun setVisible(nextVisible: Boolean) {
        if (closed || visible == nextVisible) {
            return
        }
        visible = nextVisible
        val opened = controller ?: return
        if (nextVisible) {
            appear(opened)
        } else {
            // on disappear: SetVisible(false)
            opened.setVisible(false)
        }
    }

    /** Pull to refresh; the controller publishes its refreshing state. */
    fun refresh() {
        controller?.refresh()
    }

    /** A group header's tap or its expand and collapse actions. */
    fun setGroupExpanded(kind: String, expanded: Boolean) {
        val opened = controller ?: return
        opened.setGroupExpanded(kind, expanded)
        // the controller lays the rows out before it returns
        apply(opened.snapshot())
    }

    /** Show more, under the device sign-ins or an open group. */
    fun showMoreSessions(kind: String) {
        val opened = controller ?: return
        opened.showMoreSessions(kind)
        apply(opened.snapshot())
    }

    /**
     * The swipe's Sign out, or the row's screen reader action, of a device
     * sign-in or a group's row: asks first.
     */
    fun requestSignOut(sessionId: String) {
        if (controller == null) {
            return
        }
        val row = ui.row(sessionId) ?: return
        if (row.signingOut) {
            return
        }
        confirmation = SessionsConfirmation.SignOut(row)
    }

    /** Sign out all other sessions: asks first. */
    fun requestSignOutOthers() {
        if (controller == null) {
            return
        }
        val others = ui.signOutOthers ?: return
        if (others.signingOut) {
            return
        }
        confirmation = SessionsConfirmation.SignOutOthers
    }

    /** Cancel, the default: nothing is signed out. */
    fun dismissConfirmation() {
        confirmation = null
    }

    /**
     * The confirmation's Sign out. A sign out that is already running is not
     * sent again; the controller also reuses its operation for a repeat.
     */
    fun confirm() {
        val target = confirmation ?: return
        confirmation = null
        val opened = controller ?: return
        when (target) {
            is SessionsConfirmation.SignOut -> {
                if (ui.row(target.row.sessionId)?.signingOut == true) {
                    return
                }
                // the current session's success signs this app out through
                // the sdk's credential rejection and the app's logout flow
                opened.revokeSession(target.row.sessionId)
            }
            SessionsConfirmation.SignOutOthers -> {
                if (ui.signOutOthers?.signingOut == true) {
                    return
                }
                opened.revokeOtherSessions()
            }
        }
        // the controller marks the action loading before it returns
        apply(opened.snapshot())
    }

    /** The screen is gone: closes the controller, after which nothing updates. */
    fun close() {
        if (closed) {
            return
        }
        closed = true
        visible = false
        device = null
        controller?.let { close(it) }
    }

    // on appear: Start (once per controller in the sdk), then SetVisible(true)
    private fun appear(opened: SessionsController) {
        opened.start()
        opened.setVisible(true)
    }

    private fun open(device: D): SessionsController {
        val generation = ++controllerGeneration
        val opened = openController(device) {
            post {
                if (controllerGeneration == generation) {
                    controller?.let { apply(it.snapshot()) }
                }
            }
        }
        controller = opened
        apply(opened.snapshot())
        return opened
    }

    private fun close(opened: SessionsController) {
        if (controller === opened) {
            controller = null
            controllerGeneration++
            // a confirmation belongs to the closed controller's account
            confirmation = null
            apply(SessionsSnapshot.Initial)
        }
        opened.close()
    }

    private fun apply(snapshot: SessionsSnapshot) {
        ui = sessionsUi(snapshot, clock())
    }
}
