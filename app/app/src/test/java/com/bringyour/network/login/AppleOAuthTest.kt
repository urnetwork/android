package com.bringyour.network.login

import com.bringyour.network.ui.login.APPLE_OAUTH_SERVICES_ID
import com.bringyour.network.ui.login.appleOAuthAuthorizeUrl
import com.bringyour.network.ui.login.appleOAuthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sign in with Apple through Apple's web flow: the state carries the platform
 * claim the api's callback reads to redirect back to this app, and the
 * authorize url carries the client id, the callback, the state and the nonce.
 */
class AppleOAuthTest {

    @Test
    fun stateIsBase64UrlJsonWithThePlatformClaim() {
        val state = appleOAuthState("tok-1")
        assertFalse(state.contains("=") || state.contains("+") || state.contains("/"))
        val json = String(java.util.Base64.getUrlDecoder().decode(state), Charsets.UTF_8)
        assertEquals("{\"platform\":\"android\",\"token\":\"tok-1\"}", json)
    }

    @Test
    fun authorizeUrlCarriesClientRedirectStateAndNonce() {
        val url = appleOAuthAuthorizeUrl("https://api.bringyour.com/", "st ate", "n&once")
        assertEquals(
            "https://appleid.apple.com/auth/authorize" +
                    "?client_id=$APPLE_OAUTH_SERVICES_ID" +
                    "&redirect_uri=https%3A%2F%2Fapi.bringyour.com%2Fauth%2Fapple%2Fcallback" +
                    "&response_type=code%20id_token" +
                    "&response_mode=form_post" +
                    "&scope=name%20email" +
                    "&state=st%20ate" +
                    "&nonce=n%26once",
            url
        )
        assertTrue(url.startsWith("https://appleid.apple.com/"))
    }
}
