package com.example.hymate.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.example.hymate.HyViewModel
import com.example.hymate.data.DEST
import com.example.hymate.data.GD
import com.example.hymate.data.GType
import com.example.hymate.data.Quest
import com.example.hymate.data.ROUTE_POINTS
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import com.example.hymate.data.guide
import com.example.hymate.data.holdLabels
import com.example.hymate.data.lvLabel
import com.example.hymate.data.nextTier
import com.example.hymate.data.posAt
import com.example.hymate.data.tierOf
import com.example.hymate.ui.components.ArcadeTag
import com.example.hymate.ui.components.BtnKind
import com.example.hymate.ui.components.Eyebrow
import com.example.hymate.ui.components.H1
import com.example.hymate.ui.components.HButton
import com.example.hymate.ui.components.HCard
import com.example.hymate.ui.components.HChip
import com.example.hymate.ui.components.HIcon
import com.example.hymate.ui.components.HText
import com.example.hymate.ui.components.OxMark
import com.example.hymate.ui.components.PixelGauge
import com.example.hymate.ui.components.VSpacer1
import com.example.hymate.ui.theme.DisplayFont
import com.example.hymate.ui.theme.HyIcons
import com.example.hymate.ui.theme.MonoFont
import com.example.hymate.ui.theme.hy
import com.example.hymate.ui.theme.mix
import com.example.hymate.ui.theme.u
import kotlin.random.Random

