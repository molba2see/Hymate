package com.example.hymate.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import com.example.hymate.HyViewModel
import com.example.hymate.VoiceUi
import com.example.hymate.data.DEST
import com.example.hymate.data.ROUTE_POINTS
import com.example.hymate.data.clockText
import com.example.hymate.data.nextManeuver
import com.example.hymate.data.posAt
import com.example.hymate.ui.components.GleoOrb
import com.example.hymate.ui.components.HIcon
import com.example.hymate.ui.components.HText
import com.example.hymate.ui.components.HyMark
import com.example.hymate.ui.components.VoiceWave
import com.example.hymate.ui.theme.HyIcons
import com.example.hymate.ui.theme.hy
import com.example.hymate.ui.theme.mix
import com.example.hymate.ui.theme.u
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 주행 · 정차 화면.
 * 주행 중 Hy! mate는 화면을 쓰지 않아요(OEM 내비가 전체). 여기 지도는 OEM 내비를 흉내 낸 목업이고,
 * Hy! mate가 실제로 그리는 건 하단 우측 상태 알약과 정차 중 상단 우측 Gleo 배너뿐이에요.
 * 배너는 실차에서는 AAOS heads-up 알림으로 띄우는 것을 기본안으로 둬요.
 */
@Composable
fun DriveScreen(vm: HyViewModel) {
    // progress는 매 프레임 바뀌어요. 화면 전체가 아니라 표시 값이 달라질 때만 리컴포즈되도록 파생 상태로 읽어요.
    val nmState by remember { derivedStateOf { nextManeuver(vm.progress) } }
    val distText by remember {
        derivedStateOf {
            val p = vm.progress
            val r = (nextManeuver(p)?.d ?: DEST) - p
            if (r < 1000) "${(r / 10).toInt() * 10}m" else "%.1fkm".format(Locale.US, r / 1000)
        }
    }
    val remKmText by remember { derivedStateOf { "%.1fkm".format(Locale.US, (DEST - vm.progress) / 1000f) } }
    val remMin by remember { derivedStateOf { kotlin.math.ceil((DEST - vm.progress) / 1000f / 28f * 60f).toInt() } }
    val context = LocalContext.current
    val debuggable = remember { (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 }
    Box(Modifier.fillMaxSize().background(hy.mapBg)) {
        NavMap({ vm.progress }, Modifier.fillMaxSize())
        if (vm.raining) RainOverlay()

        // TBT
        val nm = nmState
        if (nm != null) {
            Row(
                Modifier.padding(32.u).widthIn(max = 780.u)
                    .shadow(24.u, RoundedCornerShape(36.u))
                    .clip(RoundedCornerShape(36.u)).background(hy.blue)
                    .padding(start = 28.u, end = 40.u, top = 24.u, bottom = 24.u),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(26.u),
            ) {
                val ic = when {
                    nm.kind == "dest" -> HyIcons.Flag
                    nm.kind == "merge" -> HyIcons.Merge
                    nm.dir == "L" -> HyIcons.TurnL
                    else -> HyIcons.TurnR
                }
                HIcon(ic, 112, Color.White)
                Column {
                    HText(distText, 84, color = Color.White, weight = FontWeight.ExtraBold, lineHeight = 1f)
                    HText(nm.road.ifEmpty { nm.name }, 34, Modifier.padding(top = 8.u), color = Color.White.copy(alpha = 0.92f), maxLines = 1)
                }
            }
        }
        Box(
            Modifier.offset(32.u, 232.u).clip(RoundedCornerShape(12.u)).background(mix(hy.s1, 0.85f, Color.Transparent))
                .padding(horizontal = 16.u, vertical = 8.u),
        ) { HText("OEM 내비게이션 화면 · Hy! mate는 백그라운드", 22, color = hy.sub) }

        // ETA
        Row(
            Modifier.align(Alignment.BottomStart).padding(32.u)
                .shadow(12.u, RoundedCornerShape(32.u))
                .clip(RoundedCornerShape(32.u)).background(mix(hy.s1, 0.95f, Color.Transparent))
                .padding(horizontal = 40.u, vertical = 22.u),
            horizontalArrangement = Arrangement.spacedBy(52.u),
        ) {
            EtaItem(clockText(DEST / (28f / 3.6f)), "도착 예정")
            EtaItem(remKmText, "남은 거리")
            EtaItem("${remMin}분", "남은 시간")
        }

        // Hy! mate 상태 알약 — 화면 조작 없이 기록 중임을 알리는 최소 표시
        Row(
            Modifier.align(Alignment.BottomEnd).padding(32.u).height(96.u)
                .shadow(12.u, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50)).background(mix(hy.s1, 0.95f, Color.Transparent))
                .then(if (debuggable) Modifier.clickable(onClick = vm::skipToArrive) else Modifier) // 개발용(디버그 빌드만): 탭하면 도착 처리
                .padding(start = 16.u, end = 32.u),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.u),
        ) {
            HyMark(64, off = !vm.questStarted, stop = vm.questStarted && vm.stopped)
            HText(
                when {
                    !vm.questStarted -> "기록 안 함"
                    vm.stopped -> "정차 · 짧게 말할 수 있어요"
                    else -> "동승 중 · 화면 조작 없이 기록"
                },
                28, color = hy.sub,
            )
        }

        // Gleo 배너 (정차 중에만)
        AnimatedVisibility(
            visible = vm.voice != null,
            modifier = Modifier.align(Alignment.TopEnd).padding(32.u),
            enter = slideInVertically { -it / 4 } + fadeIn(),
            exit = fadeOut(),
        ) {
            val last = remember { mutableListOf<VoiceUi>() }
            vm.voice?.let { last.clear(); last += it }
            last.firstOrNull()?.let { GleoBanner(it, vm::answer) }
        }
    }
}

