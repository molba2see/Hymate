package com.example.hymate.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.hymate.HyViewModel
import com.example.hymate.data.GD
import com.example.hymate.data.GType
import com.example.hymate.data.lvLabel
import com.example.hymate.data.TODAY_KEY
import com.example.hymate.data.XP
import com.example.hymate.data.guide
import com.example.hymate.data.routeCount
import com.example.hymate.data.todayQuest
import com.example.hymate.ui.components.BtnKind
import com.example.hymate.ui.components.Eyebrow
import com.example.hymate.ui.components.H1
import com.example.hymate.ui.components.HButton
import com.example.hymate.ui.components.HCard
import com.example.hymate.ui.components.HChip
import com.example.hymate.ui.components.HIcon
import com.example.hymate.ui.components.HText
import com.example.hymate.ui.components.SquareButton
import com.example.hymate.ui.theme.HyIcons
import com.example.hymate.ui.theme.OnOk
import com.example.hymate.ui.theme.hy
import com.example.hymate.ui.theme.mix
import com.example.hymate.ui.theme.u
import com.example.hymate.vehicle.VehicleLink

@Composable
fun GuideDetailScreen(vm: HyViewModel) {
    val k = vm.selectedGuide ?: return
    val isToday = k == TODAY_KEY
    val steps = vm.steps(k)
    val st = vm.practiceState(k)
    val isCheck = steps.isEmpty()
    val title = if (isToday) todayQuest(vm.rainy).title else guide(k).title
    ScreenFrame {
        Column(verticalArrangement = Arrangement.spacedBy(32.u)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.u)) {
                SquareButton(HyIcons.ChevL, vm::back)
                Column(Modifier.weight(1f)) {
                    if (isToday) Eyebrow("오늘의 실습 퀘스트 · 주차 중에만", hy.blue)
                    else Eyebrow("가이드 자세히 · ${guide(k).lvLabel} · ${guide(k).src}")
                    H1(title)
                }
                HChip(
                    when {
                        st.done -> if (isCheck) "확인 완료" else "연습 완료"
                        isCheck -> "내용 확인"
                        else -> "연습 ${st.step}/${steps.size}"
                    },
                    if (st.done) HyIcons.Check else null,
                    color = if (st.done) hy.ok else hy.fg,
                )
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(36.u)) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(22.u),
                ) { if (isToday) TodayLeft(vm) else GuideLeft(vm, k) }
                PracticePanel(vm, k, Modifier.weight(0.82f))
            }
        }
    }
}

@Composable
private fun TodayLeft(vm: HyViewModel) {
    val t = todayQuest(vm.rainy)
    HCard(Modifier.fillMaxWidth()) {
        HText(t.why, 34, weight = FontWeight.SemiBold, lineHeight = 1.45f)
        HText("실제 조작은 차량 데이터(Vehicle SDK)로 확인해요. 주차 실습 완료와 실제 주행 결과는 따로 기록해요.", 24, Modifier.padding(top = 8.u), color = hy.sub)
    }
    CtxTiles(listOf("보상" to "+${XP.PRAC} XP", "걸리는 시간" to "약 1분", "열리는 때" to "주차 중"))
    Note(HyIcons.Lock, "주행 중에는 열리지 않아요. 오늘 못 하면 다음 주차 때 이어서 할 수 있어요.")
}

