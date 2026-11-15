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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import com.example.hymate.HyViewModel
import com.example.hymate.VoiceUi
import com.example.hymate.ui.components.RoadMap
import com.example.hymate.ui.components.GleoOrb
import com.example.hymate.ui.components.HButton
import com.example.hymate.ui.components.HIcon
import com.example.hymate.ui.components.HText
import com.example.hymate.ui.components.HyMark
import com.example.hymate.ui.components.VoiceWave
import com.example.hymate.ui.theme.HyIcons
import com.example.hymate.ui.theme.hy
import com.example.hymate.ui.theme.mix
import com.example.hymate.ui.theme.u
import java.util.Locale
import java.util.Date
import java.text.DateFormat
import kotlin.math.abs
import kotlin.math.roundToInt

/** 실제 도로 지도 + Hy! mate 상태·음성 배너. 데모 이동은 차량 차속을 사용해요. */
@Composable
fun DriveScreen(vm: HyViewModel, onRequestVehicleAccess: () -> Unit = {}) {
    val nmState by remember { derivedStateOf {
        if (vm.navigationReady) vm.roadRoute?.nextManeuver(vm.navigationProgress) else null
    } }
    val distText by remember { derivedStateOf {
        val remaining = ((nmState?.atMeters ?: vm.navigationProgress) - vm.navigationProgress).coerceAtLeast(0f)
        if (remaining < 1000) "${(remaining / 10).toInt() * 10}m" else "%.1fkm".format(Locale.US, remaining / 1000)
    } }
    val remKmText by remember { derivedStateOf {
        vm.navigationRemainingMeters?.let { "%.1fkm".format(Locale.US, it / 1000f) } ?: "대기 중"
    } }
    val remMin by remember { derivedStateOf { vm.remainingSeconds?.let { kotlin.math.ceil(it / 60f).toInt() } } }
    val arrivalText by remember { derivedStateOf {
        vm.arrivalPredictionMillis?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) } ?: "대기 중"
    } }
    Box(Modifier.fillMaxSize().background(MapBg)) {
        RoadMap({ vm.mapPosition }, vm.roadRoute, Modifier.fillMaxSize())
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
                    nm.type == "arrive" -> HyIcons.Flag
                    nm.type == "merge" -> HyIcons.Merge
                    nm.modifier.contains("left") || nm.modifier == "uturn" -> HyIcons.TurnL
                    nm.modifier.contains("right") -> HyIcons.TurnR
                    else -> HyIcons.Straight
                }
                HIcon(ic, 112, Color.White)
                Column {
                    HText(distText, 84, color = Color.White, weight = FontWeight.ExtraBold, lineHeight = 1f)
                    HText("${nm.instruction} · ${nm.name.ifEmpty { "경로 안내" }}", 34, Modifier.padding(top = 8.u), color = Color.White.copy(alpha = 0.92f), maxLines = 1)
                }
            }
        }
        // 도로 구간별 프로파일 시간으로 계산해요. 경로 이탈·재탐색·GPS 유실 시 숨겨요.
        if (vm.navigationReady) Row(
            Modifier.align(Alignment.BottomStart).padding(32.u)
                .shadow(12.u, RoundedCornerShape(32.u))
                .clip(RoundedCornerShape(32.u)).background(MapCard)
                .padding(horizontal = 40.u, vertical = 22.u),
            horizontalArrangement = Arrangement.spacedBy(52.u),
        ) {
            EtaItem(arrivalText, "도착 예상")
            EtaItem(remKmText, "남은 거리")
            EtaItem(remMin?.let { "${it}분" } ?: "대기 중", "남은 시간")
        }

        // Hy! mate 상태 알약 — 화면 조작 없이 기록 중임을 알리는 최소 표시
        Row(
            Modifier.align(Alignment.BottomEnd).padding(32.u).height(96.u)
                .shadow(12.u, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50)).background(MapCard)
                .then(if (!vm.motionAvailable || (!vm.driveUsesDemo && vm.locationLink != com.example.hymate.vehicle.VehicleLink.Connected))
                    Modifier.clickable(onClick = onRequestVehicleAccess) else Modifier)
                .padding(start = 16.u, end = 32.u),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.u),
        ) {
            HyMark(64, off = !vm.questStarted, stop = vm.questStarted && vm.stopped)
            HText(
                when {
                    !vm.motionAvailable -> "차속 연결 대기 · 눌러서 확인"
                    !vm.driveUsesDemo && vm.locationLink != com.example.hymate.vehicle.VehicleLink.Connected -> "위치 연결 대기 · 눌러서 확인"
                    !vm.questStarted -> "기록 안 함"
                    abs(vm.vehicle.speedMps ?: 0f) <= 0.5f / 3.6f && !vm.stopped -> "차량 정지 · 출발 대기"
                    vm.stopped -> "정차 · 짧게 말할 수 있어요"
                    else -> "동승 중 · 화면 조작 없이 기록"
                },
                28, color = MapSub,
            )
        }

        Column(
            Modifier.align(Alignment.TopEnd).padding(32.u).widthIn(max = 900.u),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.u),
        ) {
            HText(
                vm.navigationStatus, 26,
                Modifier.clip(RoundedCornerShape(16.u)).background(MapCard)
                    .padding(horizontal = 20.u, vertical = 12.u),
                color = MapFg,
            )
            HText(
                vm.seatBeltStatus, 26,
                Modifier.clip(RoundedCornerShape(16.u)).background(MapCard)
                    .padding(horizontal = 20.u, vertical = 12.u),
                color = if (com.example.hymate.data.VehicleSignal.BELT in vm.vehicle.knownSignals &&
                    !vm.vehicle.belt) Color(0xFFB3261E) else MapSub,
            )
            if (vm.demoResultsAvailable) {
                if (!vm.driving) HButton(
                    "데모 · 80% 성공 리포트", vm::finishDemoDrive,
                    height = 88, textSize = 30, hPad = 24,
                    container = Color(0xFF006BFF), content = Color.White,
                ) else HText(
                    "데모 리포트 · P에서 실행", 24,
                    Modifier.clip(RoundedCornerShape(16.u)).background(MapCard).padding(16.u),
                    color = MapSub,
                )
            }
        }

        // 음성 배너 (정차 중에만)
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
        HText(value, 50, color = MapFg, weight = FontWeight.Bold, lineHeight = 1.1f)
        HText(label, 26, color = MapSub)
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
                        is VoiceUi.Feedback -> "Hy! mate · 실시간 음성 피드백"
                        is VoiceUi.Question -> if (v.listening) "Hy! mate · 듣는 중" else "Hy! mate"
                        is VoiceUi.Confirm -> "Hy! mate · 저장했어요"
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

/** 주간(밝은) 목업 지도 색 — 테마와 독립이라 리포트 지도는 그대로 둬요. */
private val MapBg = Color(0xFFEAEEF3)

// 지도 위 카드·알약은 앱 다크 테마와 무관하게 주간 지도에 맞는 밝은 색을 써요.
private val MapCard = Color(0xF2FFFFFF)
private val MapFg = Color(0xFF1B2129)
private val MapSub = Color(0xFF66707C)

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
