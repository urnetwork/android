package com.bringyour.network

import android.os.Build

/**
 * The physical network's Private DNS (DNS-over-TLS) mode, as Android reports it
 * on LinkProperties from API 28 (P) onward.
 *
 * Strict mode (a user-set hostname) makes Android send every lookup as DoT
 * straight to that host through the tunnel, bypassing URnetwork's DNS. When that
 * DoT path is down the device gets no name resolution at all
 * (ERR_NAME_NOT_RESOLVED, "Private DNS server cannot be accessed"). The app
 * neither detects nor explains this today; this is the detected state it acts on
 * (see MainApplication's physical-network callback and ConnectActions' notice).
 *
 * Kept Android-free apart from the mapping input so it is unit testable
 * (PrivateDnsModeTest).
 */
sealed class PrivateDnsMode {
    /** Private DNS is off; lookups are plaintext and ride the tunnel normally. */
    object Off : PrivateDnsMode()

    /** Automatic: DoT is tried opportunistically and falls back to the tunnel. */
    object Opportunistic : PrivateDnsMode()

    /** A user-set DoT hostname; [host] is the provider name Android validates against. */
    data class Strict(val host: String) : PrivateDnsMode()

    /** A short tag for the service log line (see MainService builder.establish()). */
    fun logValue(): String = when (this) {
        Off -> "off"
        Opportunistic -> "opportunistic"
        is Strict -> "strict($host)"
    }
}

/**
 * Maps the LinkProperties Private DNS signals to a [PrivateDnsMode].
 *
 * The two Android inputs are `isPrivateDnsActive` and `privateDnsServerName`
 * (both API 28+). A non-blank server name with DoT active is strict mode; DoT
 * active with no server name is automatic (opportunistic); otherwise off. Below
 * API 28 neither signal exists, so the mode is reported Off.
 *
 * Pure: [sdkInt] is passed in rather than read from Build so the SDK_INT guard
 * is exercised in tests.
 */
fun privateDnsModeOf(
    sdkInt: Int,
    isPrivateDnsActive: Boolean,
    privateDnsServerName: String?,
): PrivateDnsMode {
    if (sdkInt < Build.VERSION_CODES.P) {
        return PrivateDnsMode.Off
    }
    if (!isPrivateDnsActive) {
        return PrivateDnsMode.Off
    }
    val host = privateDnsServerName?.trim().orEmpty()
    return if (host.isNotEmpty()) {
        PrivateDnsMode.Strict(host)
    } else {
        PrivateDnsMode.Opportunistic
    }
}

/**
 * Whether the muted strict-mode notice shows: only while connected and only in
 * strict mode, since that is the state that silently breaks resolution. Returns
 * the host to interpolate into the notice, or null when nothing shows.
 */
fun privateDnsStrictNoticeHost(mode: PrivateDnsMode, connected: Boolean): String? {
    if (!connected) {
        return null
    }
    return (mode as? PrivateDnsMode.Strict)?.host
}
