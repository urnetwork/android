package com.bringyour.network.ui.shared.viewmodels

import com.bringyour.sdk.Sdk

/**
 * The post-purchase confirmation for purchases the app cannot see complete on the
 * server itself (the Stripe sheet, the ur.io pay page, a checkout that returns
 * through the login deep link). UPGRADE.md N2: the success overlay used to launch
 * the moment the sheet or page reported success, before the server had confirmed
 * anything, and its premium copy keyed off whether the network was Pro at all -- so
 * a network that was already Pro (or a stale snapshot) read "You're premium." for a
 * purchase the server never saw.
 *
 * The confirmation is now the SDK's SubscriptionBalanceViewController (one
 * implementation for every app): it polls, anchors the rule to a baseline taken
 * before the purchase (Pro flipped false -> true, or the balance grew), pauses its
 * two-minute budget while the app is in the background, and ends in a terminal
 * state. The overlay launches only on Confirmed; ConfirmationGaveUp shows the
 * confirmation-delayed notice.
 *
 * [prepare] opens the controller when a purchase UI starts so the baseline loads
 * before the payment; [confirm] starts the confirmation when the purchase UI
 * reports success. Main-thread confined: the source's state callbacks must be
 * delivered on the main thread.
 */
internal class PurchaseConfirmation(
    private val openSource: (onState: (String) -> Unit) -> Source?,
    private val onConfirmed: (isPro: Boolean) -> Unit,
    private val onGaveUp: () -> Unit,
) {
    /** The parts of SubscriptionBalanceViewController the confirmation drives. */
    interface Source {
        fun start()
        fun setForeground(foreground: Boolean)
        fun startPurchaseConfirmation()
        fun clearPurchaseConfirmation()
        fun isPro(): Boolean
        fun close()
    }

    private var source: Source? = null
    private var foreground = true

    var waiting = false
        private set

    private fun ensureSource(): Source? {
        source?.let { return it }
        val opened = openSource { state -> onState(state) } ?: return null
        source = opened
        opened.setForeground(foreground)
        opened.start()
        return opened
    }

    private fun closeSource() {
        source?.close()
        source = null
    }

    fun prepare() {
        ensureSource()
    }

    /** The purchase UI ended without a purchase. */
    fun cancel() {
        if (!waiting) {
            closeSource()
        }
    }

    /** Returns false when there is no api to confirm against. */
    fun confirm(): Boolean {
        val s = ensureSource() ?: return false
        waiting = true
        s.startPurchaseConfirmation()
        return true
    }

    fun setForeground(foreground: Boolean) {
        this.foreground = foreground
        source?.setForeground(foreground)
    }

    fun onState(state: String) {
        if (!waiting) {
            return
        }
        when (state) {
            Sdk.PurchaseConfirmationStateConfirmed -> {
                val isPro = source?.isPro() ?: false
                end()
                onConfirmed(isPro)
            }
            Sdk.PurchaseConfirmationStateConfirmationGaveUp -> {
                end()
                onGaveUp()
            }
        }
    }

    private fun end() {
        waiting = false
        source?.clearPurchaseConfirmation()
        closeSource()
    }

    fun close() {
        waiting = false
        closeSource()
    }
}
