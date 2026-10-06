package com.bringyour.network.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SolanaAddressTest {

    // a fixture key (sha256 of "urnetwork test solana wallet"), not a wallet
    private val key = "GES2rXHpUVwXKtXRDVXUH7uU2BLX3VPUPexTq4uTXuvt"

    @Test
    fun `a base58 32 byte public key is valid syntax`() {
        // the USDC mint is well formed; only the server refuses it as a payout address
        assertTrue(SolanaAddress.isValidSyntax("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"))
        assertTrue(SolanaAddress.isValidSyntax(key))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertTrue(SolanaAddress.isValidSyntax("  $key\n"))
        assertTrue(SolanaAddress.isValidSyntax("\tEPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v "))
    }

    @Test
    fun `empty and blank are invalid`() {
        assertFalse(SolanaAddress.isValidSyntax(""))
        assertFalse(SolanaAddress.isValidSyntax("   "))
    }

    @Test
    fun `a bittensor coldkey is not a solana address`() {
        // ss58: a prefix byte, the 32 byte key and a two byte checksum, 35 bytes
        assertFalse(SolanaAddress.isValidSyntax("5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"))
    }

    @Test
    fun `an evm address is invalid`() {
        assertFalse(SolanaAddress.isValidSyntax("0x4b2a9f3e1c7d8a6b5e0f2d1c3b4a596877665544"))
    }

    @Test
    fun `characters outside the base58 alphabet are invalid`() {
        for (c in listOf('0', 'O', 'I', 'l')) {
            assertFalse("$c", SolanaAddress.isValidSyntax(c + key.drop(1)))
        }
    }

    @Test
    fun `a 31 byte key is invalid`() {
        // 41 characters, inside the length range, so the byte count decides
        assertFalse(SolanaAddress.isValidSyntax("thX6LZfHDZZKUs92febYZhYRcXddmzfzF2NvTkPNE"))
    }

    @Test
    fun `a 33 byte value is invalid`() {
        assertFalse(SolanaAddress.isValidSyntax("JNArUumxYJcSQpbuxuroRZtcSMVLcy5WbYGt14SRm1Fv"))
    }

    @Test
    fun `short keeps the first and last four characters`() {
        assertEquals("GES2…Xuvt", SolanaAddress.short(key))
        assertEquals("GES2…Xuvt", SolanaAddress.short(" $key "))
        assertEquals("7Xk9aQ2m3fQa", SolanaAddress.short("7Xk9aQ2m3fQa"))
    }
}
