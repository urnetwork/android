package com.bringyour.network.ui.settings

import com.bringyour.network.ui.components.LoginMode

/**
 * The in-app entry to the existing ur.io cloud proxies (open bug P150, section
 * 30). ur.io/app/proxies already issues an always-on HTTPS proxy plus SOCKS and
 * WireGuard proxies on Pro, with routing hosted on the platform, so a user can
 * run URnetwork as a proxy without the Android VPN slot. The app declines a
 * local TUN-less mode for now and links to that page instead.
 *
 * Kept pure (CloudProxyTest) so the URL builder and the entry's visibility are
 * unit testable without an Android runtime.
 */
object CloudProxy {
    /** The default proxies host when the active network space names no link host. */
    const val DEFAULT_PROXIES_HOST = "ur.io"

    /** The path of the cloud proxies page on the link host. */
    private const val PROXIES_PATH = "/app/proxies"

    /**
     * The https url of the cloud proxies page.
     *
     * [linkHostName] is the active network space's link host (GetLinkHostName);
     * blank falls back to [DEFAULT_PROXIES_HOST], so a self-hosted space with no
     * link host still reaches the official page.
     *
     * [authCode] is an optional one-time sign-in code (POST /auth/code-create)
     * that opens the page signed in. It is a bearer secret, so only a one-time
     * code is ever placed in the url -- never a long-lived token. null opens the
     * page without one.
     */
    fun proxiesUrl(linkHostName: String?, authCode: String? = null): String {
        val host = linkHostName?.trim().orEmpty().ifEmpty { DEFAULT_PROXIES_HOST }
        val base = "https://$host$PROXIES_PATH"
        val code = authCode?.trim().orEmpty()
        return if (code.isEmpty()) base else "$base?auth_code=$code"
    }

    /**
     * Whether the cloud-proxy entry shows. The cloud proxies are tied to an
     * account, so the entry is shown only to a signed-in (non-guest) user; a
     * guest has no account to own a proxy.
     */
    fun entryVisible(loginMode: LoginMode): Boolean = loginMode == LoginMode.Authenticated
}