@Composable
private fun GuideLeft(vm: HyViewModel, k: String) {
    val g = guide(k)
    val d = GD.getValue(k)
    val s = vm.user.st.getValue(k)
    val need = if (g.lv <= 2) 1 else 3
    HCard(Modifier.fillMaxWidth()) {
        HText(d.why, 34, weight = FontWeight.SemiBold, lineHeight = 1.45f)
        HText("근거: ${g.src}", 24, Modifier.padding(top = 8.u), color = hy.sub)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.u)) {
        CtxTile("오늘 경로에서", if (vm.user.routeMemory) routeCount(k, vm.rainy).removePrefix("오늘 경로 ") else "주행 중 확인", Modifier.weight(1f))
        CtxTile(
            "졸업까지",
            when {
                g.type == GType.RECORD -> "기록만 해요"
                g.type == GType.EXPERIENCE -> "경험형"
                s.status == "graduated" -> "졸업"
                else -> "${(need - s.count).coerceAtLeast(0)}곳 남음"
            },
            Modifier.weight(1f),
        )
        Column(Modifier.weight(1f).clip(RoundedCornerShape(24.u)).background(hy.s2).padding(horizontal = 22.u, vertical = 20.u)) {
            HText("최근 기록", 24, color = hy.sub)
            val rec = s.recent.takeLast(5)
            if (rec.isEmpty()) HText("아직 없음", 34, Modifier.padding(top = 4.u), weight = FontWeight.Bold)
            else Row(Modifier.padding(top = 8.u), horizontalArrangement = Arrangement.spacedBy(8.u)) {
                rec.forEach { r ->
                    Box(Modifier.size(36.u).clip(RoundedCornerShape(10.u)).background(hy.s1), contentAlignment = Alignment.Center) {
                        HText(if (r == 'S') "✓" else "✕", 22, color = if (r == 'S') hy.ok else hy.bad, weight = FontWeight.Black)
                    }
                }
            }
        }
    }
    HCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 30.u, vertical = 24.u), borderColor = mix(hy.amber, 0.45f, Color.Transparent)) {
        HText("이 길에서 초보가 어려워하는 이유", 26, Modifier.padding(bottom = 6.u), color = hy.amber, weight = FontWeight.Bold)
        HText(d.pain, 30, lineHeight = 1.45f)
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.u)) {
        HText("이렇게 해요", 30, weight = FontWeight.Bold)
        d.how.forEachIndexed { i, h ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.u)) {
                Box(Modifier.size(56.u).clip(RoundedCornerShape(18.u)).background(hy.s2), contentAlignment = Alignment.Center) {
                    HText("${i + 1}", 28, color = hy.blue, weight = FontWeight.Bold)
                }
                HText(h, 30, Modifier.weight(1f).padding(top = 6.u), lineHeight = 1.4f)
            }
        }
    }
    if (d.dia != null) HCard(Modifier.fillMaxWidth(), padding = PaddingValues(24.u)) { SignalDiagram(d.dia == "merge") }
    Note(HyIcons.Shield, "오늘 어느 지점에서 판정하는지는 미리 알려주지 않아요. 알려주면 정답을 따라 하게 돼서, 몸에 익었는지 확인할 수 없거든요.")
}

@Composable
private fun CtxTiles(items: List<Pair<String, String>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.u)) { items.forEach { (a, b) -> CtxTile(a, b, Modifier.weight(1f)) } }
}

@Composable
private fun CtxTile(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(24.u)).background(hy.s2).padding(horizontal = 22.u, vertical = 20.u)) {
        HText(label, 24, color = hy.sub)
        HText(value, 34, Modifier.padding(top = 4.u), weight = FontWeight.Bold, maxLines = 2)
    }
}

@Composable
private fun Note(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, size: Int = 24) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.u)) {
        HIcon(icon, 30, hy.sub)
        HText(text, size, Modifier.weight(1f), color = hy.sub, lineHeight = 1.4f)
    }
}

