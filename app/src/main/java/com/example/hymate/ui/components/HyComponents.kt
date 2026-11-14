package com.example.hymate.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import com.example.hymate.ui.theme.BrandBlue
import com.example.hymate.ui.theme.BrandGreen
import com.example.hymate.ui.theme.BrandViolet
import com.example.hymate.ui.theme.DisplayFont
import com.example.hymate.ui.theme.OnOk
import com.example.hymate.ui.theme.fs
import com.example.hymate.ui.theme.hy
import com.example.hymate.ui.theme.u

/** 디자인 px 기준 텍스트 */
@Composable
fun HText(
    text: String,
    size: Number,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    weight: FontWeight = FontWeight.Normal,
    lineHeight: Float = 1.3f,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
    family: FontFamily? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size.fs,
        fontWeight = weight,
        lineHeight = lineHeight.em,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
        fontFamily = family,
    )
}

@Composable
fun Eyebrow(text: String, color: Color = hy.ok) = HText(text, 30, color = color, weight = FontWeight.SemiBold)

/** 아케이드 HUD 라벨 색 (민트). 밝은 테마에서는 읽히도록 어둡게. */
val arcadeMint: Color
    @Composable get() = if (hy.isDark) Color(0xFF83E8C6) else Color(0xFF0B7A62)

