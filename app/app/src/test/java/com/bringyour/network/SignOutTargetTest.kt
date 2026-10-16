package com.bringyour.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A sign out that waits on the server signs out only the account it was asked
 * for (owner report 2026-10-16, as the Apple app's 9e5468cb): the account
 * deletion answers after the server may have signed that account out and the
 * next account signed in.
 */
class SignOutTargetTest {

    @Test
    fun theSameDeviceSignsOut() {
        val device = Any()
        assertTrue(signOutApplies(device, device))
    }

    @Test
    fun theNextAccountsDeviceIsNotSignedOut() {
        assertFalse(signOutApplies(Any(), Any()))
    }

    @Test
    fun noDeviceIsNoAccountToSignOut() {
        // the server's sign out already ran
        assertFalse(signOutApplies(Any(), null))
        assertFalse(signOutApplies(null, null))
    }

    @Test
    fun anEqualDeviceThatIsNotTheSameIsNotSignedOut() {
        // a gomobile proxy's equals is not identity
        class Proxy {
            override fun equals(other: Any?) = other is Proxy
            override fun hashCode() = 0
        }
        assertFalse(signOutApplies(Proxy(), Proxy()))
    }
}