@Composable
private fun PracticePanel(vm: HyViewModel, k: String, modifier: Modifier) {
    val steps = vm.steps(k)
    val st = vm.practiceState(k)
    val isCheck = steps.isEmpty()
    Column(
        modifier.clip(RoundedCornerShape(32.u)).background(hy.s1).verticalScroll(rememberScrollState()).padding(32.u),
        verticalArrangement = Arrangement.spacedBy(20.u),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HText(if (isCheck) "내용 확인" else "주차 중 연습", 32, Modifier.weight(1f), weight = FontWeight.Bold)
            HText(if (isCheck) "주행 중에 판정" else "차량 데이터로 확인", 24, color = hy.sub, weight = FontWeight.SemiBold)
        }
        if (isCheck) {
            Note(HyIcons.Lock, "주차 중에는 직접 해볼 수 없어서 내용만 확인해요. 판정은 주행 중에 차량 데이터로 해요.", 26)
            GD.getValue(k).check.forEachIndexed { i, c -> StepRow(i, c, null, "ok") }
            if (st.done) {
                DoneBox("확인했어요 · +${vm.practiceReward(k)} XP")
                HButton("가이드 목록으로", vm::back, Modifier.fillMaxWidth(), icon = HyIcons.ChevL)
            } else {
                HButton("확인했어요", vm::confirmCheck, Modifier.fillMaxWidth(), icon = HyIcons.Check, kind = BtnKind.Primary)
            }
        } else {
            steps.forEachIndexed { i, p ->
                val state = if (st.done || i < st.step) "ok" else if (i == st.step) "cur" else "todo"
                StepRow(i, p.title, p.hint, state)
            }
            if (st.done) {
                DoneBox("연습을 마쳤어요 · +${vm.practiceReward(k)} XP")
                HButton("가이드 목록으로", vm::back, Modifier.fillMaxWidth(), icon = HyIcons.ChevL)
            } else {
                val cur = steps[st.step]
                Row(
                    Modifier.fillMaxWidth()
                        .border(2.u, hy.line, RoundedCornerShape(20.u))
                        .padding(horizontal = 18.u, vertical = 14.u),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.u),
                ) {
                    HIcon(HyIcons.Dot, 26, hy.ok)
                    HText(cur.live(vm.vehicle), 26, weight = FontWeight.Bold, family = com.example.hymate.ui.theme.MonoFont)
                    HText("조작하면 바로 확인해요", 26, color = hy.sub)
                }
                if (vm.vehicleLink != VehicleLink.Connected && vm.speedLink != VehicleLink.Connected) Row(
                    Modifier.height(80.u)
                        .clip(RoundedCornerShape(22.u))
                        .border(3.u, hy.blue, RoundedCornerShape(22.u))
                        .clickable(onClick = vm::simulateStep)
                        .padding(horizontal = 26.u),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.u),
                ) {
                    HIcon(HyIcons.Play, 30, hy.blue)
                    HText("차량 조작 시뮬레이션", 28, color = hy.blue, weight = FontWeight.Bold)
                }
                when (val link = vm.vehicleLink) {
                    VehicleLink.Connected ->
                        HText("차량과 연결됐어요. 레버나 다이얼을 직접 조작하면 바로 확인해요.", 22, color = hy.ok)
                    is VehicleLink.Unavailable ->
                        HText("차량 데이터에 연결되지 않아 시뮬레이션으로 연습해요. (${link.reason})", 22, color = hy.sub, maxLines = 2)
                    else ->
                        HText("실제 차에서는 레버나 다이얼을 직접 조작하면 Vehicle SDK로 감지해요.", 22, color = hy.sub)
                }
            }
        }
    }
}

@Composable
private fun StepRow(i: Int, title: String, hint: String?, state: String) {
    val cur = state == "cur"
    val ok = state == "ok"
    val pulse by rememberInfiniteTransition(label = "p").animateFloat(1f, 0.94f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
    Row(
        Modifier.fillMaxWidth()
            .alpha(if (cur || ok) 1f else 0.55f)
            .clip(RoundedCornerShape(24.u))
            .background(if (cur) mix(hy.blue, 0.10f, hy.s2) else hy.s2)
            .then(if (cur) Modifier.border(3.u, hy.blue, RoundedCornerShape(24.u)) else Modifier)
            .padding(horizontal = 20.u, vertical = 18.u),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.u),
    ) {
        Box(
            Modifier.size(64.u).scale(if (cur) pulse else 1f).clip(RoundedCornerShape(20.u))
                .background(if (ok) hy.ok else if (cur) hy.blue else hy.s1),
            contentAlignment = Alignment.Center,
        ) {
            if (ok && hint != null) HIcon(HyIcons.Check, 36, OnOk)
            else HText("${i + 1}", 28, color = if (ok) OnOk else if (cur) Color.White else hy.fg, weight = FontWeight.ExtraBold)
        }
        Column(Modifier.weight(1f)) {
            HText(title, 30, lineHeight = 1.35f)
            if (hint != null) HText(hint, 24, Modifier.padding(top = 2.u), color = hy.sub)
        }
    }
}

