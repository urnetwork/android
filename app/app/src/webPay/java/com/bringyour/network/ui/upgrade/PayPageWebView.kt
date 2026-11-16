package com.bringyour.network.ui.upgrade

import android.annotation.SuppressLint
import android.content.Context
import android.os.Message
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * The pay page's web view (PayPageDialog), and the windows it opens.
 *
 * Stripe asks the card's bank to authenticate the buyer (3D Secure) while the buyer
 * is still on the page: Stripe.js puts the bank's challenge in a frame over the
 * page, and some banks' challenge opens a window of its own on the buyer's tap (an
 * approval page, a "verify in another window" link) that reports back to the
 * challenge through `window.opener` and closes itself. A web view without
 * multiple windows loads such a window in the pay page's place: `window.open`
 * hands back the top window, which goes to the window's url (or to
 * about:blank#blocked), so the page and the challenge waiting on the window are
 * gone, and the purchase with them.
 *
 * So the pay page's web view supports multiple windows: a window opened by a tap
 * (never by a script on its own: JavaScriptCanOpenWindowsAutomatically stays
 * false, as in a browser's popup blocker) becomes a second web view, which
 * PayPageDialog shows over the pay page, in the app, with the pay page and its
 * `window.opener` link kept underneath. It closes itself (`window.close()`), the
 * challenge closes it, or the reader does (PayPageDialog's close). The window is
 * held to the pay page's own rules and to no more: JavaScript and DOM storage (the
 * bank's pages need them), no file or content access, no bridge into the app, no
 * windows of its own, and no third-party cookies (the web view's default). Stripe
 * redirects (a payment method that leaves the page and comes back to it with
 * `complete=1`) stay top-level loads of the pay page itself, as before, and an
 * app's own link (a bank app's scheme) loads nowhere, as before.
 */
internal object PayPageWebView {

    /**
     * The pay page's web view: [onReturn] runs once, when the page or a window it
     * opened first navigates to [returnUrl]; [onPopup] shows a window the page
     * opened, [onPopupClosed] removes it. Main thread.
     */
    fun create(
        context: Context,
        returnUrl: String,
        onReturn: () -> Unit,
        onPopup: (WebView) -> Unit,
        onPopupClosed: (WebView) -> Unit,
    ): WebView = WebView(context).apply {
        var returned = false
        val returnOnce = {
            if (!returned) {
                returned = true
                onReturn()
            }
        }
        configure(this, multipleWindows = true)
        webViewClient = ReturnClient(returnUrl, returnOnce)
        webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                // only a window the reader's tap opened
                if (!isUserGesture) {
                    return false
                }
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                val popup = WebView(view.context)
                configure(popup, multipleWindows = false)
                popup.webViewClient = ReturnClient(returnUrl, returnOnce)
                popup.webChromeClient = object : WebChromeClient() {
                    override fun onCloseWindow(window: WebView) {
                        onPopupClosed(window)
                    }
                }
                transport.webView = popup
                resultMsg.sendToTarget()
                onPopup(popup)
                return true
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(webView: WebView, multipleWindows: Boolean) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportMultipleWindows(multipleWindows)
            javaScriptCanOpenWindowsAutomatically = false
            allowFileAccess = false
            allowContentAccess = false
        }
    }

    /** Hands control back when the page, or a window it opened, reaches the return url. */
    private class ReturnClient(private val returnUrl: String, private val onReturn: () -> Unit) : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url ?: return false
            if (url.toString().startsWith(returnUrl)) {
                onReturn()
                return true
            }
            // the page stays on ur.io and Stripe's own frames
            return false
        }
    }
}
