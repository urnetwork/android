package com.bringyour.network.ui.wallet

import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sign screen's pure checks and payload (Top200Binding.kt) and its state
 * holder (Top200SignFlow) with a fake signer, on the spec's test vector: sn
 * `fleet_sign_test.go`'s fixture, client seed 0x01..0x20 (mmm's
 * tests/top200-fleet-binding.test.mjs uses the same).
 */
class Top200BindingTest {

    companion object {
        const val CLIENT_ID = "019b2e10-5a7c-4d3e-8f01-23456789abcd"
        const val CLIENT_KEY = "0x79b5562e8fe654f94078b112e8a98ba7901f853ae695bed7e0e3910bad049664"
        const val BINDING = "{\"chain_id\":945,\"netuid\":25,\"coordinator\":\"0xc0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3\",\"fleet_id\":\"0xf0f1f2f3f4f5f6f7f8f9fafbfcfdfeffe0e1e2e3e4e5e6e7e8e9eaebecedeeef\",\"hotkey\":\"0x0a1c77edb48519069bbb154bf088c7f17678839de458e0679370ae989af3f55c\",\"client_id\":\"019b2e10-5a7c-4d3e-8f01-23456789abcd\",\"client_key\":\"0x79b5562e8fe654f94078b112e8a98ba7901f853ae695bed7e0e3910bad049664\",\"generation\":3,\"valid_from_epoch\":100,\"valid_to_epoch\":200,\"commitment_hash\":\"0x9e1d4fa4a979f45cf003f26d826837250b08b45b8e896f98316cfc60005d3471\"}"
        const val SIGNATURE = "0x8a3e5b990f1a280f0eef9455c81dfbfd251a5ac5a9bc021770cf0a4dee1b4aafab04762d6ca8b587c1e04642d46cff87844b89aefcaddf26700fdbc849efbc0a"
        const val PAYLOAD = "{\"binding\":{\"chain_id\":945,\"netuid\":25,\"coordinator\":\"0xc0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3\",\"fleet_id\":\"0xf0f1f2f3f4f5f6f7f8f9fafbfcfdfeffe0e1e2e3e4e5e6e7e8e9eaebecedeeef\",\"hotkey\":\"0x0a1c77edb48519069bbb154bf088c7f17678839de458e0679370ae989af3f55c\",\"client_id\":\"019b2e10-5a7c-4d3e-8f01-23456789abcd\",\"client_key\":\"0x79b5562e8fe654f94078b112e8a98ba7901f853ae695bed7e0e3910bad049664\",\"generation\":3,\"valid_from_epoch\":100,\"valid_to_epoch\":200,\"commitment_hash\":\"0x9e1d4fa4a979f45cf003f26d826837250b08b45b8e896f98316cfc60005d3471\"},\"client_signature\":\"0x8a3e5b990f1a280f0eef9455c81dfbfd251a5ac5a9bc021770cf0a4dee1b4aafab04762d6ca8b587c1e04642d46cff87844b89aefcaddf26700fdbc849efbc0a\"}"

        const val OTHER_CLIENT_ID = "019b2e10-5a7c-4d3e-8f01-23456789abce"
        const val OTHER_CLIENT_KEY = "0x79b5562e8fe654f94078b112e8a98ba7901f853ae695bed7e0e3910bad049665"

        // the vector with one field's json value replaced
        fun withField(name: String, json: String): String {
            val m = Regex("\"$name\":(\"[^\"]*\"|[^,}]*)").find(BINDING)!!
            return BINDING.replaceRange(m.range, "\"$name\":$json")
        }

        fun withoutField(name: String): String {
            val m = Regex(",?\"$name\":(\"[^\"]*\"|[^,}]*)").find(BINDING)!!
            return BINDING.removeRange(m.range).replace("{,", "{")
        }

        fun b64url(text: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray(Charsets.UTF_8))
    }

    private fun check(text: String, clientId: String? = CLIENT_ID, clientKey: String = CLIENT_KEY) =
        Top200Bindings.check(text, clientId, clientKey)

    private class FakeDevice(
        override val clientId: String? = CLIENT_ID,
        override val clientKey: String = CLIENT_KEY,
        val sign: (String) -> String = { SIGNATURE },
    ) : Top200SignDevice {
        val signed = mutableListOf<String>()
        override fun signFleetBinding(bindingJson: String): String {
            signed.add(bindingJson)
            return sign(bindingJson)
        }
    }

    // ---- validation

