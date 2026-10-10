package com.bringyour.network.ui.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bringyour.network.VisibleDeviceControllerOwner

/**
 * The parts of the sdk's ClientSessionViewController that Account -> Sessions
 * drives (REVOKE-UI-FINAL.md §2). The controller fetches, polls every 30
 * seconds while visible, and owns operation ids, retries and status recovery;
 * the screen only forwards its lifecycle and actions.
 */
internal interface SessionsController {
    // the controller's current snapshot, copied off it
    fun snapshot(): SessionsSnapshot

    fun start()

    fun setVisible(visible: Boolean)

    fun refresh()

    fun revokeSession(sessionId: String)

    fun revokeOtherSessions()

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
 * The lifecycle is ProviderStatusViewModel's (VisibleDeviceControllerOwner):
 * the controller is open while there is a device and the app is in the
 * foreground, so the background closes it and the foreground opens a new one,
 * whose start loads the list again (§6 "foreground goes to SetForeground": the
 * sdk's SetForeground(true) is exactly that refresh). While open, the screen's
 * visibility starts it and sets SetVisible, which runs the controller's
 * polling. A replaced device closes the former device's controller, which
 * clears the list; switching accounts never shows the older account's rows.
 *
 * The controller's change listener fires on an sdk thread. [post] hands each
 * change to the main thread, which then reads the controller's latest snapshot:
 * deliveries that race across sdk threads cannot apply an older snapshot over
 * a newer one, and a change of a controller that has since closed (the screen
 * gone, the background, another device) is dropped.
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

    // the open controller, and a count that changes whenever one opens or closes
    private var controller: SessionsController? = null
    private var controllerGeneration = 0L

    private val owner = VisibleDeviceControllerOwner<D, SessionsController>(
        open = { device -> open(device) },
        close = { _, opened -> close(opened) },
        // on appear: Start, then SetVisible(true); Start runs once per controller
        start = {
            it.start()
            it.setVisible(true)
        },
        // on disappear: SetVisible(false), which keeps the controller and its list
        stop = { it.setVisible(false) },
    )

    init {
        // the screen exists exactly while this model does
        owner.setEnabled(true)
    }

    /** The device whose account api the controller opens on. */
    fun setDevice(device: D?) {
        owner.setDevice(device)
    }

    /** Whether the app is in the foreground. */
    fun setForeground(foreground: Boolean) {
        owner.setForeground(foreground)
    }

    /** Whether the screen shows. */
    fun setVisible(visible: Boolean) {
        owner.setVisible(visible)
    }

    /** Pull to refresh; the controller publishes its refreshing state. */
    fun refresh() {
        controller?.refresh()
    }

    /** The swipe's Sign out, or the row's screen reader action: asks first. */
    fun requestSignOut(sessionId: String) {
        if (controller == null) {
            return
        }
        val row = ui.rows.firstOrNull { it.sessionId == sessionId } ?: return
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
                val row = ui.rows.firstOrNull { it.sessionId == target.row.sessionId }
                if (row?.signingOut == true) {
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
        owner.close()
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
