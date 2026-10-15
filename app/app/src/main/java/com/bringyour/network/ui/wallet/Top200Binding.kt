package com.bringyour.network.ui.wallet

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The Top 200 fleet binding as the ur.io page builds it and sn's
 * `provider fleet sign` prints it: one compact JSON object with exactly the
 * eleven fields of WHITEPAPER §11.4. The app signs it with the device's own
 * client key (Sign fleet binding, Earnings › Top 200).
 *
 * Everything here is pure, so the checks run (and are tested) before any sdk
 * call: the sdk checks the binding's client_id but not its client_key, so the
 * key check is the app's. Nothing here logs the binding or the payload, and
 * nothing is sent anywhere.
 */
data class Top200Binding(
    val chainId: Long,
    val netuid: Long,
    val coordinator: String,
    val fleetId: String,
    val hotkey: String,
    val clientId: String,
    val clientKey: String,
    val generation: Long,
    val validFromEpoch: Long,
    val validToEpoch: Long,
    val commitmentHash: String,
)

/** The pasted text judged for this device, in the order the checks run. */
sealed class Top200BindingCheck {
    /** the field is empty: nothing to judge yet */
    object Empty : Top200BindingCheck()

    /** not a binding (a pasted payload, a missing field, a bad value): top200_sign_error_invalid */
    object Invalid : Top200BindingCheck()

    /** the device's client key is not available: top200_sign_error_no_key */
    object NoKey : Top200BindingCheck()

    /** a binding of another client id or client key: top200_sign_error_other_device */
    object OtherDevice : Top200BindingCheck()

    /** this device's binding: `text` is the trimmed field, exactly what is signed and wrapped */
    data class Valid(val text: String, val binding: Top200Binding) : Top200BindingCheck()
}

object Top200Bindings {
    /** the longest `binding` parameter an app link may carry */
    const val LINK_MAX_CHARS = 4096

    const val LINK_SCHEME = "ur"
    const val LINK_HOST = "top200"
    const val LINK_PATH = "/sign"
    const val LINK_BINDING_PARAMETER = "binding"

    private val fieldNames = listOf(
        "chain_id",
        "netuid",
        "coordinator",
        "fleet_id",
        "hotkey",
        "client_id",
        "client_key",
        "generation",
        "valid_from_epoch",
        "valid_to_epoch",
        "commitment_hash",
    )

    private val unsignedIntegerPattern = Regex("^(0|[1-9][0-9]*)$")
    private val hex40Pattern = Regex("^0x[0-9a-fA-F]{40}$")
    private val hex64Pattern = Regex("^0x[0-9a-fA-F]{64}$")
    private val uuidPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private val hex32IdPattern = Regex("^0x[0-9a-fA-F]{32}$")
    private val base64UrlPattern = Regex("^[A-Za-z0-9_-]*$")

