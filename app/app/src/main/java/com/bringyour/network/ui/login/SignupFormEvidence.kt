package com.bringyour.network.ui.login

internal const val ACCEPTANCE_CREATE_NETWORK_ERROR_TAG = "acceptance.create.error"

/** An in-flight submission can still render the preceding frame's error text. */
internal fun signupFormErrorIsTerminal(errorPresent: Boolean, inProgress: Boolean): Boolean =
    errorPresent && !inProgress
