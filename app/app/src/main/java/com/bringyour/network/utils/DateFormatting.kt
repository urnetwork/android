package com.bringyour.network.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun todayFormatted(): String {
    val today = LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("MMM d")
    return today.format(formatter)
}