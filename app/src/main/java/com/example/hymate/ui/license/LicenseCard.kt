package com.example.hymate.ui.license

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.hymate.data.TIERS
import com.example.hymate.ui.theme.DisplayFont
import com.example.hymate.ui.theme.MonoFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** 라이선스 계열: Learner · Provisional · Full (단계적 운전면허 제도에서 따온 앱 안 성장 단계) */
private class LicPalette(
    val base: List<Pair<Float, Color>>,
    val ink: Color,
    val glow: Color,
    val foil: List<Color>,
    val foilAlpha: Float,
    val code: List<Color>,
)

private fun palette(fam: Char) = when (fam) {
    'P' -> LicPalette(
        listOf(0f to Color(0xFF171A47), .42f to Color(0xFF36268F), .78f to Color(0xFF0D5AA6), 1f to Color(0xFF1B1546)),
        Color(0xFFF2F0FF), Color(0x997864FF),
        listOf(Color(0xFFFF5FD2), Color(0xFF55E6FF), Color(0xFF9B7BFF), Color(0xFF7AFFB9)), .55f,
        listOf(Color.White, Color(0xFF9FE8FF), Color(0xFFFF9EF0)),
    )
    'F' -> LicPalette(
        listOf(0f to Color(0xFF0B0B0D), .45f to Color(0xFF1D1912), .75f to Color(0xFF2C2415), 1f to Color(0xFF0D0C0A)),
        Color(0xFFF5DC96), Color(0x80F0BE50),
        listOf(Color(0xFFFFD36B), Color(0xFFFFF1C4), Color(0xFFC98A2B), Color(0xFFFFE39A)), .4f,
        listOf(Color(0xFFFFF2C2), Color(0xFFE8B04B), Color(0xFFFFF8DE)),
    )
    else -> LicPalette(
        listOf(0f to Color(0xFFE9FFF8), .38f to Color(0xFFA7ECD9), .70f to Color(0xFFD7F4FF), 1f to Color(0xFFBFF1E3)),
        Color(0xFF0B3A32), Color(0x8C46DCB4),
        listOf(Color(0xFF7FF5D3), Color(0xFF7AC8FF), Color(0xFFE2A8FF), Color(0xFFFFF3A1)), .55f,
        listOf(Color(0xFF0B8F74), Color(0xFF2A6CFF), Color(0xFF14C7A0)),
    )
}

data class LicenseStats(val guides: Int, val drives: Int, val streak: Int, val date: String = "2026.11.04")

/**
 * Hy! mate 라이선스 카드.
 * - 드래그하면 3D 틸트 + 홀로 포일 + 글레어가 손가락을 따라가요.
 * - [idle]이면 천천히 흔들리고, [enter]면 뒤집히며 등장하고 ISSUED 도장이 찍혀요.
 */
