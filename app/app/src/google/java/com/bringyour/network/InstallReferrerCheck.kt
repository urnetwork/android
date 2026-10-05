package com.bringyour.network

import com.android.installreferrer.api.InstallReferrerClient.InstallReferrerResponse
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/**
 * The Play install referrer behind a referral link (support inbox 1698). On
 * Android, ur.io/c sends a friend without the app to Play with
 * `referrer=https://ur.io/c?bonus=<code>`, and the first signed-out launch
 * after the install reads it back to pre-fill sign-up with the code.
 *
 * The read used to be gated on DeviceManager.canRefer, which reads the device
 * (`device?.canRefer ?: false`). The device is always null on the signed-out
 * launch that runs the read, so the referrer was never read. The gate is now a
 * one-shot flag the app keeps per install.
 *
 * Platform-free so the play unit tests cover it; LoginActivity binds the flag
 * to SharedPreferences and the read to the InstallReferrerClient.
 */
internal class InstallReferrerCheck(private val store: Store) {

    /** Whether this install has had its referrer read. */
    interface Store {
        /** True once a read ended with Play's final answer. */
        fun isChecked(): Boolean

        /** Records the final answer, so no later launch reads again. */
        fun markChecked()
    }

    /** A signed-out launch reads the referrer, once per install. */
    fun shouldCheck(signedIn: Boolean): Boolean = !signedIn && !store.isChecked()

    /**
     * Ends a read with Play's answer. An answer is final (the referrer, or
     * none to read on this device); a service that was not reachable is
     * asked again on the next launch.
     */
    fun finish(responseCode: Int) {
        when (responseCode) {
            InstallReferrerResponse.SERVICE_UNAVAILABLE,
            InstallReferrerResponse.SERVICE_DISCONNECTED -> Unit
            else -> store.markChecked()
        }
    }

    companion object {

        // the codes ur.io/c accepts (react/src/lib/connectLink.js); the server
        // validates the code itself
        private val CODE = Regex("^[A-Za-z0-9-]{1,64}$")

        /**
         * The referral code a Play install referrer carries, or null. Only a
         * ur.io/c link (https, host ur.io, path /c) counts, and only its
         * `bonus` code is used: anyone can put a referrer on a Play link, so
         * nothing else in it (an auth code, a target) is acted on.
         */
        fun referralCode(referrer: String?): String? {
            var text = referrer?.trim().orEmpty()
            if (text.isEmpty()) {
                return null
            }
            // Play returns the referrer decoded once; tolerate one left encoded
            if (text.startsWith("https%3A", ignoreCase = true)) {
                text = try {
                    URLDecoder.decode(text, "UTF-8")
                } catch (e: IllegalArgumentException) {
                    return null
                }
            }
            val uri = try {
                URI(text)
            } catch (e: URISyntaxException) {
                return null
            }
            if (uri.scheme != "https" || uri.host != "ur.io" || uri.path != "/c") {
                return null
            }
            val code = queryParameter(uri.rawQuery, "bonus") ?: return null
            return code.takeIf { CODE.matches(it) }
        }

        /** The decoded, trimmed value of the first [name] in [rawQuery], or null. */
        private fun queryParameter(rawQuery: String?, name: String): String? {
            for (pair in rawQuery.orEmpty().split('&')) {
                val i = pair.indexOf('=')
                val key = if (i < 0) pair else pair.substring(0, i)
                if (key == name) {
                    val value = if (i < 0) "" else pair.substring(i + 1)
                    return try {
                        URLDecoder.decode(value, "UTF-8").trim()
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }
            }
            return null
        }
    }
}