    /**
     * The binding in `text`, or null when it is not one: exactly the eleven
     * fields (a `{binding, client_signature}` payload is not a binding); the
     * integers whole JSON numbers >= 0 with netuid <= 65535, chain_id and
     * generation > 0 and valid_to_epoch >= valid_from_epoch; the coordinator
     * 0x + 40 hex; fleet_id, hotkey, client_key and commitment_hash 0x + 64
     * hex; client_id a uuid or 0x + 32 hex. Hex case is not significant.
     */
    fun parse(text: String): Top200Binding? {
        val json = try {
            Json.parseToJsonElement(text.trim()) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return null
        if (json.keys != fieldNames.toSet() || !hasExactKeys(text.trim())) {
            return null
        }

        // a json number with no sign, fraction or exponent
        val integer = { name: String ->
            (json[name] as? JsonPrimitive)
                ?.takeIf { !it.isString && unsignedIntegerPattern.matches(it.content) }
                ?.content
                ?.toLongOrNull()
        }
        val string = { name: String, pattern: Regex ->
            (json[name] as? JsonPrimitive)
                ?.takeIf { it.isString && pattern.matches(it.content) }
                ?.content
        }

        val chainId = integer("chain_id") ?: return null
        val netuid = integer("netuid") ?: return null
        val generation = integer("generation") ?: return null
        val validFromEpoch = integer("valid_from_epoch") ?: return null
        val validToEpoch = integer("valid_to_epoch") ?: return null
        val coordinator = string("coordinator", hex40Pattern) ?: return null
        val fleetId = string("fleet_id", hex64Pattern) ?: return null
        val hotkey = string("hotkey", hex64Pattern) ?: return null
        val clientKey = string("client_key", hex64Pattern) ?: return null
        val commitmentHash = string("commitment_hash", hex64Pattern) ?: return null
        val clientId = (json["client_id"] as? JsonPrimitive)
            ?.takeIf { it.isString && clientIdHex(it.content) != null }
            ?.content
            ?: return null

        if (chainId <= 0 || generation <= 0 || 65535 < netuid || validToEpoch < validFromEpoch) {
            return null
        }

        return Top200Binding(
            chainId = chainId,
            netuid = netuid,
            coordinator = coordinator,
            fleetId = fleetId,
            hotkey = hotkey,
            clientId = clientId,
            clientKey = clientKey,
            generation = generation,
            validFromEpoch = validFromEpoch,
            validToEpoch = validToEpoch,
            commitmentHash = commitmentHash,
        )
    }

    /**
     * Whether the object's own keys, as written, are the eleven names spelled
     * exactly, each once. The json tree keeps only the last of a duplicate key,
     * and the sdk's Go decoder matches keys case-insensitively with the last
     * duplicate winning, so a binding with both "hotkey" and "HOTKEY" (or two
     * "hotkey") could show one value in the review and sign another. A key
     * written with an escape is refused too. Run on text that parsed as an object.
     */
    private fun hasExactKeys(text: String): Boolean {
        val keys = mutableListOf<String>()
        var depth = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' -> {
                    // a string: find its end, skipping escapes
                    var j = i + 1
                    var escaped = false
                    while (text[j] != '"') {
                        if (text[j] == '\\') {
                            escaped = true
                            j++
                        }
                        j++
                    }
                    var k = j + 1
                    while (k < text.length && text[k].isWhitespace()) {
                        k++
                    }
                    if (depth == 1 && k < text.length && text[k] == ':') {
                        if (escaped) {
                            return false
                        }
                        keys.add(text.substring(i + 1, j))
                    }
                    i = j + 1
                    continue
                }
                c == '{' || c == '[' -> depth++
                c == '}' || c == ']' -> depth--
            }
            i++
        }
        return keys.size == fieldNames.size && keys.toSet() == fieldNames.toSet()
    }

    /**
     * Judges the field text for this device: a binding at all, then the
     * device's client key available, then the binding's client_id and
     * client_key this device's (the 16 id bytes and the key compared as
     * lowercase hex). Only [Top200BindingCheck.Valid] may be signed.
     */
    fun check(text: String, deviceClientId: String?, deviceClientKey: String): Top200BindingCheck {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return Top200BindingCheck.Empty
        }
        val binding = parse(trimmed) ?: return Top200BindingCheck.Invalid
        if (deviceClientKey.isEmpty()) {
            return Top200BindingCheck.NoKey
        }
        val deviceIdHex = deviceClientId?.let { clientIdHex(it) }
        if (deviceIdHex == null || clientIdHex(binding.clientId) != deviceIdHex) {
            return Top200BindingCheck.OtherDevice
        }
        if (binding.clientKey.lowercase() != deviceClientKey.lowercase()) {
            return Top200BindingCheck.OtherDevice
        }
        return Top200BindingCheck.Valid(text = trimmed, binding = binding)
    }

    /** The 16 id bytes as 32 lowercase hex, from a uuid or 0x + 32 hex; else null. */
    fun clientIdHex(clientId: String): String? = when {
        uuidPattern.matches(clientId) -> clientId.replace("-", "").lowercase()
        hex32IdPattern.matches(clientId) -> clientId.substring(2).lowercase()
        else -> null
    }

    /**
     * The device's fleet manifest member entry,
     * `{"client_id":"0x<32 hex>","client_key":"<client key>"}`; null without
     * a client id or a client key.
     */
    fun memberEntry(deviceClientId: String?, deviceClientKey: String): String? {
        val idHex = deviceClientId?.let { clientIdHex(it) } ?: return null
        if (deviceClientKey.isEmpty()) {
            return null
        }
        return "{\"client_id\":\"0x$idHex\",\"client_key\":\"$deviceClientKey\"}"
    }

    /**
     * The `{binding, client_signature}` payload: the trimmed binding text as
     * pasted, wrapped by concatenation (never re-serialized), so a page-built
     * binding gives the bytes `provider fleet sign` prints.
     */
    fun payload(bindingText: String, signature: String): String =
        "{\"binding\":" + bindingText.trim() + ",\"client_signature\":\"" + signature + "\"}"

    /** Whether a uri is the sign screen's app link, `ur://top200/sign?binding=<b64url>`. */
    fun isSignLink(scheme: String?, host: String?, path: String?): Boolean =
        scheme == LINK_SCHEME && host == LINK_HOST && (path == LINK_PATH || path == "$LINK_PATH/")

    /**
     * The raw `binding` parameter of an app link's encoded query, before any
     * percent decoding (a strict base64url value has nothing to decode, so a
     * percent escape is left in and refused by [decodeLinkBinding]). Null when
     * it is missing or given more than once.
     */
    fun linkBindingParameter(encodedQuery: String?): String? {
        val values = (encodedQuery ?: return null)
            .split('&')
            .filter { it.startsWith("$LINK_BINDING_PARAMETER=") }
            .map { it.substring(LINK_BINDING_PARAMETER.length + 1) }
        return values.singleOrNull()
    }

    /**
     * The binding text an app link carries: its `binding` parameter, base64url
     * (RFC 4648 §5, no padding) of the UTF-8 binding. Null when the parameter
     * is missing, longer than [LINK_MAX_CHARS], not strict base64url (padding,
     * the standard alphabet and percent escapes are refused), or not UTF-8.
     * The text still goes through [check] like a paste: the link only fills
     * the field.
     */
    fun decodeLinkBinding(parameter: String?): String? {
        if (parameter.isNullOrEmpty() || LINK_MAX_CHARS < parameter.length) {
            return null
        }
        if (!base64UrlPattern.matches(parameter) || parameter.length % 4 == 1) {
            return null
        }
        val bytes = try {
            Base64.getUrlDecoder().decode(parameter)
        } catch (e: IllegalArgumentException) {
            return null
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            null
        }
    }

    /** A long hex value for display: the first 10 and last 6 characters (`0x79b5562e…049664`). */
    fun shortHex(value: String): String =
        if (value.length <= 16) value else value.take(10) + "…" + value.takeLast(6)
}
