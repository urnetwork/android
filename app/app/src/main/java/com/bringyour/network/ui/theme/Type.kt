package com.bringyour.network.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bringyour.network.R

val gravityFamily = FontFamily(
    Font(R.font.abcgravity_extended, FontWeight.Normal),
)

val gravityCondensedFamily = FontFamily(
    Font(R.font.abcgravity_extra_condensed, FontWeight.Normal),
)

val ppNeueMontreal = FontFamily(
    Font(R.font.pp_neue_montreal_regular, FontWeight.Normal),
)

val ppNeueBitBold = FontFamily(
    Font(R.font.pp_neue_bit_bold, FontWeight.Bold),
)

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = ppNeueMontreal,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),

    bodyMedium = TextStyle(
        fontFamily = ppNeueMontreal,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
        // fontSize = 16.sp,
        // lineHeight = 24.sp,
        // letterSpacing = 0.5.sp
    ),

    headlineLarge = TextStyle(
        fontFamily = gravityFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),

    headlineMedium = TextStyle(
        fontFamily = gravityCondensedFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),



    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)