@Composable
private fun DoneBox(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.u)).background(mix(hy.ok, 0.16f, hy.s2)).padding(horizontal = 24.u, vertical = 22.u),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.u),
    ) {
        HIcon(HyIcons.Check, 48, hy.ok)
        HText(text, 30, color = hy.ok, weight = FontWeight.Bold)
    }
}

/** 회전/합류 지점 30m 전 방향지시등 다이어그램 (프로토타입 diaSVG) */
@Composable
private fun SignalDiagram(merge: Boolean) {
    val c = hy
    val tm = rememberTextMeasurer()
    val blink by rememberInfiniteTransition(label = "blk").animateFloat(
        1f, 0.15f, infiniteRepeatable(tween(660, easing = Easing { if (it < 0.5f) 0f else 1f }), RepeatMode.Restart), label = "b",
    )
    Canvas(Modifier.fillMaxWidth().aspectRatio(900f / 250f)) {
        val k = size.width / 900f
        fun o(x: Float, y: Float) = Offset(x * k, y * k)
        drawRoundRect(c.s2, o(0f, 70f), Size(900 * k, 110 * k), CornerRadius(10 * k))
        if (merge) {
            drawPath(Path().apply { moveTo(640 * k, 180 * k); lineTo(900 * k, 250 * k); lineTo(900 * k, 180 * k); close() }, c.s2)
            drawLine(c.line, o(560f, 180f), o(900f, 260f), 4 * k, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18 * k, 14 * k)))
        } else {
            drawRect(c.s2, o(720f, 0f), Size(110 * k, 250 * k))
        }
        drawLine(c.line, o(0f, 125f), o(if (merge) 900f else 720f, 125f), 4 * k, pathEffect = PathEffect.dashPathEffect(floatArrayOf(26 * k, 20 * k)))
        translate(150 * k, 98 * k) {
            drawRoundRect(c.blue, Offset.Zero, Size(120 * k, 54 * k), CornerRadius(16 * k))
            drawRoundRect(Color(0xFFFFB020).copy(alpha = blink), o(100f, if (merge) 40f else 4f), Size(18 * k, 10 * k), CornerRadius(3 * k))
        }
        val lx = if (merge) 470f else 560f
        val tx = if (merge) 640f else 720f
        drawLine(c.ok, o(lx, 40f), o(lx, 210f), 5 * k, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10 * k, 8 * k)))
        drawLine(c.sub, o(lx, 232f), o(tx - 8, 232f), 3 * k)
        drawPath(Path().apply { moveTo(tx * k, 232 * k); lineTo((tx - 12) * k, 226 * k); lineTo((tx - 12) * k, 238 * k); close() }, c.sub)
        fun label(t: String, x: Float, y: Float, sizePx: Float, col: Color, bold: Boolean) {
            val r = tm.measure(t, TextStyle(fontSize = (sizePx * k / density / fontScale).sp, color = col, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
            drawText(r, topLeft = Offset(x * k - r.size.width / 2f, y * k - r.size.height * 0.8f))
        }
        label("여기서 켜기 · 30m 전", lx, 30f, 26f, c.ok, true)
        label("30m", (lx + tx) / 2, 226f, 22f, c.sub, false)
        label(if (merge) "합류·분기 지점" else "회전 지점", if (merge) 780f else 775f, 60f, 24f, c.fg, true)
    }
}
