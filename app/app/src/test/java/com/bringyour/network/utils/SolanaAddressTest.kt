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
        // the USDC mint is well formed, as a Solana Pay quote names it; it is only not a wallet
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
    fun `the usdc mint and the all-zero key are not wallet addresses`() {
        assertEquals(List(32) { 0.toByte() }, Ss58.decodeBase58("1".repeat(32))?.toList())
        // both are well formed; the server refuses them as a wallet
        for (address in listOf("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", "1".repeat(32))) {
            assertTrue(address, SolanaAddress.isValidSyntax(address))
            assertFalse(address, SolanaAddress.isValidWalletAddress(address))
            assertFalse(address, SolanaAddress.isValidWalletAddress(" $address\n"))
        }
    }

    @Test
    fun `a value in the length range that is not 32 bytes is not a wallet address`() {
        // 44 characters of 33 bytes, and 32 characters of 23 bytes
        assertEquals(33, Ss58.decodeBase58("z".repeat(44))?.size)
        assertEquals(23, Ss58.decodeBase58("2".repeat(32))?.size)
        assertFalse(SolanaAddress.isValidWalletAddress("z".repeat(44)))
        assertFalse(SolanaAddress.isValidWalletAddress("2".repeat(32)))
    }

    @Test
    fun `a public key is a wallet address`() {
        assertTrue(SolanaAddress.isValidWalletAddress(key))
        assertTrue(SolanaAddress.isValidWalletAddress("  $key\n"))
        // 31 zero bytes and a one: leading zero bytes alone are not the all-zero key
        assertTrue(SolanaAddress.isValidWalletAddress("1".repeat(31) + "2"))
    }

    @Test
    fun `short keeps the first and last four characters`() {
        assertEquals("GES2…Xuvt", SolanaAddress.short(key))
        assertEquals("GES2…Xuvt", SolanaAddress.short(" $key "))
        assertEquals("7Xk9aQ2m3fQa", SolanaAddress.short("7Xk9aQ2m3fQa"))
    }
}