@Composable
fun LicenseCard(
    code: String,
    width: Dp,
    stats: LicenseStats,
    modifier: Modifier = Modifier,
    name: String = "A씨",
    idle: Boolean = true,
    enter: Boolean = false,
) {
    val fam = code.first()
    val pal = remember(fam) { palette(fam) }
    val em = width / 26f
    val density = LocalDensity.current
    fun esp(x: Float): TextUnit = with(density) { (em * x).toSp() }

    // 포인터 상태 (0..1)
    var mx by remember { mutableStateOf(.5f) }
    var my by remember { mutableStateOf(.5f) }
    var dragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val release = remember { Animatable(0f) }

    val inf = rememberInfiniteTransition(label = "lic")
    val sway by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway")
    val sealA by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "seal")

    val enterP = remember { Animatable(if (enter) 0f else 1f) }
    val stamp = remember { Animatable(if (enter) 0f else 1f) }
    LaunchedEffect(enter) {
        if (enter) {
            launch { enterP.animateTo(1f, tween(1300, easing = CubicBezierEasing(.2f, .9f, .2f, 1.05f))) }
            delay(1100)
            stamp.animateTo(1f, tween(500, easing = CubicBezierEasing(.2f, 1.6f, .4f, 1f)))
        }
    }

    val swayMx = if (idle && !dragging) 0.25f + 0.5f * sway else mx
    val swayMy = if (idle && !dragging) 0.4f + 0.2f * sway else my
    val rx: Float
    val ry: Float
    if (dragging) {
        rx = (my - .5f) * -18f; ry = (mx - .5f) * 22f
    } else if (idle) {
        rx = 4f - 7f * sway; ry = -9f + 18f * sway
    } else {
        rx = (my - .5f) * -18f * (1 - release.value); ry = (mx - .5f) * 22f * (1 - release.value)
    }
    val px = swayMx
    val py = swayMy

    val shape = RoundedCornerShape(em * 1.15f)
    Box(
        modifier
            .size(width, width / 1.586f)
            .graphicsLayer {
                val p = enterP.value
                rotationX = rx
                rotationY = ry + 180f * (1 - p)
                scaleX = .4f + .6f * p
                scaleY = .4f + .6f * p
                alpha = (p / .6f).coerceAtMost(1f)
                cameraDistance = 16 * density.density * 60
            }
            .shadow(em * 1.4f, shape, ambientColor = pal.glow, spotColor = pal.glow)
            .clip(shape)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .background(Brush.linearGradient(*pal.base.toTypedArray(), start = Offset.Zero, end = Offset.Infinite))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { o -> dragging = true; mx = o.x / size.width; my = o.y / size.height },
                    onDragEnd = {
                        dragging = false
                        scope.launch { release.snapTo(0f); release.animateTo(1f, tween(250)) }
                    },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    mx = (change.position.x / size.width).coerceIn(0f, 1f)
                    my = (change.position.y / size.height).coerceIn(0f, 1f)
                }
            }
            .drawBehind {
                // pattern: 오른쪽 위 바깥을 중심으로 한 동심원
                val center = Offset(size.width * 1.1f, -size.height * .2f)
                val step = em.toPx() * .6f
                var r = step
                val maxR = size.width * 1.6f
                while (r < maxR) {
                    drawCircle(pal.ink.copy(alpha = .14f), r, center, style = Stroke(em.toPx() * .02f))
                    r += step
                }
            }
            .drawWithContent {
                drawContent()
                // foil: 115° 반복 무지개, 포인터 위치로 흐름
                val len = size.width * .7f
                val ang = Math.toRadians(115.0 - 90.0)
                val dir = Offset(cos(ang).toFloat(), sin(ang).toFloat())
                val shift = (px * 2.6f + py) * len
                val start = Offset(-shift * dir.x, -shift * dir.y)
                val foil = Brush.linearGradient(
                    pal.foil + pal.foil.first(), start = start, end = start + dir * len, tileMode = TileMode.Repeated,
                )
                drawRect(foil, alpha = pal.foilAlpha, blendMode = BlendMode.ColorDodge)
                // grain
                val g = em.toPx() * .9f
                var y = 0f
                while (y < size.height) {
                    var x = 0f
                    while (x < size.width) {
                        drawCircle(Color.White.copy(alpha = .3f), em.toPx() * .03f, Offset(x, y), blendMode = BlendMode.Overlay)
                        x += g
                    }
                    y += g
                }
                // glare
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = .65f), Color.White.copy(alpha = 0f)),
                        center = Offset(px * size.width, py * size.height), radius = size.maxDimension * .42f,
                    ),
                    blendMode = BlendMode.Softlight,
                )
                // 테두리 하이라이트
                drawRoundRect(
                    Color.White.copy(alpha = .35f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(em.toPx() * 1.15f),
                    style = Stroke(em.toPx() * .06f),
                )
            },
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = em * 1.4f, vertical = em * 1.3f)) {
            // 헤더
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Hy! mate", color = pal.ink, fontSize = esp(1.25f), fontWeight = FontWeight.Black, fontFamily = DisplayFont, lineHeight = 1.em)
                    Text(
                        "DRIVING COMPANION LICENSE", color = pal.ink.copy(alpha = .8f), fontSize = esp(1.25f * .36f),
                        fontFamily = MonoFont, fontWeight = FontWeight.SemiBold, letterSpacing = .22.em,
                        modifier = Modifier.padding(top = em * .45f),
                    )
                }
                Text(
                    code,
                    style = TextStyle(
                        brush = Brush.linearGradient(
                            listOf(pal.code[0], pal.code[1], pal.code[2], pal.code[0]),
                            start = Offset(-px * 400f, 0f), end = Offset(-px * 400f + 600f, 0f), tileMode = TileMode.Mirror,
                        ),
                        fontSize = esp(3.3f), fontWeight = FontWeight.Black, fontFamily = DisplayFont,
                        lineHeight = .85.em, letterSpacing = (-.04).em,
                    ),
                )
            }
            Spacer(Modifier.weight(1f))
            // 가운데: 칩 · 이름 · 씰
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(em)) {
                Box(
                    Modifier.size(em * 2.6f, em * 2f).clip(RoundedCornerShape(em * .4f))
                        .background(Brush.linearGradient(listOf(Color(0xFFF6E3A1), Color(0xFFC99A3B), Color(0xFFF3D98A), Color(0xFFA87A2A))))
                        .drawBehind {
                            val c = Color(0x733C2800)
                            val w = em.toPx() * .06f
                            drawRoundRect(c, Offset(em.toPx() * .5f, em.toPx() * .35f), androidx.compose.ui.geometry.Size(size.width - em.toPx(), size.height - em.toPx() * .7f), androidx.compose.ui.geometry.CornerRadius(em.toPx() * .2f), style = Stroke(w))
                            drawLine(c, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), w)
                        },
                )
                Column(Modifier.weight(1f)) {
                    Text(name, color = pal.ink, fontSize = esp(1.15f), fontWeight = FontWeight.Bold)
                    Text(
                        "디 올 뉴 아반떼 · " + when (fam) { 'L' -> "Learner 단계"; 'P' -> "Provisional 단계"; else -> "Full 단계" },
                        color = pal.ink.copy(alpha = .8f), fontSize = esp(.62f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    Modifier.size(em * 3.4f).rotate(sealA).clip(CircleShape)
                        .background(Brush.sweepGradient(listOf(Color(0xFFFF7AD9), Color(0xFF7AF0FF), Color(0xFF9DFF7A), Color(0xFFFFE27A), Color(0xFFFF7AD9))))
                        .graphicsLayer { alpha = .85f },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(em * 2.5f).rotate(-sealA).clip(CircleShape)
                            .background(Brush.linearGradient(*pal.base.toTypedArray())),
                        contentAlignment = Alignment.Center,
                    ) { Text("HY", color = pal.ink, fontSize = esp(.62f * 1.4f), fontWeight = FontWeight.Black, fontFamily = DisplayFont, letterSpacing = .05.em) }
                }
            }
            Spacer(Modifier.weight(1f))
            // 하단: 스탯 · 단계 트랙 · 마이크로 텍스트
            Column(verticalArrangement = Arrangement.spacedBy(em * .45f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(em * .36f)) {
                    listOf("발급" to stats.date, "익힌 가이드" to "${stats.guides}", "누적 주행" to "${stats.drives}회", "연속 기록" to "${stats.streak}일").forEach { (k, v) ->
                        Column(Modifier.weight(1f)) {
                            Text(k, color = pal.ink.copy(alpha = .7f), fontSize = esp(.6f * .85f), maxLines = 1)
                            Text(v, color = pal.ink, fontSize = esp(.6f * 1.25f), fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
                val idx = TIERS.indexOfFirst { it.first == code }
                Row(horizontalArrangement = Arrangement.spacedBy(em * .3f)) {
                    TIERS.forEachIndexed { i, _ ->
                        Box(
                            Modifier.weight(1f).height(em * .28f).clip(RoundedCornerShape(em * .2f))
                                .background(
                                    if (i <= idx) Brush.horizontalGradient(listOf(pal.code[0], pal.code[1]))
                                    else Brush.horizontalGradient(listOf(pal.ink.copy(alpha = .18f), pal.ink.copy(alpha = .18f))),
                                ),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    Text("앱 안 성장 단계이며 운전면허가 아닙니다", Modifier.weight(1f), color = pal.ink.copy(alpha = .7f), fontSize = esp(.42f), fontFamily = MonoFont, maxLines = 1, letterSpacing = .08.em)
                    Text("HM-26-1104-$code", color = pal.ink.copy(alpha = .7f), fontSize = esp(.42f), fontFamily = MonoFont, maxLines = 1, letterSpacing = .08.em)
                }
            }
        }
        if (enter && stamp.value > 0f) {
            val s = 3f - 2f * stamp.value
            val stampColor = if (fam == 'L') Color(0xFFFF4C5E) else Color(0xFFFF7A8A)
            Box(
                Modifier.align(Alignment.CenterEnd).offset(x = -em * 5.4f, y = -em * .6f)
                    .graphicsLayer { rotationZ = -14f; scaleX = s; scaleY = s; alpha = .9f * stamp.value }
                    .border(em * .14f, stampColor, RoundedCornerShape(em * .3f))
                    .padding(horizontal = em * .5f, vertical = em * .15f),
            ) {
                Text("ISSUED", color = stampColor, fontSize = esp(.9f), fontWeight = FontWeight.Black, fontFamily = DisplayFont, letterSpacing = .12.em)
            }
        }
    }
}
