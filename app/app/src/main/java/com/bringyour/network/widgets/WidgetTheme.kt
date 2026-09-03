package com.bringyour.network.widgets

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp
import androidx.glance.color.ColorProvider
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.OffBlack
import com.bringyour.network.ui.theme.OffWhite
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted

/**
 * The app's palette for the widgets (the app is dark-only, so the widgets
 * are too, like the iOS ones). Glance wants ColorProviders; the bitmap
 * renderers want ARGB ints.
 */
object WidgetTheme {

    val background: ColorProvider = ColorProvider(Black)
    val card: ColorProvider = ColorProvider(OffBlack)
    val text: ColorProvider = ColorProvider(OffWhite)
    val textMuted: ColorProvider = ColorProvider(TextMuted)
    val textFaint: ColorProvider = ColorProvider(TextFaint)

    /** The quick connect button's tint when on (the app's accent pink). */
    val tint: ColorProvider = ColorProvider(Pink)
    /** The connected mark next to the location (the connect screen's green). */
    val connected: ColorProvider = ColorProvider(Green)

    /** Usage bar segments, as the app's UsageBar draws them. */
    val balanceUsed: ColorProvider = ColorProvider(BlueMedium)
    val balancePending: ColorProvider = ColorProvider(Red)
    val balanceAvailable: ColorProvider = ColorProvider(TextFaint)

    val balanceUsedArgb: Int = BlueMedium.toArgb()
    val balancePendingArgb: Int = Red.toArgb()
    val balanceAvailableArgb: Int = TextFaint.toArgb()

    /** The throughput charts: bytes in green, packets in pink, for the client and the provider alike (the app's TransferChart). */
    val byteSeriesArgb: Int = Green.toArgb()
    val packetSeriesArgb: Int = Pink.toArgb()
    val sendStackArgb: Int = Green.toArgb()
    val receiveStackArgb: Int = Pink.toArgb()

    val title = TextStyle(color = text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val body = TextStyle(color = text, fontSize = 13.sp)
    val caption = TextStyle(color = textMuted, fontSize = 11.sp)
    val label = TextStyle(color = textMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    val faint = TextStyle(color = textFaint, fontSize = 11.sp)
    val mono = TextStyle(color = text, fontSize = 11.sp, fontFamily = FontFamily.Monospace)

    fun withAlpha(color: Color, alpha: Float): ColorProvider = ColorProvider(color.copy(alpha = alpha))
}
