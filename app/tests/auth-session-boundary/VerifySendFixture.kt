package com.bringyour.network.ui.login

/** Inert data carrier for the real LoginStartupState source. */
data class VerifySendError(val code: String, val message: String, val retryAfterSeconds: Long)