@Composable
fun ReportScreen(vm: HyViewModel) {
    if (!vm.questStarted) {
        ScreenFrame {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                H1("기록하지 않은 주행이에요")
                HText("퀘스트를 시작하지 않아서 판정, 기록, 보상이 없어요.", 32, Modifier.padding(top = 12.u), color = hy.sub)
                HButton("다음 주행 준비", vm::nextDrive, Modifier.padding(top = 40.u), kind = BtnKind.Primary)
            }
        }
        return
    }
    val qs = vm.quests
    val auto = qs.filter { guide(it.g).type == GType.AUTO && it.res in "SF" }
    val okN = auto.count { it.res == 'S' }
    val xN = auto.count { it.res == 'F' && !it.disputed }
    val exN = auto.count { it.res == 'F' && it.disputed } // 이의 반영 → 판정 제외 (성장 계산과 같은 기준)
    val recN = qs.count { it.res == 'R' }
    val expN = qs.count { it.res == 'E' }
    val holdN = qs.count { it.res == 'H' } // 운전자 확인 전에는 성공·놓침으로 확정하지 않아요
    ScreenFrame {
        Column(verticalArrangement = Arrangement.spacedBy(24.u)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.u)) {
                Column(Modifier.weight(1f)) {
                    ArcadeTag("04 / DRIVE REPORT", Modifier.padding(bottom = 6.u))
                    Eyebrow("주행 리포트 · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(vm.arrivedAtMillis))} 종료 · ${"%.2f".format(Locale.US, vm.progress / 1000f)}km", hy.blue)
                    H1("오늘 출근길, 이렇게 달렸어요", 50)
                    Row(Modifier.padding(top = 14.u), horizontalArrangement = Arrangement.spacedBy(14.u)) {
                        HChip("지킴 $okN", height = 60, textSize = 28, leading = { OxMark('S') })
                        HChip("놓침 $xN", height = 60, textSize = 28, leading = { OxMark('F') })
                        if (recN > 0) HChip("기록 $recN", height = 60, textSize = 28, leading = { OxMark('R') })
                        if (expN > 0) HChip("경험 $expN", height = 60, textSize = 28, leading = { OxMark('E') })
                        if (holdN > 0) HChip("확인 필요 $holdN", height = 60, textSize = 28, color = hy.amber, leading = { OxMark('H') })
                        if (exN > 0) HChip("제외 $exN", height = 60, textSize = 28, color = hy.sub, leading = { OxMark('X') })
                    }
                }
                HButton("확인하고 마치기", vm::commit, icon = HyIcons.Check, kind = BtnKind.Primary)
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(40.u)) {
                // 지도
                Box(Modifier.weight(1.15f).fillMaxHeight().clip(RoundedCornerShape(32.u)).background(hy.s1)) {
                    ReportMap(qs, vm.reportDetail, Modifier.fillMaxSize())
                    Box(
                        Modifier.padding(start = 24.u, top = 22.u).clip(RoundedCornerShape(16.u)).background(mix(hy.s1, 0.92f, Color.Transparent))
                            .padding(horizontal = 18.u, vertical = 10.u),
                    ) {
                        HText(
                            vm.reportDetail?.let { "${guide(it).short} · 지점 확대" } ?: "예시 경로 · 실측 ${"%.2f".format(Locale.US, vm.progress / 1000f)}km · ${(vm.arrivedSec / 60).toInt()}분",
                            28, weight = FontWeight.Bold,
                        )
                    }
                    Row(
                        Modifier.align(Alignment.BottomEnd).padding(end = 24.u, bottom = 22.u)
                            .clip(RoundedCornerShape(18.u)).background(mix(hy.s1, 0.9f, Color.Transparent))
                            .padding(horizontal = 16.u, vertical = 10.u),
                        horizontalArrangement = Arrangement.spacedBy(24.u),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (vm.reportDetail != null) {
                            listOf('S' to "지킴", 'F' to "놓침", 'R' to "기록").forEach { (r, t) ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.u), verticalAlignment = Alignment.CenterVertically) { OxMark(r); HText(t, 26, color = hy.sub) }
                            }
                        } else {
                            HText("오른쪽에서 상세 보기를 누르면 어디서 놓쳤는지 여기에 표시돼요", 26, color = hy.sub)
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.u)) {
                    val detail = vm.reportDetail
                    if (detail != null) DetailPanel(vm, detail) else SummaryPanel(vm, auto)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.SummaryPanel(vm: HyViewModel, auto: List<Quest>) {
    val rank = auto.groupBy { it.g }
        .map { (k, list) -> Triple(k, list, list.count { it.res == 'F' && !it.disputed }) }
        .filter { it.third > 0 }
        .sortedWith(compareByDescending<Triple<String, List<Quest>, Int>> { it.third }.thenByDescending { guide(it.first).risk })
    // 자가 체크·경험 자기보고: 차가 확인하지 못한 항목은 운전자에게 물어요
    vm.quests.filter { it.res == 'H' }.map { it.g }.distinct().forEach { k ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.u)).background(mix(hy.amber, 0.14f, hy.s1)).clickable { vm.openDetail(k) }
                .padding(horizontal = 24.u, vertical = 18.u),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.u),
        ) {
            OxMark('H')
            Column(Modifier.weight(1f)) {
                HText(guide(k).title, 30, weight = FontWeight.Bold, maxLines = 1)
                HText("차가 확인하지 못했어요 · 직접 알려주세요", 24, color = hy.sub, maxLines = 1)
            }
            HText("답하기", 26, color = hy.amber, weight = FontWeight.Bold)
            HIcon(HyIcons.ChevR, 32, hy.amber)
        }
    }
    Row(verticalAlignment = Alignment.Bottom) {
        HText("가장 많이 놓친 퀘스트 TOP 3", 32, Modifier.weight(1f), weight = FontWeight.Bold)
        HText("상세 보기에서 놓친 지점을 확인해요", 24, color = hy.sub, weight = FontWeight.Medium)
    }
    if (rank.isEmpty()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.u)).background(mix(hy.ok, 0.14f, hy.s1)).padding(30.u),
            horizontalArrangement = Arrangement.spacedBy(18.u), verticalAlignment = Alignment.CenterVertically,
        ) { HIcon(HyIcons.Check, 44, hy.ok); HText("오늘은 놓친 퀘스트가 없어요", 32, color = hy.ok, weight = FontWeight.Bold) }
    }
    rank.take(3).forEachIndexed { i, (k, list, f) ->
        val g = guide(k)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.u)).background(hy.s1).clickable { vm.openDetail(k) }
                .padding(horizontal = 24.u, vertical = 20.u),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(22.u),
        ) {
            Box(Modifier.size(64.u).clip(RoundedCornerShape(20.u)).background(mix(hy.bad, 0.2f, hy.s2)), contentAlignment = Alignment.Center) {
                HText("${i + 1}", 30, color = hy.bad, weight = FontWeight.Black, family = DisplayFont)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.u)) {
                HText(g.title, 32, weight = FontWeight.Bold, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(10.u), verticalAlignment = Alignment.CenterVertically) {
                    list.sortedBy { it.d }.forEach { OxMark(if (it.disputed) 'X' else it.res) }
                    HText("${list.size}곳 중 ${f}곳 놓침 · ${g.lvLabel}", 24, color = hy.sub, maxLines = 1)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                HText("상세 보기", 26, color = hy.blue, weight = FontWeight.Bold)
                HIcon(HyIcons.ChevR, 32, hy.blue)
            }
        }
    }
    if (rank.size > 3) HText("그 밖에 ${rank.size - 3}개 퀘스트에서 놓친 곳이 있어요.", 24, color = hy.sub)
    VSpacer1()
    GrowBox(vm)
}

