package com.bringyour.network.ui.login

import android.util.Base64
import android.util.Log
import org.json.JSONObject

private const val TAG = "WalletCreateBundle"

/**
 * Encapsulates the wallet authentication data needed to create a network.
 *
 * The signed message is an arbitrary server-issued string (it may contain
 * spaces and newlines) so it is passed between screens as a Base64 URL-safe
 * JSON bundle rather than raw URI path segments. This avoids double-decoding
 * or route-parsing issues that can silently corrupt or empty a segment.
 */
data class WalletCreateBundle(
    val blockchain: String,
    val publicKey: String,
    val signedMessage: String,
    val signature: String
) {
    fun toBase64Json(): String {
        val json = JSONObject().apply {
            put("blockchain", blockchain)
            put("publicKey", publicKey)
            put("signedMessage", signedMessage)
            put("signature", signature)
        }.toString()
        return Base64.encodeToString(
            json.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }
}

fun String.toWalletCreateBundle(): WalletCreateBundle? {
    return try {
        val jsonBytes = Base64.decode(this, Base64.URL_SAFE or Base64.NO_PADDING)
        val json = JSONObject(String(jsonBytes, Charsets.UTF_8))
        WalletCreateBundle(
            blockchain = json.getString("blockchain"),
            publicKey = json.getString("publicKey"),
            signedMessage = json.getString("signedMessage"),
            signature = json.getString("signature")
        )
    } catch (e: Exception) {
        // do not log `this` - it is a base64-encoded bundle containing the
        // signed wallet challenge and signature, which is sensitive auth
        // material and must not end up in device logs.
        Log.e(TAG, "Failed to decode wallet create bundle", e)
        null
    }
}