@Composable
private fun EtaItem(value: String, label: String) {
    Column {
        HText(value, 50, weight = FontWeight.Bold, lineHeight = 1.1f)
        HText(label, 26, color = hy.sub)
    }
}

@Composable
private fun GleoBanner(v: VoiceUi, onAnswer: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(36.u)
    Box(
        Modifier.width(980.u)
            .shadow(30.u, shape)
            .clip(shape)
            .background(hy.s1)
            .border(4.u, hy.gleo, shape),
    ) {
        Row(
            Modifier.padding(start = 20.u, end = 24.u, top = 20.u, bottom = 22.u),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(22.u),
        ) {
            GleoOrb(84) {
                HIcon(
                    when (v) { is VoiceUi.Feedback -> HyIcons.Speaker; is VoiceUi.Question -> HyIcons.Mic; is VoiceUi.Confirm -> HyIcons.Check },
                    44, Color.White,
                )
            }
            Column(Modifier.weight(1f)) {
                HText(
                    when (v) {
                        is VoiceUi.Feedback -> "Gleo · 실시간 피드백 · 음악 볼륨 낮춤"
                        is VoiceUi.Question -> if (v.listening) "Gleo · 듣는 중" else "Gleo"
                        is VoiceUi.Confirm -> "Gleo · 저장했어요"
                    },
                    24, color = hy.sub, weight = FontWeight.Bold,
                )
                HText(
                    when (v) { is VoiceUi.Feedback -> v.text; is VoiceUi.Question -> v.text; is VoiceUi.Confirm -> v.text },
                    38, weight = FontWeight.Bold, lineHeight = 1.25f,
                )
            }
            when {
                v is VoiceUi.Feedback -> VoiceWave()
                v is VoiceUi.Question && v.listening -> Row(horizontalArrangement = Arrangement.spacedBy(12.u)) {
                    AnswerButton("응") { onAnswer(true) }
                    AnswerButton("아니") { onAnswer(false) }
                }
            }
        }
        if (v is VoiceUi.Question && v.listening) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(v.remain).height(6.u).background(hy.gleo))
        }
    }
}

/** 음성 응답 시뮬레이션 버튼 (실차에서는 음성으로만 답해요) */
@Composable
private fun AnswerButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.u)
    Box(
        Modifier.height(88.u).widthIn(min = 128.u)
            .clip(shape)
            .border(3.u, mix(hy.gleo, 0.7f, Color.Transparent), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.u),
        contentAlignment = Alignment.Center,
    ) { HText("“$text”", 36, weight = FontWeight.Bold) }
}

