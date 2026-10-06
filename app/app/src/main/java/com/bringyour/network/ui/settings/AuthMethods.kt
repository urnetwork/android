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
