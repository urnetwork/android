package com.bringyour.network.ui.shared.models

enum class BundleStore {
    PLAY,
    SOLANA_DAPP;

    companion object {
        fun fromString(value: String): BundleStore? {
            return when (value.uppercase()) {
                "PLAY" -> PLAY
                "SOLANA_DAPP" -> SOLANA_DAPP
                else -> null
            }
        }
    }
}