@Composable
private fun GrowBox(vm: HyViewModel) {
    // XP 게이지 애니메이션 동안 매 프레임 재계산하지 않도록 입력이 바뀔 때만 계산해요
    val g = remember(vm.quests, vm.user) { vm.computeGrowth() }
    val xp0 = vm.user.xp
    val xp1 = xp0 + g.gain
    val t0 = tierOf(xp0)
    val t1 = tierOf(xp1)
    val nx = nextTier(xp0)
    val lo = t0?.second ?: 0
    val hi = nx?.second ?: (xp0 + 1)
    val pa = ((xp0 - lo).toFloat() / (hi - lo)).coerceIn(0f, 1f)
    val pb by animateFloatAsState(((xp1 - lo).toFloat() / (hi - lo)).coerceIn(0f, 1f), tween(900), label = "xp")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.u))
            .background(Brush.linearGradient(listOf(mix(hy.gleo, 0.16f, hy.s1), hy.s1, hy.s1)))
            .padding(horizontal = 26.u, vertical = 22.u),
        verticalArrangement = Arrangement.spacedBy(10.u),
    ) {
        ArcadeTag("PLAYER / XP")
        Row(verticalAlignment = Alignment.Bottom) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.u)) {
                HText("+${g.gain}", 44, weight = FontWeight.Bold, family = DisplayFont, lineHeight = 1f)
                HText(
                    "XP · " + when {
                        t1 != null && t1.first != t0?.first -> "${t1.first} ${if (t0 == null) "첫 라이선스" else "승급"} · 확인하면 발급돼요"
                        nx != null -> "${nx.first}까지 ${(nx.second - xp1).coerceAtLeast(0)}"
                        else -> "최고 단계"
                    },
                    24, color = hy.sub,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                HText("남은 가이드", 24, color = hy.sub)
                HText("${g.remAfter}", 40, weight = FontWeight.Bold, family = DisplayFont, lineHeight = 1f)
            }
        }
        PixelGauge(base = pa, gained = pb, modifier = Modifier.fillMaxWidth())
        if (g.grads.isNotEmpty() || g.rets.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.u)) {
                g.grads.take(3).forEach { HChip("졸업 · ${guide(it).short}", color = hy.ok, height = 52, textSize = 24) }
                g.rets.take(2).forEach { HChip("복귀 · ${guide(it).short}", color = hy.amber, height = 52, textSize = 24) }
            }
        }
    }
}

