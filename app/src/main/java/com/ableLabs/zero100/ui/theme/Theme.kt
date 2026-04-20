package com.ableLabs.zero100.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView

// ── Zero100 커스텀 컬러 시스템 ──
// 브랜드: 레드 (#E53935)
// 보조: 시안 (#00E5FF) — GPS 상태/정보 전용

data class Zero100Colors(
    val background: Color,
    val card: Color,
    val surface: Color,
    val accent: Color,       // 브랜드 레드 — 버튼, 강조, 선택
    val info: Color,         // 시안 — GPS 정상, 정보 표시, 타이머
    val success: Color,      // 녹색 — GPS 연결 상태
    val warning: Color,      // 앰버 — 대기/경고
    val danger: Color,       // 빨강 진한 — 오류/삭제 (accent보다 진함)
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
)

val DarkZero100Colors = Zero100Colors(
    background = Color(0xFF0A0A0A),
    card = Color(0xFF111111),
    surface = Color(0xFF1A1A1A),
    accent = Color(0xFFE53935),      // 레드 — 브랜드, 버튼
    info = Color(0xFF66BB6A),        // 초록 — 타이머/그래프/정보
    success = Color(0xFF43A047),     // 녹색 — 연결 OK
    warning = Color(0xFFFFB300),     // 앰버 — 대기
    danger = Color(0xFFB71C1C),      // 진한 빨강 — 오류/삭제
    textPrimary = Color(0xFFFAFAFA),
    textSecondary = Color(0xFFB0B0B0),
    textTertiary = Color(0xFF666666),
)

val LightZero100Colors = Zero100Colors(
    background = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    surface = Color(0xFFF5F5F5),
    accent = Color(0xFFD32F2F),      // 레드 — 브랜드 (라이트에서 약간 진하게)
    info = Color(0xFF2E7D32),        // 진한 초록 — 라이트에서 가독성 확보
    success = Color(0xFF2E7D32),     // 녹색 진한
    warning = Color(0xFFE65100),     // 주황 진한
    danger = Color(0xFFB71C1C),      // 진한 빨강
    textPrimary = Color(0xFF212121),
    textSecondary = Color(0xFF757575),
    textTertiary = Color(0xFFBDBDBD),
)

val LocalZero100Colors = staticCompositionLocalOf { DarkZero100Colors }

// ── 기존 top-level 변수 (호환용) ──
val PrecisionDark = Color(0xFF0A0A0A)
val PrecisionCard = Color(0xFF111111)
val PrecisionSurface = Color(0xFF1A1A1A)
val CyanAccent = Color(0xFF00E5FF)
val GreenAccent = Color(0xFF43A047)
val AmberAccent = Color(0xFFFFB300)
val RedAccent = Color(0xFFE53935)
val TextPrimary = Color(0xFFFAFAFA)
val TextSecondary = Color(0xFFB0B0B0)
val TextTertiary = Color(0xFF666666)

val RacingRed = RedAccent
val RacingGreen = GreenAccent
val RacingYellow = AmberAccent
val DarkBg = PrecisionDark
val DarkSurface = PrecisionSurface
val DarkCard = PrecisionCard
val SpeedWhite = TextPrimary
val SpeedGray = TextSecondary

// ── Material ColorScheme ──

private val DarkColorScheme = darkColorScheme(
    primary = RedAccent,
    secondary = CyanAccent,
    tertiary = AmberAccent,
    background = PrecisionDark,
    surface = PrecisionSurface,
    surfaceVariant = PrecisionCard,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFD32F2F),
    secondary = Color(0xFF00838F),
    tertiary = Color(0xFF2E7D32),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFFFFFFFF),
    onBackground = Color(0xFF212121),
    onSurface = Color(0xFF212121),
    onSurfaceVariant = Color(0xFF757575),
)

// ── 계측기 스타일 텍스트 ──
val SpeedDisplayStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 120.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-3).sp
)

val SpeedUnitStyle = TextStyle(
    fontSize = 24.sp,
    fontWeight = FontWeight.Normal,
    color = TextSecondary
)

val TimerDisplayStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 80.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-2).sp
)

// ── 테마 모드 ──
enum class ThemeMode {
    DARK, LIGHT, SYSTEM
}

@Composable
fun Zero100Theme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val zero100Colors = if (darkTheme) DarkZero100Colors else LightZero100Colors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalZero100Colors provides zero100Colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content
        )
    }
}