    @Test
    fun `the vector's binding is valid for the vector's device`() {
        val c = check(BINDING)
        assertTrue(c is Top200BindingCheck.Valid)
        val valid = c as Top200BindingCheck.Valid
        assertEquals(BINDING, valid.text)
        assertEquals(945L, valid.binding.chainId)
        assertEquals(25L, valid.binding.netuid)
        assertEquals("0xf0f1f2f3f4f5f6f7f8f9fafbfcfdfeffe0e1e2e3e4e5e6e7e8e9eaebecedeeef", valid.binding.fleetId)
        assertEquals("0x0a1c77edb48519069bbb154bf088c7f17678839de458e0679370ae989af3f55c", valid.binding.hotkey)
        assertEquals(3L, valid.binding.generation)
        assertEquals(100L, valid.binding.validFromEpoch)
        assertEquals(200L, valid.binding.validToEpoch)
    }

    @Test
    fun `surrounding whitespace is trimmed and hex case does not matter`() {
        val c = check("\n  $BINDING \n")
        assertEquals(BINDING, (c as Top200BindingCheck.Valid).text)
        // the device key in upper case, the binding's client id as 0x hex
        assertTrue(check(BINDING, clientKey = "0x" + CLIENT_KEY.substring(2).uppercase()) is Top200BindingCheck.Valid)
        assertTrue(check(BINDING, clientId = "0x019b2e105a7c4d3e8f0123456789abcd") is Top200BindingCheck.Valid)
        assertTrue(check(withField("client_id", "\"0x019B2E105A7C4D3E8F0123456789ABCD\"")) is Top200BindingCheck.Valid)
    }

    @Test
    fun `another device's client id is refused`() {
        assertEquals(Top200BindingCheck.OtherDevice, check(BINDING, clientId = OTHER_CLIENT_ID))
        assertEquals(Top200BindingCheck.OtherDevice, check(withField("client_id", "\"$OTHER_CLIENT_ID\"")))
        assertEquals(Top200BindingCheck.OtherDevice, check(BINDING, clientId = null))
    }

    @Test
    fun `another device's client key is refused before the sdk is asked`() {
        assertEquals(Top200BindingCheck.OtherDevice, check(BINDING, clientKey = OTHER_CLIENT_KEY))
        assertEquals(Top200BindingCheck.OtherDevice, check(withField("client_key", "\"$OTHER_CLIENT_KEY\"")))
    }

    @Test
    fun `a pasted payload is not a binding`() {
        assertEquals(Top200BindingCheck.Invalid, check(PAYLOAD))
        assertNull(Top200Bindings.parse(PAYLOAD))
    }

    @Test
    fun `malformed json is invalid`() {
        val cases = listOf(
            "{",
            "not json",
            BINDING.dropLast(1),
            "$BINDING}",
            "[$BINDING]",
            "\"$BINDING\"",
            "null",
            "945",
        )
        for (c in cases) {
            assertEquals(c, Top200BindingCheck.Invalid, check(c))
        }
    }

    @Test
    fun `text longer than the cap is invalid, even a binding padded with whitespace`() {
        // whitespace inside the object is valid json, so only the cap refuses these
        fun padded(length: Int): String =
            "{" + " ".repeat(length - BINDING.length) + BINDING.substring(1)
        assertEquals(4096, Top200Bindings.TEXT_MAX_CHARS)
        val atCap = padded(Top200Bindings.TEXT_MAX_CHARS)
        assertEquals(Top200Bindings.TEXT_MAX_CHARS, atCap.length)
        assertTrue(check(atCap) is Top200BindingCheck.Valid)
        assertEquals(Top200BindingCheck.Invalid, check(padded(Top200Bindings.TEXT_MAX_CHARS + 1)))
        assertEquals(Top200BindingCheck.Invalid, check("x".repeat(1_000_000)))
        // surrounding whitespace is trimmed before the cap applies
        assertTrue(check(" ".repeat(10_000) + BINDING + "\n".repeat(10_000)) is Top200BindingCheck.Valid)
    }

    @Test
    fun `an empty field is neither valid nor an error`() {
        assertEquals(Top200BindingCheck.Empty, check(""))
        assertEquals(Top200BindingCheck.Empty, check(" \n\t"))
    }

