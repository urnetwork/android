package com.bringyour.network.utils

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.pow

fun formatDecimalString(number: Double, decimals: Int): String {
    val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())
    numberFormat.maximumFractionDigits = decimals
    numberFormat.minimumFractionDigits = decimals
    val localizedString = numberFormat.format(number)
    return localizedString
}

fun Double.roundToDecimals(decimals: Int): Double {
    val factor = 10.0.pow(decimals.toDouble())
    return Math.round(this * factor) / factor
}

fun formatUnpaidByteCount(ubc: Double): String {
    return if (ubc >= 1_000_000_000) { // 1 GB = 1,000,000,000 bytes
        val gb = ubc / 1_000_000_000
        "${formatDecimalString(gb, 2)} GB"
    } else {
        val mb = ubc / 1_000_000
        "${formatDecimalString(mb, 2)} MB"
    }
}