@Composable
private fun ColumnScope.DetailPanel(vm: HyViewModel, k: String) {
    val g = guide(k)
    val list = vm.quests.filter { it.g == k }.sortedBy { it.d }
    val nf = list.count { it.res == 'F' && !it.disputed }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.u)) {
        HButton(null, vm::closeDetail, icon = HyIcons.ChevL, height = 88, hPad = 20)
        Column {
            HText(g.title, 36, weight = FontWeight.Bold, lineHeight = 1.2f)
            HText("${g.lvLabel} · ${g.src} · ${list.size}곳 중 ${nf}곳 놓침", 24, color = hy.sub)
        }
    }
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.u)) {
        items(list, key = { it.id }) { q ->
            val x = q.res == 'F'
            Row(
                Modifier.fillMaxWidth().alpha(if (q.disputed) .55f else 1f).clip(RoundedCornerShape(24.u)).background(hy.s1)
                    .padding(horizontal = 22.u, vertical = 18.u),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.u),
            ) {
                OxMark(if (q.disputed) 'X' else q.res)
                if (q.res == 'H') {
                    // 보류: 운전자 응답으로 확정 (자기보고라는 사실은 근거에 남겨요)
                    val (yes, no) = holdLabels(q.g)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.u)) {
                        HText(q.place, 30, weight = FontWeight.Bold, maxLines = 1)
                        HText(q.ev, 22, color = hy.sub, family = MonoFont, maxLines = 2)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.u)) {
                            listOf(yes to true, no to false).forEach { (label, v) ->
                                Box(
                                    Modifier.height(76.u).clip(RoundedCornerShape(24.u)).border(2.u, hy.amber, RoundedCornerShape(24.u))
                                        .clickable { vm.answerHold(q.id, v) }.padding(horizontal = 20.u),
                                    contentAlignment = Alignment.Center,
                                ) { HText(label, 24, weight = FontWeight.SemiBold) }
                            }
                        }
                    }
                }
                if (q.res != 'H') Column(Modifier.weight(1f)) {
                    HText(q.place, 30, weight = FontWeight.Bold, maxLines = 1)
                    HText(q.ev, 22, color = if (x && !q.disputed) hy.bad else hy.sub, family = MonoFont, maxLines = 1)
                }
                if (x) {
                    val on = q.disputed
                    Box(
                        Modifier.height(76.u).clip(RoundedCornerShape(24.u))
                            .then(if (on) Modifier.background(hy.s2) else Modifier.border(2.u, hy.line, RoundedCornerShape(24.u)))
                            .clickable { vm.toggleDispute(q.id) }.padding(horizontal = 20.u),
                        contentAlignment = Alignment.Center,
                    ) { HText(if (on) "이의 반영됨" else "이건 아니었어요", 24, color = if (on) hy.sub else hy.fg, weight = FontWeight.SemiBold) }
                }
            }
        }
    }
    GD[k]?.let {
        HCard(Modifier.fillMaxWidth(), color = mix(hy.blue, 0.12f, hy.s1), radius = 24, padding = PaddingValues(horizontal = 24.u, vertical = 18.u)) {
            HText("다음엔 이렇게", 22, color = hy.blue, weight = FontWeight.Bold)
            HText(it.how[1], 28, Modifier.padding(top = 4.u), lineHeight = 1.45f)
        }
    }
}

private class Seg(val a: Offset, val b: Offset, val w: Float)