private class Street(val a: Offset, val b: Offset, val w: Float)

/** OEM 내비게이션 목업 지도: 차량 기준으로 경로를 이동·회전 */
@Composable
private fun NavMap(progress: () -> Float, modifier: Modifier) {
    val c = hy
    // 경로 Path는 한 번만 만들고, 지나온 구간 Path는 재사용해요 (프레임마다 할당하지 않기)
    val routePath = remember {
        Path().apply { ROUTE_POINTS.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
    }
    val donePath = remember { Path() }
    val streets = remember {
        val r = Random(11)
        List(260) {
            val x = -260f + r.nextFloat() * 880f
            val y = -140f + r.nextFloat() * 720f
            val ang = listOf(0, 90, 28, 118, 62, 152)[r.nextInt(6)] * PI.toFloat() / 180f + (r.nextFloat() - .5f) * .2f
            val l = 15f + r.nextFloat() * 80f
            Street(Offset(x - cos(ang) * l, y - sin(ang) * l), Offset(x + cos(ang) * l, y + sin(ang) * l), if (r.nextFloat() < .25f) 5f else 2.5f)
        }
    }
    Canvas(modifier) {
        val d = progress()
        val s = size.width / 1700f
        val nk = 6f
        val car = Offset(size.width * 850f / 1700f, size.height * 0.68f)
        val p = posAt(d)
        val a = posAt((d + 30).coerceAtMost(DEST.toFloat()))
        val b = posAt((d - 10).coerceAtLeast(0f))
        val heading = atan2(a.y - b.y, a.x - b.x) * 180f / PI.toFloat() + 90f
        withTransform({
            translate(car.x, car.y)
            rotate(-heading, Offset.Zero)
            scale(nk * s, nk * s, Offset.Zero)
            translate(-p.x, -p.y)
        }) {
            streets.forEach { drawLine(c.mapStreet, it.a, it.b, it.w * 3f / nk, cap = StrokeCap.Round) }
            val path = routePath
            drawPath(path, c.mapCase, style = Stroke(26f / nk, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, c.blue, style = Stroke(16f / nk, cap = StrokeCap.Round, join = StrokeJoin.Round))
            donePath.rewind()
            donePath.moveTo(ROUTE_POINTS.first().x, ROUTE_POINTS.first().y)
            var i = 0
            while (i < ROUTE_POINTS.size && i * 60 <= d) {
                donePath.lineTo(ROUTE_POINTS[i].x, ROUTE_POINTS[i].y)
                i++
            }
            donePath.lineTo(p.x, p.y)
            drawPath(donePath, c.mapDone, style = Stroke(16f / nk, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val end = posAt(DEST.toFloat())
            drawCircle(c.bad, 4f, end)
            drawCircle(Color.White, 4f, end, style = Stroke(1.2f))
        }
        // 차량 아이콘 (항상 위쪽을 향함)
        translate(car.x, car.y) {
            drawCircle(c.blue.copy(alpha = .16f), 72f * s)
            val arrow = Path().apply {
                moveTo(0f, -44f * s); lineTo(31f * s, 34f * s); lineTo(0f, 18f * s); lineTo(-31f * s, 34f * s); close()
            }
            drawPath(arrow, c.blue)
            drawPath(arrow, Color.White, style = Stroke(6f * s, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun BoxScope.RainOverlay() {
    val t by rememberInfiniteTransition(label = "rain").animateFloat(0f, 1f, infiniteRepeatable(tween(450, easing = LinearEasing)), label = "t")
    Canvas(Modifier.matchParentSize()) {
        val gap = 60f * size.width / 1700f
        val dx = size.height * 0.27f // 105° 기울기
        var x = -dx - gap + t * gap
        val col = Color(0xFFAAC8FF).copy(alpha = 0.16f)
        while (x < size.width + dx) {
            drawLine(col, Offset(x + dx, -size.height * t * 0.05f), Offset(x, size.height), 2f * size.width / 1700f)
            x += gap
        }
    }
}
