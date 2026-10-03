package com.bringyour.network.ui.login

/**
 * The forgot-password address field. It opens with the address from the
 * sign-in screen, and the user may correct it before sending. The link goes to
 * the field as typed, so the after-send screen, whose Resend sends the link
 * again, must get that same address: it used to get the address the screen
 * opened with, so after an edit Resend mailed the old address.
 */
class PasswordResetAddress(openedWith: String) {
    var typed: String = openedWith

    /** The address a reset link request goes to. */
    val userAuth: String
        get() = typed.trim()

    /** The after-send route for the address the link went to. `encode` is `Uri.encode` on device. */
    fun afterSendRoute(encode: (String) -> String): String =
        "reset-password-after-send/${encode(userAuth)}"
}