/** 오늘 달린 경로 · 퀘스트 지점 지도 (detail이 있으면 해당 지점들로 확대) */
@Composable
private fun ReportMap(quests: List<Quest>, focus: String?, modifier: Modifier) {
    val c = hy
    val tm = rememberTextMeasurer()
    val streets = remember {
        val r = Random(11)
        List(260) {
            val x = -260f + r.nextFloat() * 880f
            val y = -140f + r.nextFloat() * 720f
            val ang = listOf(0, 90, 28, 118, 62, 152)[r.nextInt(6)] * Math.PI.toFloat() / 180f + (r.nextFloat() - .5f) * .2f
            val l = 15f + r.nextFloat() * 80f
            Seg(Offset(x - kotlin.math.cos(ang) * l, y - kotlin.math.sin(ang) * l), Offset(x + kotlin.math.cos(ang) * l, y + kotlin.math.sin(ang) * l), if (r.nextFloat() < .25f) 5f else 2.5f)
        }
    }
    val marks = quests.filter { it.g == focus && it.res in "SFRHEN" }.sortedBy { it.d }
    // 뷰박스 (경로 좌표계)
    val pts = if (marks.isNotEmpty()) marks.map { posAt(it.d.toFloat()) } else ROUTE_POINTS
    val pad = if (marks.isNotEmpty()) 30f else 40f
    val x0 = pts.minOf { it.x } - pad
    val x1 = pts.maxOf { it.x } + pad
    val y0 = pts.minOf { it.y } - pad - if (marks.isEmpty()) 70f else 0f
    val y1 = pts.maxOf { it.y } + pad + if (marks.isEmpty()) 60f else 0f
    val cx by animateFloatAsState((x0 + x1) / 2, tween(650), label = "cx")
    val cy by animateFloatAsState((y0 + y1) / 2, tween(650), label = "cy")
    val vw by animateFloatAsState((x1 - x0).coerceAtLeast(if (marks.isNotEmpty()) 150f else 0f), tween(650), label = "vw")
    val vh by animateFloatAsState((y1 - y0).coerceAtLeast(if (marks.isNotEmpty()) 100f else 0f), tween(650), label = "vh")

    Canvas(modifier) {
        drawRect(c.mapBg)
        val k = minOf(size.width / vw, size.height / vh)
        val px = size.width / 1260f // 화면 px 단위 (디자인 기준)
        withTransform({
            translate(size.width / 2, size.height / 2)
            scale(k, k, Offset.Zero)
            translate(-cx, -cy)
        }) {
            // 한강
            val river = Path().apply {
                moveTo(-400f, 236f); cubicTo(60f, 226f, 120f, 268f, 200f, 252f); cubicTo(280f, 236f, 320f, 236f, 800f, 262f)
                lineTo(800f, 284f); cubicTo(320f, 258f, 260f, 276f, 200f, 274f); cubicTo(140f, 272f, 60f, 252f, -400f, 258f); close()
            }
            drawPath(river, c.mapRiver)
            streets.forEach { drawLine(c.mapStreet, it.a, it.b, it.w * px / k, cap = StrokeCap.Round) }
            val path = Path().apply { ROUTE_POINTS.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
            drawPath(path, c.mapCase, style = Stroke(22f * px / k, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, c.blue.copy(alpha = if (focus != null) .6f else 1f), style = Stroke(11f * px / k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        fun screen(o: Offset) = Offset(size.width / 2 + (o.x - cx) * k, size.height / 2 + (o.y - cy) * k)
        fun label(t: String, at: Offset, sz: Float, col: Color, bold: Boolean = true, anchorEnd: Boolean = false, bg: Color? = null) {
            val r = tm.measure(t, TextStyle(fontSize = (sz * px / density / fontScale).sp, color = col, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold))
            val tl = Offset(if (anchorEnd) at.x - r.size.width else at.x, at.y - r.size.height / 2f)
            if (bg != null) drawRoundRect(bg, tl - Offset(12 * px, 4 * px), androidx.compose.ui.geometry.Size(r.size.width + 24 * px, r.size.height + 8 * px), androidx.compose.ui.geometry.CornerRadius(14 * px))
            drawText(r, topLeft = tl)
        }
        // 도로 이름
        if (focus == null) {
            listOf("정릉로" to 1300f, "내부순환로" to 6200f, "동부간선도로" to 10000f, "영동대로" to 14600f, "양재대로" to 19300f).forEach { (t, d) ->
                label(t, screen(posAt(d)) + Offset(18 * px, 0f), 22f, c.mapLabel, bold = false)
            }
        }
        // 출발 · 도착
        val s = screen(posAt(0f))
        val e = screen(posAt(DEST.toFloat()))
        drawCircle(c.ok, 16 * px, s); drawCircle(Color.White, 16 * px, s, style = Stroke(5 * px))
        drawCircle(c.bad, 16 * px, e); drawCircle(Color.White, 16 * px, e, style = Stroke(5 * px))
        if (focus == null) {
            label("출발 · 국민대학교", s + Offset(28 * px, 0f), 28f, c.fg)
            label("도착 · 현대자동차 양재 본사", e - Offset(28 * px, 0f), 28f, c.fg, anchorEnd = true)
        }
        // 퀘스트 지점
        marks.forEachIndexed { i, q ->
            val p = screen(posAt(q.d.toFloat()))
            val res = if (q.disputed || q.res == 'N') 'X' else q.res
            val col = when (res) { 'F' -> c.bad; 'S' -> c.ok; 'R' -> c.blue; 'E' -> c.gleo; 'X' -> c.sub; else -> c.amber }
            if (res == 'S' || res == 'X') {
                drawCircle(c.s1, 22 * px, p); drawCircle(col, 22 * px, p, style = Stroke(8 * px))
            } else {
                drawCircle(col, 30 * px, p); drawCircle(Color.White, 30 * px, p, style = Stroke(4 * px))
                val r = tm.measure(when (res) { 'F' -> "✕"; 'R' -> "기"; 'E' -> "경"; else -> "?" }, TextStyle(fontSize = (30 * px / density / fontScale).sp, color = Color.White, fontWeight = FontWeight.Black, textAlign = TextAlign.Center))
                drawText(r, topLeft = p - Offset(r.size.width / 2f, r.size.height / 2f))
            }
            val left = i % 2 == 1
            label(q.short, p + Offset(if (left) -54 * px else 54 * px, 0f), 28f, if (res == 'F') c.bad else c.fg, anchorEnd = left, bg = c.s1.copy(alpha = .92f))
        }
    }
}
