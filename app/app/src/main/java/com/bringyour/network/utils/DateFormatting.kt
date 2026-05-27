package com.bringyour.network.utils

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val todayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

fun todayFormatted(): String {
    val today = LocalDate.now()
    return today.format(todayFormatter)
}

@SuppressLint("LocalContextConfigurationRead")
@Composable
fun formatDateLocalized(date: LocalDateTime): String {
    val context = LocalContext.current
    val locale = context.resources.configuration.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("MMM d", locale) }
    return date.format(formatter)
}