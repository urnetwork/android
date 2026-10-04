package com.bringyour.network.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Known substrate dev-account vectors (subkey //Alice, //Bob). */
class Ss58Test {

    private val alice = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"
    private val alicePubkey = "d43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d"
    private val alicePolkadot = "15oF4uVJwmo4TdGW7VfQxNLavjCXviqxT9S1MgbjMNHr6Sp5"
    private val bob = "5FHneW46xGXgs5mUiveU4sbTyGBzmstUspZC92UhjJM694ty"

    @Test
    fun `bittensor addresses decode to prefix 42 and the public key`() {
        val bytes = Ss58.decodeBase58(alice)!!
        assertEquals(35, bytes.size)
        assertEquals(42, bytes[0].toInt() and 0xFF)
        assertEquals(alicePubkey, bytes.copyOfRange(1, 33).joinToString("") { "%02x".format(it) })
        assertTrue(Ss58.isValidSyntax(alice))
        assertTrue(Ss58.isValidSyntax(" $bob\n"))
    }

    @Test
    fun `other prefixes and non base58 text are refused`() {
        assertFalse(Ss58.isValidSyntax(alicePolkadot))
        assertFalse(Ss58.isValidSyntax(alice.replace('5', '0')))
        assertFalse(Ss58.isValidSyntax(""))
    }

    @Test
    fun `short keeps both ends`() {
        assertEquals("5Grw…utQY", Ss58.short(alice))
    }
}
