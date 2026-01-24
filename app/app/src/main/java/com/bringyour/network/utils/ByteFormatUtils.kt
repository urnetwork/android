package com.bringyour.network.utils

val formatBalanceBytes: (Long) -> String = { bytes ->
    val oneTiB = 1024L * 1024 * 1024 * 1024
    val oneGiB = 1024L * 1024 * 1024
    when {
        bytes >= oneTiB -> String.format("%.2f TiB", bytes.toDouble() / oneTiB)
        else -> String.format("%.2f GiB", bytes.toDouble() / oneGiB)
    }
}