package com.bringyour.network.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The delete-account dialog's typed gate, as a pure function.
 *
 * The dialog is one tap away from the settings list, and the button on it
 * used to be live the moment it opened. The gate makes the user type the
 * network name back before the button enables, so a stray tap cannot delete
 * an account. What this pins is the comparison itself: which entries count
 * as the name, and what happens when the name is not known.
 */
class DeleteAccountConfirmationTest {

    @Test
    fun theTypedGateOnlyAppliesWhenTheNetworkNameIsKnown() {
        assertTrue(DeleteAccountConfirmation.requiresTypedName("ur_network"))
        // the account view model has not answered yet, or answered with
        // nothing to show. The dialog falls back to the plain confirm rather
        // than locking the user out of deleting at all
        assertFalse(DeleteAccountConfirmation.requiresTypedName(null))
        assertFalse(DeleteAccountConfirmation.requiresTypedName(""))
        assertFalse(DeleteAccountConfirmation.requiresTypedName("   "))
    }

    @Test
    fun anUnknownNetworkNameConfirmsWithoutAnEntry() {
        assertTrue(DeleteAccountConfirmation.confirms(networkName = null, typed = ""))
        assertTrue(DeleteAccountConfirmation.confirms(networkName = "", typed = ""))
        assertTrue(DeleteAccountConfirmation.confirms(networkName = null, typed = "anything"))
    }

    @Test
    fun theNetworkNameTypedBackConfirms() {
        assertTrue(DeleteAccountConfirmation.confirms("ur_network", "ur_network"))
    }

    @Test
    fun anEmptyOrPartialEntryDoesNotConfirm() {
        assertFalse(DeleteAccountConfirmation.confirms("ur_network", ""))
        assertFalse(DeleteAccountConfirmation.confirms("ur_network", "ur_net"))
        assertFalse(DeleteAccountConfirmation.confirms("ur_network", "ur_network2"))
    }

    @Test
    fun aDifferentNameDoesNotConfirm() {
        assertFalse(DeleteAccountConfirmation.confirms("ur_network", "my_network"))
    }

    @Test
    fun surroundingWhitespaceIsNotPartOfTheName() {
        // soft keyboards append a space after an autocomplete accept, and a
        // name copied out of the account screen can carry one either side
        assertTrue(DeleteAccountConfirmation.confirms("ur_network", " ur_network "))
        assertTrue(DeleteAccountConfirmation.confirms(" ur_network ", "ur_network"))
    }

    @Test
    fun caseIsNotPartOfTheName() {
        // the field disables auto-capitalisation, but a keyboard that ignores
        // that hint would otherwise turn a correct entry into a refusal
        assertTrue(DeleteAccountConfirmation.confirms("ur_network", "UR_Network"))
        assertTrue(DeleteAccountConfirmation.confirms("UR_Network", "ur_network"))
    }

    @Test
    fun whitespaceInsideTheNameIsStillCompared() {
        assertFalse(DeleteAccountConfirmation.confirms("ur_network", "ur network"))
        assertFalse(DeleteAccountConfirmation.confirms("ur_network", "ur_ network"))
    }
}
