package com.bringyour.network.ui.settings

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
        val methodLabel = if (userAuth.contains("@")) "email" else "phone"
        if (!methods.contains(methodLabel)) {
            methods.add(methodLabel)
        }
    }

    return methods
}

fun methodDisplayName(method: String): String {
    return when (method) {
        "email" -> "Email"
        "phone" -> "Phone"
        "google" -> "Google"
        "apple" -> "Apple"
        "solana" -> "Solana Wallet"
        "seedphrase" -> "Seedphrase"
        else -> method.replaceFirstChar { it.uppercase() }
    }
}
