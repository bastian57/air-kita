package com.daftech.airkita.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AirKitaTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 0.2.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 11.sp, color = Muted)
)

val MonoReceipt = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