/** 아케이드풍 섹션 라벨: "◆ 01 / PRE-DRIVE" — 모노스페이스 · 대문자 · 넓은 자간 */
@Composable
fun ArcadeTag(text: String, modifier: Modifier = Modifier, size: Int = 22, color: Color = arcadeMint) {
    Text(
        text = "◆ $text",
        modifier = modifier,
        color = color,
        fontSize = size.fs,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 0.14.em,
        lineHeight = 1.2.em,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 아케이드풍 픽셀 게이지: [base]까지는 기존 값, [gained]까지는 이번에 얻은 값 (0..1) */
@Composable
fun PixelGauge(base: Float, gained: Float, modifier: Modifier = Modifier, cells: Int = 28, height: Int = 20) {
    val c = hy
    val mint = arcadeMint
    Box(
        modifier.height(height.u).drawBehind {
            val gap = size.height * 0.22f
            val w = (size.width - gap * (cells - 1)) / cells
            for (i in 0 until cells) {
                val mid = (i + 0.5f) / cells
                val col = when {
                    mid <= base -> c.blue
                    mid <= gained -> mint
                    else -> c.s2
                }
                drawRect(col, Offset(i * (w + gap), 0f), androidx.compose.ui.geometry.Size(w, size.height))
            }
        },
    )
}

@Composable
fun H1(text: String, size: Int = 56, modifier: Modifier = Modifier) =
    HText(text, size, modifier, weight = FontWeight.Bold, lineHeight = 1.2f, maxLines = 2)

@Composable
fun HIcon(icon: ImageVector, size: Number, tint: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    Icon(icon, null, modifier.size(size.u), tint = if (tint == Color.Unspecified) hy.fg else tint)
}

@Composable
fun HCard(
    modifier: Modifier = Modifier,
    color: Color = hy.s1,
    radius: Int = 32,
    padding: PaddingValues = PaddingValues(36.u),
    borderColor: Color? = null,
    borderWidth: Int = 3,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius.u)
    Column(
        modifier
            .clip(shape)
            .background(color)
            .then(if (borderColor != null) Modifier.border(borderWidth.u, borderColor, shape) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** .i-chip */
@Composable
fun HChip(
    text: String,
    icon: ImageVector? = null,
    color: Color = hy.fg,
    bg: Color = hy.s2,
    height: Int = 64,
    textSize: Int = 30,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.height(height.u).clip(RoundedCornerShape(20.u)).background(bg).padding(horizontal = 24.u),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.u),
    ) {
        leading?.invoke()
        if (icon != null) HIcon(icon, 36, color)
        HText(text, textSize, color = color, weight = FontWeight.Medium, maxLines = 1)
    }
}

enum class BtnKind { Default, Primary, Ghost }

/** .i-btn — 터치 영역 최소 80px 이상 */
@Composable
fun HButton(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: ImageVector? = null,
    kind: BtnKind = BtnKind.Default,
    height: Int = 112,
    textSize: Int = 38,
    radius: Int = 32,
    hPad: Int = 40,
    container: Color? = null,
    content: Color? = null,
) {
    val bg = container ?: when (kind) { BtnKind.Primary -> hy.ok; BtnKind.Ghost -> Color.Transparent; BtnKind.Default -> hy.s2 }
    val fg = content ?: when (kind) { BtnKind.Primary -> OnOk; BtnKind.Ghost -> hy.sub; BtnKind.Default -> hy.fg }
    Row(
        modifier
            .heightIn(min = height.u)
            .height(height.u)
            .clip(RoundedCornerShape(radius.u))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = hPad.u),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.u, Alignment.CenterHorizontally),
    ) {
        if (icon != null) HIcon(icon, 48, fg)
        if (text != null) HText(text, textSize, color = fg, weight = if (kind == BtnKind.Primary) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
        if (trailing != null) HIcon(trailing, 40, fg)
    }
}

@Composable
fun SquareButton(icon: ImageVector, onClick: () -> Unit, size: Int = 112, radius: Int = 32) {
    Box(
        Modifier.size(size.u).clip(RoundedCornerShape(radius.u)).background(hy.s2).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { HIcon(icon, size * 0.43f) }
}

/** .i-seg — 큰 세그먼트 컨트롤 */
@Composable
fun HSeg(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    gleoValue: String? = "ask",
) {
    Row(
        modifier.clip(RoundedCornerShape(28.u)).background(hy.s2).padding(8.u),
        horizontalArrangement = Arrangement.spacedBy(8.u),
    ) {
        options.forEach { (v, label) ->
            val on = v == selected
            val g = on && v == gleoValue
            Box(
                Modifier
                    .weight(1f)
                    .height(96.u)
                    .clip(RoundedCornerShape(22.u))
                    .background(if (g) hy.gleo else if (on) hy.s1 else Color.Transparent)
                    .clickable { onSelect(v) },
                contentAlignment = Alignment.Center,
            ) {
                HText(label, 32, color = if (g) Color.White else if (on) hy.fg else hy.sub, weight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

/** 지킴 O / 놓침 ✕ / 기록 / 보류 */
@Composable
fun OxMark(res: Char, small: Boolean = true) {
    val s = if (small) 34 else 44
    val c = hy
    when (res) {
        'S' -> Box(Modifier.size(s.u).drawBehind {
            val w = (if (small) 4 else 5) * (size.width / s)
            drawCircle(c.ok, radius = size.minDimension / 2 - w / 2, style = Stroke(w))
        })
        else -> {
            val (bg, fg, t) = when (res) {
                'F' -> Triple(c.bad, Color.White, "✕")
                'R' -> Triple(c.blue, Color.White, "기")
                'E' -> Triple(c.gleo, Color.White, "경") // 경험형
                'X', 'N' -> Triple(c.s2, c.sub, "–") // 이의 반영 · 해당 없음 → 판정 제외
                else -> Triple(c.amber, Color(0xFF111111), "?")
            }
            Box(Modifier.size(s.u).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                HText(t, if (small) 20 else 26, color = fg, weight = FontWeight.Black, lineHeight = 1f)
            }
        }
    }
}

/** 브랜드 Hy 마크 (conic gradient) */
@Composable
fun HyMark(size: Int, off: Boolean = false, stop: Boolean = false, round: Boolean = true) {
    val brush = when {
        off -> Brush.sweepGradient(listOf(hy.s2, hy.s2))
        stop -> Brush.sweepGradient(listOf(Color(0xFFFFB020), Color(0xFFFF7A3D), Color(0xFFFFB020)))
        else -> Brush.sweepGradient(listOf(BrandGreen, BrandBlue, BrandViolet, BrandGreen))
    }
    Box(
        Modifier.size(size.u).clip(if (round) CircleShape else RoundedCornerShape((size * 0.31f).u)).background(brush),
        contentAlignment = Alignment.Center,
    ) {
        HText("Hy", size * 0.34f, color = if (off) hy.sub else Color.White, weight = FontWeight.Black, family = DisplayFont, lineHeight = 1f)
    }
}

/** Gleo 음성 아이콘 (보라 구체, 숨쉬기 애니메이션) */
@Composable
fun GleoOrb(size: Int, breathe: Boolean = true, content: @Composable BoxScope.() -> Unit = {}) {
    val t = rememberInfiniteTransition(label = "orb")
    val s by t.animateFloat(1f, if (breathe) 0.94f else 1f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
    Box(
        Modifier
            .size(size.u)
            .scale(s)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    0f to Color(0xFFB3A8FF), 0.55f to Color(0xFF6A57FF), 1f to Color(0xFF2B1FA0),
                    center = Offset.Unspecified,
                ),
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** 음성 파형 */
@Composable
fun VoiceWave(color: Color = hy.amber) {
    val t = rememberInfiniteTransition(label = "wave")
    Row(Modifier.height(40.u), horizontalArrangement = Arrangement.spacedBy(8.u), verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { i ->
            val h by t.animateFloat(
                10f, 40f,
                infiniteRepeatable(tween(450, delayMillis = 0, easing = FastOutSlowInEasing), RepeatMode.Reverse, initialStartOffset = androidx.compose.animation.core.StartOffset(i * 150)),
                label = "h$i",
            )
            Box(Modifier.width(8.u).height(h.u).clip(RoundedCornerShape(6.u)).background(color))
        }
    }
}

/** 회전하는 원형 레이 (라이선스 발급 배경) */
@Composable
fun SpinningRays(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "rays")
    val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(30000, easing = LinearEasing)), label = "a")
    Box(
        modifier.rotate(a).drawBehind {
            val r = size.maxDimension
            val brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.08f), Color.Transparent), radius = r * 0.55f)
            var deg = 0f
            while (deg < 360f) {
                drawArc(brush, deg, 6f, useCenter = true, topLeft = Offset(center.x - r, center.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2))
                deg += 18f
            }
        },
    )
}

@Composable
fun RowScope.Spacer1() = androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))

@Composable
fun ColumnScope.VSpacer1() = androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))

@Composable
fun Divider2(color: Color = hy.s2) = Box(Modifier.fillMaxHeight().width(2.u).background(color))

@Suppress("unused")
fun Dp.half() = this / 2
