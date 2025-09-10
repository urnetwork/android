package com.bringyour.network.utils

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.sql.Time
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun todayFormatted(): String {
    val today = LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("MMM d")
    return today.format(formatter)
}

@SuppressLint("LocalContextConfigurationRead")
@Composable
fun formatDateLocalized(date: LocalDateTime): String {
    val context = LocalContext.current
    val locale = context.resources.configuration.locales[0]
    val formatter = DateTimeFormatter.ofPattern("MMM d", locale)
    return date.format(formatter)
}