    @Test
    fun `every field is required and no other is allowed`() {
        for (name in listOf(
            "chain_id", "netuid", "coordinator", "fleet_id", "hotkey", "client_id",
            "client_key", "generation", "valid_from_epoch", "valid_to_epoch", "commitment_hash",
        )) {
            assertEquals(name, Top200BindingCheck.Invalid, check(withoutField(name)))
        }
        assertEquals(Top200BindingCheck.Invalid, check(BINDING.dropLast(1) + ",\"extra\":1}"))
    }

    @Test
    fun `a duplicate key is invalid`() {
        // the json tree keeps the last duplicate; the review would show it, the sdk might sign either
        val other = "0x1111111111111111111111111111111111111111111111111111111111111111"
        assertEquals(Top200BindingCheck.Invalid, check(BINDING.dropLast(1) + ",\"hotkey\":\"$other\"}"))
        assertEquals(Top200BindingCheck.Invalid, check(BINDING.replace("{\"chain_id\"", "{\"hotkey\":\"$other\",\"chain_id\"")))
        assertEquals(Top200BindingCheck.Invalid, check(BINDING.dropLast(1) + ",\"client_key\":\"$CLIENT_KEY\"}"))
    }

    @Test
    fun `a case-variant key is invalid`() {
        // Go's decoder would take "HOTKEY" as hotkey
        val other = "0x1111111111111111111111111111111111111111111111111111111111111111"
        assertEquals(Top200BindingCheck.Invalid, check(BINDING.dropLast(1) + ",\"HOTKEY\":\"$other\"}"))
        assertEquals(Top200BindingCheck.Invalid, check(BINDING.replace("\"hotkey\"", "\"Hotkey\"")))
        assertEquals(Top200BindingCheck.Invalid, check(BINDING.replace("\"client_key\"", "\"CLIENT_KEY\"")))
    }

    @Test
    fun `an extra or escaped key is invalid`() {
        assertEquals(Top200BindingCheck.Invalid, check(BINDING.dropLast(1) + ",\"note\":\"x\"}"))
        assertEquals(Top200BindingCheck.Invalid, check(BINDING.dropLast(1) + ",\"nested\":{\"hotkey\":1}}"))
        // "hotkey" spelled with an escape decodes to the same name
        assertEquals(Top200BindingCheck.Invalid, check(BINDING.replace("\"hotkey\"", "\"hot\\u006bey\"")))
        // whitespace between tokens is fine
        assertTrue(check(BINDING.replace(",\"netuid\":", " ,\n \"netuid\" : ")) is Top200BindingCheck.Valid)
    }

    @Test
    fun `bad values are invalid`() {
        val cases = listOf(
            // hex sizes
            "coordinator" to "\"0xc0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2\"",
            "coordinator" to "\"c0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3\"",
            "fleet_id" to "\"0xf0f1f2f3f4f5f6f7f8f9fafbfcfdfeffe0e1e2e3e4e5e6e7e8e9eaebecedee\"",
            "hotkey" to "\"0x0a1c77edb48519069bbb154bf088c7f17678839de458e0679370ae989af3f55c00\"",
            "client_key" to "\"0x79b5562e8fe654f94078b112e8a98ba7901f853ae695bed7e0e3910bad04966g\"",
            "commitment_hash" to "\"0x9e1d\"",
            "client_id" to "\"019b2e10-5a7c-4d3e-8f01-23456789abc\"",
            "client_id" to "\"0x019b2e105a7c4d3e8f0123456789ab\"",
            // integers
            "chain_id" to "0",
            "chain_id" to "\"945\"",
            "chain_id" to "945.0",
            "chain_id" to "9.45e2",
            "netuid" to "65536",
            "netuid" to "-1",
            "generation" to "0",
            "valid_from_epoch" to "-5",
            // a string field given as a number
            "coordinator" to "1",
        )
        for ((name, json) in cases) {
            assertEquals("$name=$json", Top200BindingCheck.Invalid, check(withField(name, json)))
        }
        assertTrue(check(withField("netuid", "65535")) is Top200BindingCheck.Valid)
        assertTrue(check(withField("valid_from_epoch", "0")) is Top200BindingCheck.Valid)
    }

    @Test
    fun `valid to before valid from is invalid`() {
        assertEquals(Top200BindingCheck.Invalid, check(withField("valid_to_epoch", "99")))
        assertTrue(check(withField("valid_to_epoch", "100")) is Top200BindingCheck.Valid)
    }

    @Test
    fun `without the client key a binding cannot be signed`() {
        assertEquals(Top200BindingCheck.NoKey, check(BINDING, clientKey = ""))
        // a non-binding is still invalid first
        assertEquals(Top200BindingCheck.Invalid, check(PAYLOAD, clientKey = ""))
    }

