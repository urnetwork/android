package com.bringyour.network.ui.wallet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Bittensor manual entry's Continue: what the address checks must say first. */
class AddressValidationTest {

    @Test
    fun `only an ok or looks new address can continue`() {
        assertTrue(AddressValidation.Ok.canContinue)
        assertTrue(AddressValidation.LooksNew(null).canContinue)

        listOf(
            AddressValidation.Empty,
            AddressValidation.InvalidSyntax,
            AddressValidation.Checking,
            AddressValidation.Blocked(null),
            AddressValidation.Unavailable(null),
        ).forEach { assertFalse("$it", it.canContinue) }
    }
}
