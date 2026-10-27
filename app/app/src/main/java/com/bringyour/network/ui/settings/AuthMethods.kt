package com.bringyour.network.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bringyour.network.R
import com.bringyour.network.utils.sdkStringListToList
import com.bringyour.sdk.NetworkUser
import com.bringyour.sdk.StringList

fun authTypesContains(authTypes: StringList?, method: String): Boolean {
    return sdkStringListToList(authTypes).contains(method)
}

fun parseAuthMethods(networkUser: NetworkUser): List<String> {
    val fromAuthTypes = sdkStringListToList(networkUser.authTypes).filter { it.isNotEmpty() }
    if (fromAuthTypes.isNotEmpty()) {
        return fromAuthTypes
    }

    // Fallback for old server: read single authType + userAuth
    val methods = mutableListOf<String>()
    if (networkUser.authType.isNotEmpty()) {
        methods.add(networkUser.authType)
    }
    val userAuth = networkUser.userAuth
    if (userAuth.isNotEmpty()) {
        val authType = if (userAuth.contains("@")) "email" else "phone"
        if (!methods.contains(authType)) {
            methods.add(authType)
        }
    }

    return methods
}

/** A sign-in method's name as the settings list shows it; Google and Apple are names everywhere. */
@Composable
fun methodDisplayName(method: String): String {
    return when (method) {
        "email" -> stringResource(id = R.string.site_app_email)
        "phone" -> stringResource(id = R.string.site_app_phone)
        "google" -> "Google"
        "apple" -> "Apple"
        "solana" -> stringResource(id = R.string.solana_wallet)
        "seedphrase" -> stringResource(id = R.string.seedphrase)
        else -> method.replaceFirstChar { it.uppercase() }
    }
}

/**
 * An email or phone password sign-in of the account: the sdk's NetworkUserUserAuth, kept
 * sdk-free so it can be tested on the JVM.
 */
data class PasswordSignIn(
    val userAuth: String,
    // "email" or "phone"
    val authType: String,
)

/** The account's password sign-ins, the server's `user_auths`. */
fun passwordSignIns(networkUser: NetworkUser): List<PasswordSignIn> {
    val list = networkUser.userAuths ?: return emptyList()
    val signIns = mutableListOf<PasswordSignIn>()
    for (i in 0 until list.len()) {
        val userAuth = list.get(i) ?: continue
        signIns.add(PasswordSignIn(userAuth = userAuth.userAuth, authType = userAuth.authType))
    }
    return signIns
}

/**
 * The address the profile's Change password sends a reset link to, or null when the account
 * has no password sign-in to change.
 *
 * A reset rewrites the account's email and phone password sign-ins, `userAuths`, so the link
 * goes to one of them, an email before a phone. `userAuth` is only the identity the account was
 * created with: an account created with a wallet, a seed phrase or as a guest that added an
 * email and password later has none, so it was never offered the reset, and a Google or Apple
 * account has the provider's email, which has no password. It is read only when there are no
 * `userAuths` (an older sdk or server): then it has a password when `authTypes` holds an email
 * or phone, or, from an old server with no `authTypes`, when the auth type is `password`.
 */
fun passwordResetUserAuth(
    authTypes: List<String>,
    authType: String,
    userAuth: String?,
    userAuths: List<PasswordSignIn>,
): String? {
    for (passwordAuthType in listOf("email", "phone")) {
        userAuths.firstOrNull { it.authType == passwordAuthType && it.userAuth.isNotEmpty() }?.let {
            return it.userAuth
        }
    }
    if (userAuth.isNullOrEmpty()) {
        return null
    }
    val hasPassword = if (authTypes.any { it.isNotEmpty() }) {
        authTypes.contains("email") || authTypes.contains("phone")
    } else {
        authType == "password"
    }
    return if (hasPassword) userAuth else null
}