    // ---- the payload and member entry

    @Test
    fun `the payload is the vector's byte for byte`() {
        assertEquals(PAYLOAD, Top200Bindings.payload(BINDING, SIGNATURE))
        assertEquals(PAYLOAD, Top200Bindings.payload("  $BINDING\n", SIGNATURE))
    }

    @Test
    fun `the payload wraps the pasted text, never a re-serialization`() {
        // spacing a page might add stays as pasted (inside the trim)
        val spaced = BINDING.replace(",\"netuid\"", ", \"netuid\"")
        assertEquals("{\"binding\":$spaced,\"client_signature\":\"$SIGNATURE\"}", Top200Bindings.payload(spaced, SIGNATURE))
    }

    @Test
    fun `the member entry carries the id as 0x hex and the key`() {
        assertEquals(
            "{\"client_id\":\"0x019b2e105a7c4d3e8f0123456789abcd\",\"client_key\":\"$CLIENT_KEY\"}",
            Top200Bindings.memberEntry(CLIENT_ID, CLIENT_KEY)
        )
        assertEquals(
            "{\"client_id\":\"0x019b2e105a7c4d3e8f0123456789abcd\",\"client_key\":\"$CLIENT_KEY\"}",
            Top200Bindings.memberEntry(CLIENT_ID.uppercase(), CLIENT_KEY)
        )
        assertNull(Top200Bindings.memberEntry(CLIENT_ID, ""))
        assertNull(Top200Bindings.memberEntry(null, CLIENT_KEY))
    }

    @Test
    fun `long hex is shortened to the first 10 and last 6`() {
        assertEquals("0x79b5562e…049664", Top200Bindings.shortHex(CLIENT_KEY))
    }

    // ---- the app link

    @Test
    fun `the app link's binding decodes to the binding`() {
        assertEquals(BINDING, Top200Bindings.decodeLinkBinding(b64url(BINDING)))
        assertTrue(Top200Bindings.isSignLink("ur", "top200", "/sign"))
        assertFalse(Top200Bindings.isSignLink("ur", "top200", "/other"))
        assertFalse(Top200Bindings.isSignLink("https", "top200", "/sign"))
        assertFalse(Top200Bindings.isSignLink("ur", "bittensor-sign-message", null))
    }

    @Test
    fun `bad app links decode to nothing`() {
        assertNull(Top200Bindings.decodeLinkBinding(null))
        assertNull(Top200Bindings.decodeLinkBinding(""))
        // not base64url: the standard alphabet, padding, spaces
        val standard = Base64.getEncoder().encodeToString(byteArrayOf(-5, -1, -2, 0x3e, 0x3f))
        assertTrue(standard.contains('+') || standard.contains('/'))
        assertNull(Top200Bindings.decodeLinkBinding(standard))
        assertNull(Top200Bindings.decodeLinkBinding(b64url(BINDING) + "=="))
        assertNull(Top200Bindings.decodeLinkBinding("abc def"))
        assertNull(Top200Bindings.decodeLinkBinding("a"))
        // not utf-8
        val notUtf8 = Base64.getUrlEncoder().withoutPadding().encodeToString(byteArrayOf(0x7b, -1, -2, 0x7d))
        assertNull(Top200Bindings.decodeLinkBinding(notUtf8))
    }

    @Test
    fun `the app link's binding is taken raw, once, and percent escapes are refused`() {
        val encoded = b64url(BINDING)
        assertEquals(encoded, Top200Bindings.linkBindingParameter("binding=$encoded"))
        assertEquals(encoded, Top200Bindings.linkBindingParameter("x=1&binding=$encoded&y=2"))
        assertNull(Top200Bindings.linkBindingParameter(null))
        assertNull(Top200Bindings.linkBindingParameter("x=1"))
        assertNull(Top200Bindings.linkBindingParameter("binding=$encoded&binding=$encoded"))
        // a percent-escaped character stays escaped and is not base64url
        val escaped = encoded.substring(0, 8) + "%41" + encoded.substring(9)
        assertEquals(escaped, Top200Bindings.linkBindingParameter("binding=$escaped"))
        assertNull(Top200Bindings.decodeLinkBinding(Top200Bindings.linkBindingParameter("binding=$escaped")))
        assertNull(Top200Bindings.decodeLinkBinding(Top200Bindings.linkBindingParameter("binding=$encoded%3D")))
    }

