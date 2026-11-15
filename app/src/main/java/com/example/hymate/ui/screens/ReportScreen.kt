package com.example.hymate.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import com.example.hymate.ui.components.RoadMap
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
    val recN = qs.count { it.res == 'R' && !it.selfCheck }
    val selfN = qs.count { it.selfAnswer != null || (it.selfCheck && it.notApplicable) }
    val expN = qs.count { it.res == 'E' }
    val holdN = qs.count { it.res == 'H' } // 운전자 확인 전에는 성공·놓침으로 확정하지 않아요
    ScreenFrame {
        Column(verticalArrangement = Arrangement.spacedBy(24.u)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.u)) {
                Column(Modifier.weight(1f)) {
                    ArcadeTag("04 / DRIVE REPORT", Modifier.padding(bottom = 6.u))
                    Eyebrow("주행 리포트 · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(vm.arrivedAtMillis))} 종료 · ${"%.2f".format(Locale.US, vm.progress / 1000f)}km", hy.blue)
                    H1("오늘 출근길, 이렇게 달렸어요", 50)
                    FlowRow(Modifier.padding(top = 14.u), horizontalArrangement = Arrangement.spacedBy(14.u), verticalArrangement = Arrangement.spacedBy(8.u)) {
                        HChip("지킴 $okN", height = 60, textSize = 28, leading = { OxMark('S') })
                        HChip("놓침 $xN", height = 60, textSize = 28, leading = { OxMark('F') })
                        if (recN > 0) HChip("기록 $recN", height = 60, textSize = 28, leading = { OxMark('R') })
                        if (selfN > 0) HChip("자가 체크 $selfN", height = 60, textSize = 28, leading = { OxMark('R') })
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
                    RoadMap({ vm.reportDetail?.let { k -> qs.firstOrNull { it.g == k && it.coordinate != null }?.coordinate } ?: vm.mapPosition }, vm.roadRoute, Modifier.fillMaxSize())
                    Box(
                        Modifier.padding(start = 24.u, top = 22.u).clip(RoundedCornerShape(16.u)).background(mix(hy.s1, 0.92f, Color.Transparent))
                            .padding(horizontal = 18.u, vertical = 10.u),
                    ) {
                        HText(
                            vm.reportDetail?.let { "${guide(it).short} · 기록된 지점 또는 최종 위치" } ?: "최종 안내 경로 · 실측 ${"%.2f".format(Locale.US, vm.progress / 1000f)}km · ${(vm.arrivedSec / 60).toInt()}분",
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

// 생성 여부는 내부 기록에 유지하고, 발표 화면에는 결과만 표시해요.
private fun resultText(q: Quest, generated: Boolean): String = if (!generated) q.ev else when (q.res) {
    'S' -> "가이드를 지켰어요"
    'F' -> "다음 주행에서 다시 챙겨요"
    'R' -> "주행 기록을 남겼어요"
    'E' -> "사용 경험을 기록했어요"
    else -> "이번 주행에서는 수행하지 않았어요"
}

@Composable
private fun ColumnScope.SummaryPanel(vm: HyViewModel, auto: List<Quest>) {
    val groups = vm.quests.groupBy { it.g }.entries.sortedWith(
        compareByDescending<Map.Entry<String, List<Quest>>> { it.value.any { q -> q.res == 'H' } }
            .thenByDescending { it.value.any { q -> q.res == 'F' && !q.disputed } }
            .thenBy { guide(it.key).lv },
    )
    HText("전체 퀘스트 ${groups.size}개", 34, weight = FontWeight.Bold)
    HText(
        if (vm.demoReport) "전체 ${vm.quests.size}건 · 각 항목을 눌러 결과를 확인해요"
        else "자동 판정 ${auto.size}건 · 각 항목을 눌러 근거와 직접 체크를 확인해요",
        24, color = hy.sub,
    )
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.u)) {
        items(groups, key = { it.key }) { (key, list) ->
            val pending = list.count { it.res == 'H' }
            val missed = list.count { it.res == 'F' && !it.disputed }
            val kept = list.count { it.res == 'S' }
            val selfDone = list.count { it.selfAnswer != null || (it.selfCheck && it.notApplicable) }
            val symbol = when {
                pending > 0 -> 'H'
                missed > 0 -> 'F'
                kept > 0 -> 'S'
                list.any { it.res == 'E' } -> 'E'
                selfDone > 0 || list.any { it.res == 'R' } -> 'R'
                else -> 'N'
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.u)).background(hy.s1)
                    .clickable { vm.openDetail(key) }.padding(horizontal = 24.u, vertical = 20.u),
                horizontalArrangement = Arrangement.spacedBy(18.u),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OxMark(symbol)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.u)) {
                    HText(guide(key).title, 30, weight = FontWeight.Bold, maxLines = 1)
                    HText(
                        when {
                            vm.demoReport -> resultText(list.first(), true)
                            pending > 0 -> "직접 체크 ${pending}건 대기 · 자동 지킴 $kept · 놓침 $missed"
                            kept + missed > 0 -> "자동 지킴 $kept · 놓침 $missed · 자가 체크 $selfDone"
                            selfDone > 0 -> "자가 체크 ${selfDone}건 완료 · 자동 판정과 구분해 기록했어요"
                            else -> list.first().ev
                        }, 23, color = hy.sub, maxLines = 2,
                    )
                }
                HIcon(HyIcons.ChevR, 32, if (pending > 0) hy.amber else hy.blue)
            }
        }
        item { GrowBox(vm) }
    }
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
                    val (yes, no) = if (q.selfCheck && g.type == GType.AUTO) "지켰어요" to "못 지켰어요" else holdLabels(q.g)
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
                            if (q.selfCheck) HButton("해당 없음", { vm.skipHold(q.id) }, height = 76, textSize = 24, hPad = 16)
                        }
                    }
                }
                if (q.res != 'H') Column(Modifier.weight(1f)) {
                    HText(if (vm.demoReport) g.short else q.place, 30, weight = FontWeight.Bold, maxLines = 1)
                    HText(resultText(q, vm.demoReport), 22, color = if (x && !q.disputed) hy.bad else hy.sub, family = MonoFont, maxLines = 1)
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
