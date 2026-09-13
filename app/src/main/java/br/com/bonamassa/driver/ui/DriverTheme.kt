package br.com.bonamassa.driver.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Brand {
    val Background = Color(0xFF101010)
    val Surface = Color(0xFF1C1B1A)
    val Raised = Color(0xFF272523)
    val Border = Color(0xFF3B3633)
    val Red = Color(0xFFFF5750)
    val Button = Color(0xFFAE2B27)
    val Gold = Color(0xFFF1C477)
    val Cream = Color(0xFFFFF6E8)
    val Muted = Color(0xFFBCB5AC)
    val Green = Color(0xFFB2D6A4)
}

@Composable
fun DriverTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Brand.Red, onPrimary = Brand.Background,
            primaryContainer = Brand.Button, onPrimaryContainer = Brand.Cream,
            secondary = Brand.Gold, onSecondary = Brand.Background,
            background = Brand.Background, onBackground = Brand.Cream,
            surface = Brand.Surface, onSurface = Brand.Cream,
            surfaceVariant = Brand.Raised, onSurfaceVariant = Brand.Muted,
            outline = Brand.Border, error = Color(0xFFFFB4AB)
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
            titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 23.sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
            labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
            labelSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 15.sp, letterSpacing = 1.sp)
        ), content = content
    )
}

fun money(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(java.math.BigDecimal.valueOf(cents, 2))
fun timestamp(time: Long): String = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("dd/MM · HH:mm", Locale.forLanguageTag("pt-BR")))