    @Test
    fun `app links over 4096 characters are refused`() {
        val atLimit = "A".repeat(Top200Bindings.LINK_MAX_CHARS)
        assertEquals(4096, atLimit.length)
        assertTrue(Top200Bindings.decodeLinkBinding(atLimit) != null)
        assertNull(Top200Bindings.decodeLinkBinding("A".repeat(Top200Bindings.LINK_MAX_CHARS + 4)))
        assertNull(Top200Bindings.decodeLinkBinding(atLimit + "A"))
    }

    // ---- the state holder

    @Test
    fun `signing the vector's binding gives the vector's payload`() = runBlocking {
        val device = FakeDevice()
        val flow = Top200SignFlow(signDispatcher = Dispatchers.Unconfined)
        flow.setDevice(device)
        flow.setText("\n$BINDING\n")
        assertTrue(flow.state.value.canSign)
        flow.sign()
        assertEquals(listOf(BINDING), device.signed)
        assertEquals(Top200SignResult.Signed(PAYLOAD), flow.state.value.result)
        assertFalse(flow.state.value.signing)
    }

    @Test
    fun `another device's binding is never signed`() = runBlocking {
        for (device in listOf(FakeDevice(clientKey = OTHER_CLIENT_KEY), FakeDevice(clientId = OTHER_CLIENT_ID), FakeDevice(clientKey = ""))) {
            val flow = Top200SignFlow(signDispatcher = Dispatchers.Unconfined)
            flow.setDevice(device)
            flow.setText(BINDING)
            assertFalse(flow.state.value.canSign)
            flow.sign()
            assertTrue(device.signed.isEmpty())
            assertNull(flow.state.value.result)
        }
        val device = FakeDevice()
        val flow = Top200SignFlow(signDispatcher = Dispatchers.Unconfined)
        flow.setDevice(device)
        flow.setText(PAYLOAD)
        flow.sign()
        assertTrue(device.signed.isEmpty())
        assertEquals(Top200BindingCheck.Invalid, flow.state.value.check)
    }

    @Test
    fun `an sdk error or an empty signature is a failure with its detail`() = runBlocking {
        val failing = FakeDevice(sign = { throw IllegalStateException("binding client_id is not this device") })
        val flow = Top200SignFlow(signDispatcher = Dispatchers.Unconfined)
        flow.setDevice(failing)
        flow.setText(BINDING)
        flow.sign()
        assertEquals(Top200SignResult.Failed("binding client_id is not this device"), flow.state.value.result)

        val empty = FakeDevice(sign = { "" })
        flow.setDevice(empty)
        flow.sign()
        assertEquals(Top200SignResult.Failed("empty signature"), flow.state.value.result)
    }

    @Test
    fun `editing the binding clears the payload`() = runBlocking {
        val flow = Top200SignFlow(signDispatcher = Dispatchers.Unconfined)
        flow.setDevice(FakeDevice())
        flow.setText(BINDING)
        flow.sign()
        assertTrue(flow.state.value.result is Top200SignResult.Signed)
        flow.setText("$BINDING ")
        assertNull(flow.state.value.result)
    }

    @Test
    fun `an app link fills the field and never signs`() {
        val device = FakeDevice()
        val flow = Top200SignFlow(signDispatcher = Dispatchers.Unconfined)
        flow.setDevice(device)
        flow.applyLink(Top200SignLink(Top200Bindings.decodeLinkBinding(b64url(BINDING))))
        assertEquals(BINDING, flow.state.value.text)
        assertTrue(flow.state.value.check is Top200BindingCheck.Valid)
        assertNull(flow.state.value.result)
        assertTrue(device.signed.isEmpty())

        // a bad link: the invalid error on an empty field
        flow.applyLink(Top200SignLink(Top200Bindings.decodeLinkBinding("A".repeat(5000))))
        assertEquals("", flow.state.value.text)
        assertEquals(Top200BindingCheck.Invalid, flow.state.value.check)
        assertTrue(device.signed.isEmpty())
    }

    @Test
    fun `the member entry follows the device`() {
        val flow = Top200SignFlow(signDispatcher = Dispatchers.Unconfined)
        assertNull(flow.state.value.memberEntry)
        flow.setDevice(FakeDevice())
        assertEquals(Top200Bindings.memberEntry(CLIENT_ID, CLIENT_KEY), flow.state.value.memberEntry)
        flow.setDevice(FakeDevice(clientKey = ""))
        assertNull(flow.state.value.memberEntry)
    }
}
