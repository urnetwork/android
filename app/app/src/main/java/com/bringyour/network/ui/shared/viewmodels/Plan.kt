package com.bringyour.network.ui.shared.viewmodels


enum class Plan {
    Basic,
    Supporter;

    companion object {
        fun fromString(value: String): Plan {
            return when (value.uppercase()) {
                "SUPPORTER" -> Supporter
                else -> Basic
            }
        }
    }
}
