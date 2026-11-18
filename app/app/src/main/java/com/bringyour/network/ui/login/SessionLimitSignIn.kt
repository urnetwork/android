package com.bringyour.network.ui.login

import android.content.Context
import com.bringyour.network.LoginClientCompletion
import com.bringyour.network.LoginStartupFailure
import com.bringyour.network.R
import com.bringyour.sdk.Sdk

/*
 * The server's refusal of a new session for a network that holds its cap of
 * active sessions (sdk api_session_limit.go). Every route that can create a
 * session answers it as its result's error, with the code
 * Sdk.SessionErrorCodeLimitReached: /auth/login (SSO, wallet, Bittensor and
 * recovery phrase sign-ins), /auth/login-with-password, /auth/verify,
 * /auth/network-create, /auth/code-login, and /network/auth-client, the
 * device's client right after a sign-in, whose failure is then
 * LoginStartupFailure.AUTH_CLIENT_SESSION_LIMIT_REACHED (authClientRefusal).
 *
 * Every sign-in screen shows sessions_limit_reached_sign_in for it where it
 * shows its errors, in place of the server's message, a generic error or a
 * wrong one: the user frees a session in Account > Sessions on a device that
 * is signed in. A screen of a signed-in device that can be refused (adding a
 * sign-in method, whose /auth/verify mints a session) shows
 * sessions_limit_reached instead: this device can open Account > Sessions.
 * Nothing retries and nothing signs out. The one check of the code, and the
 * lookups of the messages, are here.
 */

/** Whether a result error's code is the session limit's refusal. */
internal fun isSessionLimitReached(code: String?): Boolean = code == Sdk.SessionErrorCodeLimitReached

/** Whether a sign-in's client creation failed at the session limit. */
internal fun isSessionLimitReached(completion: LoginClientCompletion): Boolean =
    (completion as? LoginClientCompletion.Failed)?.failure == LoginStartupFailure.AUTH_CLIENT_SESSION_LIMIT_REACHED

/** What a sign-in screen shows for a result's error: the refusal's message in place of [message]. */
internal fun signInErrorMessage(context: Context, code: String?, message: String?): String? =
    if (isSessionLimitReached(code)) sessionLimitMessage(context) else message

/**
 * What a sign-in screen shows when the client creation after the sign-in
 * failed at the session limit. Null for any other outcome, which keeps the
 * screen's own error.
 */
internal fun signInClientFailureMessage(context: Context, completion: LoginClientCompletion): String? =
    if (isSessionLimitReached(completion)) sessionLimitMessage(context) else null

/** What a screen of a signed-in device shows for a result's error: the refusal's message in place of [message]. */
internal fun signedInErrorMessage(context: Context, code: String?, message: String?): String? =
    if (isSessionLimitReached(code)) context.getString(R.string.sessions_limit_reached) else message

private fun sessionLimitMessage(context: Context): String =
    context.getString(R.string.sessions_limit_reached_sign_in)
