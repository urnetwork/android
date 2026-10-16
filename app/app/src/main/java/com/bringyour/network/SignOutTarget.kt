package com.bringyour.network

/**
 * Whether a sign out the user asked for on `signedOutDevice` (the device
 * signed in when they asked) still applies to `currentDevice`. A sign out
 * that waits on the server first (the account deletion) can return after the
 * server signed that account out and the next account signed in; it must not
 * sign that account out (as the Apple app's signOut since 9e5468cb). No
 * device then is no account to sign out: the server's sign out already ran.
 * Identity, not equals: a gomobile proxy's equals is not identity.
 */
internal fun signOutApplies(signedOutDevice: Any?, currentDevice: Any?): Boolean =
    signedOutDevice != null && signedOutDevice === currentDevice
