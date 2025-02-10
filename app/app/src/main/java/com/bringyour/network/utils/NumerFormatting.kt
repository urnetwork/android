package com.bringyour.network.utils

import java.text.NumberFormat
import java.util.Locale

fun formatDecimalString(number: Double, decimals: Int): String {
    val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())
    numberFormat.maximumFractionDigits = decimals
    numberFormat.minimumFractionDigits = decimals
    val localizedString = numberFormat.format(number)
    return localizedString
}

fun Double.roundToDecimals(decimals: Int): Double {
    val factor = Math.pow(10.0, decimals.toDouble())
    return Math.round(this * factor) / factor
}