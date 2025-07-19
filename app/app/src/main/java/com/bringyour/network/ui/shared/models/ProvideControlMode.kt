package com.bringyour.network.ui.shared.models

import com.bringyour.network.R

enum class ProvideControlMode {
    AUTO,
    ALWAYS,
    NEVER;

    companion object {
        fun fromString(value: String): ProvideControlMode? {
            return when (value.lowercase()) {
                "auto" -> AUTO
                "always" -> ALWAYS
                "never" -> NEVER
                else -> null
            }
        }

        fun toString(value: ProvideControlMode): String {
            return when (value) {
                AUTO -> "auto"
                ALWAYS -> "always"
                NEVER -> "never"
            }
        }

        fun toStringResourceId(value: ProvideControlMode): Int {
            return when (value) {
                AUTO -> R.string.auto
                ALWAYS -> R.string.always
                NEVER -> R.string.never
            }
        }
    }
}