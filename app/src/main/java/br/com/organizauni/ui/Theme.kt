package br.com.organizauni.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.organizauni.R

val Cobalt = Color(0xFF3155D9)
val PaleBlue = Color(0xFFEBF0FF)
val Paper = Color(0xFFF5F7FB)
val Ink = Color(0xFF17233B)
val Midnight = Color(0xFF152447)
val Amber = Color(0xFF946200)

private val Barlow = FontFamily(
    Font(R.font.barlow_regular),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_semibold, FontWeight.Bold),
)

@Composable
fun OrganizaUniTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Cobalt,
            onPrimary = Color.White,
            primaryContainer = PaleBlue,
            onPrimaryContainer = Cobalt,
            secondary = Amber,
            secondaryContainer = Color(0xFFFFEDC6),
            background = Paper,
            onBackground = Ink,
            surface = Color.White,
            onSurface = Ink,
            surfaceVariant = Color(0xFFEEF1F7),
            onSurfaceVariant = Color(0xFF66718A),
            outline = Color(0xFFBBC4D6),
            outlineVariant = Color(0xFFDFE5EF),
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = Barlow, fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.SemiBold),
            headlineMedium = TextStyle(fontFamily = Barlow, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
            titleLarge = TextStyle(fontFamily = Barlow, fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontFamily = Barlow, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontFamily = Barlow, fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontFamily = Barlow, fontSize = 14.sp, lineHeight = 21.sp),
            bodySmall = TextStyle(fontFamily = Barlow, fontSize = 12.sp, lineHeight = 17.sp),
            labelLarge = TextStyle(fontFamily = Barlow, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            labelMedium = TextStyle(fontFamily = Barlow, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
            labelSmall = TextStyle(fontFamily = Barlow, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold),
        ),
        shapes = Shapes(small = RoundedCornerShape(6.dp), medium = RoundedCornerShape(10.dp), large = RoundedCornerShape(14.dp)),
        content = content,
    )
}
