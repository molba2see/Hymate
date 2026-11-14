package com.example.hymate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pleos Connect 디스플레이 가이드 토큰.
 * 앱 영역 기준 해상도는 1700×1184(17" Connect-S). 모든 치수는 이 "디자인 px" 단위로 적고
 * [u]/[fs] 확장으로 실제 창 크기에 맞게 비례 변환해요.
 */
@Immutable
data class HyColors(
    val app: Color,
    val s1: Color,
    val s2: Color,
    val line: Color,
    val fg: Color,
    val sub: Color,
    val ok: Color,
    val blue: Color,
    val bad: Color,
    val gleo: Color,
    val amber: Color,
    val mapBg: Color,
    val mapStreet: Color,
    val mapRiver: Color,
    val mapCase: Color,
    val mapDone: Color,
    val mapLabel: Color,
    val isDark: Boolean,
)

val DarkHy = HyColors(
    app = Color(0xFF1D1E21), s1 = Color(0xFF26272B), s2 = Color(0xFF313236), line = Color(0xFF44464E),
    fg = Color.White, sub = Color(0xFFA3A6AE), ok = Color(0xFF00E074), blue = Color(0xFF0082FF),
    bad = Color(0xFFFF4C28), gleo = Color(0xFF6A57FF), amber = Color(0xFFFFB020),
    mapBg = Color(0xFF16181D), mapStreet = Color(0xFF2A2D35), mapRiver = Color(0xFF123049),
    mapCase = Color(0xFF07080A), mapDone = Color(0xFF3C414D), mapLabel = Color(0xFF8A90A0), isDark = true,
)

val LightHy = HyColors(
    app = Color(0xFFF7F8FA), s1 = Color.White, s2 = Color(0xFFEDEEF2), line = Color(0xFFE0E1E6),
    fg = Color(0xFF131417), sub = Color(0xFF5F626B), ok = Color(0xFF02C265), blue = Color(0xFF0064FF),
    bad = Color(0xFFFE3D16), gleo = Color(0xFF5A46FA), amber = Color(0xFFD98400),
    mapBg = Color(0xFFECEFF3), mapStreet = Color.White, mapRiver = Color(0xFFB9D5EE),
    mapCase = Color(0xFFC3C8D2), mapDone = Color(0xFFA9AFBA), mapLabel = Color(0xFF6A707C), isDark = false,
)

/** 브랜드 그라디언트 (active · positive · Gleo) */
val BrandGreen = Color(0xFF00E074)
val BrandBlue = Color(0xFF0082FF)
val BrandViolet = Color(0xFF6A57FF)
val OnOk = Color(0xFF05160C)

/** 디스플레이 서체(Unbounded) 자리. 폰트 리소스를 추가하면 여기만 바꾸면 돼요. */
val DisplayFont: FontFamily = FontFamily.SansSerif
val MonoFont: FontFamily = FontFamily.Monospace

val LocalHy = staticCompositionLocalOf { DarkHy }
val LocalU = staticCompositionLocalOf { 0.5.dp }

val hy: HyColors
    @Composable @ReadOnlyComposable get() = LocalHy.current

/** 디자인 px → Dp */
val Number.u: Dp
    @Composable @ReadOnlyComposable get() = LocalU.current * toFloat()

/** 디자인 px → sp (시스템 글꼴 배율은 차량 가이드 크기 유지를 위해 무시) */
val Number.fs: TextUnit
    @Composable @ReadOnlyComposable get() = (LocalU.current.value * toFloat() / LocalDensity.current.fontScale).sp

/** CSS color-mix(in srgb, a pct%, b) */
fun mix(a: Color, pct: Float, b: Color): Color = lerp(b, a, pct)

const val DESIGN_W = 1700f
const val DESIGN_H = 1184f

@Composable
fun HyTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) DarkHy else LightHy
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val unit = minOf(maxWidth / DESIGN_W, maxHeight / DESIGN_H)
        MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            CompositionLocalProvider(
                LocalHy provides colors,
                LocalU provides unit,
                LocalContentColor provides colors.fg,
            ) { content() }
        }
    }
}
