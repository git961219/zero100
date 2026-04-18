package com.ableLabs.zero100.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// 레이싱 감성 컬러
val RacingRed = Color(0xFFE53935)
val RacingGreen = Color(0xFF43A047)
val RacingYellow = Color(0xFFFFB300)
val DarkBg = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkCard = Color(0xFF2A2A2A)
val SpeedWhite = Color(0xFFFAFAFA)
val SpeedGray = Color(0xFF9E9E9E)

private val DarkColorScheme = darkColorScheme(
    primary = RacingRed,
    secondary = RacingGreen,
    tertiary = RacingYellow,
    background = DarkBg,
    surface = DarkSurface,
    surfaceVariant = DarkCard,
    onBackground = SpeedWhite,
    onSurface = SpeedWhite,
    onSurfaceVariant = SpeedGray,
)

// 속도 대형 표시용 텍스트 스타일
val SpeedDisplayStyle = TextStyle(
    fontSize = 96.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-2).sp
)

val SpeedUnitStyle = TextStyle(
    fontSize = 24.sp,
    fontWeight = FontWeight.Normal,
    color = SpeedGray
)

val TimerDisplayStyle = TextStyle(
    fontSize = 64.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-1).sp
)

@Composable
fun Zero100Theme(content: @Composable () -> Unit) {
    val colorScheme = DarkColorScheme // 항상 다크 모드 (운전 중 눈부심 방지)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DarkBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
