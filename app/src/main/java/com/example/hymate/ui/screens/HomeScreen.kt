package com.example.hymate.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.example.hymate.HyViewModel
import com.example.hymate.data.TIERS
import com.example.hymate.data.TODAY_KEY
import com.example.hymate.data.XP
import com.example.hymate.data.nextTier
import com.example.hymate.data.remaining
import com.example.hymate.data.tierOf
import com.example.hymate.data.todayQuest
import com.example.hymate.ui.components.ArcadeTag
import com.example.hymate.ui.components.BtnKind
import com.example.hymate.ui.components.H1
import com.example.hymate.ui.components.HButton
import com.example.hymate.ui.components.HCard
import com.example.hymate.ui.components.HChip
import com.example.hymate.ui.components.HText
import com.example.hymate.ui.components.HyMark
import com.example.hymate.ui.components.OxMark
import com.example.hymate.ui.components.PixelGauge
import com.example.hymate.ui.components.SquareButton
import com.example.hymate.ui.components.VSpacer1
import com.example.hymate.ui.theme.DisplayFont
import com.example.hymate.ui.theme.HyIcons
import com.example.hymate.ui.theme.hy
import com.example.hymate.ui.theme.u
import com.example.hymate.vehicle.VehicleLink
import kotlin.random.Random

/**
 * 메인 홈. 오늘 코스로 들어가는 입구 + 내 성장 요약.
 * 글자를 크게, 항목을 적게 두어서 주행 중에도 한눈에 볼 수 있게 했어요.
 */
@Composable
fun HomeScreen(vm: HyViewModel) {
    ScreenFrame {
        Column(verticalArrangement = Arrangement.spacedBy(32.u)) {
            // ── 헤더 ──
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.u)) {
                HyMark(104)
                Column(Modifier.weight(1f)) {
                    ArcadeTag("DRIVING ARCADE", Modifier.padding(bottom = 6.u))
                    H1("오늘도 같이 달려요, A씨")
                }
                when (vm.vehicleLink) {
                    VehicleLink.Connected -> HChip(if (vm.driving) "차량 연결 · 주행 중" else "차량 연결 · 주차 중", HyIcons.Dot, color = hy.ok)
                    VehicleLink.Connecting -> HChip("차량 연결 중", HyIcons.Dot, color = hy.sub)
                    else -> HChip("시뮬레이션", HyIcons.Dot, color = hy.sub)
                }
                SquareButton(HyIcons.Gear, vm::openSettings)
            }
            // ── 본문 ──
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(40.u)) {
                Column(Modifier.weight(1000f), verticalArrangement = Arrangement.spacedBy(24.u)) {
                    CourseHero(vm, Modifier.fillMaxWidth().weight(1.25f))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.u)) {
                        TodayQuestCard(vm, Modifier.weight(1f).fillMaxHeight())
                        DriveLogCard(vm, Modifier.weight(1f).fillMaxHeight())
                    }
                }
                PlayerCard(vm, Modifier.weight(548f).fillMaxHeight())
            }
        }
    }
}

/** 오늘 코스 — 밤 도시 픽셀 일러스트 위에 경로와 "주행 준비" 버튼 */
@Composable
private fun CourseHero(vm: HyViewModel, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(32.u)).background(hy.mapBg)) {
        PixelSkyline(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(40.u)) {
            ArcadeTag("SEOUL / TODAY'S COURSE")
            HText("국민대학교 → 현대자동차 양재 본사", 50, Modifier.padding(top = 10.u), color = hy.fg, weight = FontWeight.Bold, lineHeight = 1.2f, maxLines = 2)
            HText(
                "21.4km · 약 42분 · " + if (vm.rainy) "비 예보 08:30부터" else "맑음",
                30, Modifier.padding(top = 8.u), color = hy.sub,
            )
            VSpacer1()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.u)) {
                HButton("주행 준비", vm::openPre, icon = HyIcons.Play, kind = BtnKind.Primary, textSize = 42)
                HText(
                    if (vm.user.routeMemory) "자주 다닌 길 · ${vm.user.drives}회" else "처음 가는 길",
                    28, color = hy.ok, weight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun TodayQuestCard(vm: HyViewModel, modifier: Modifier) {
    val t = todayQuest(vm.rainy)
    val st = vm.practiceState(TODAY_KEY)
    val done = if (st.done) t.practice.size else st.step
    HCard(modifier.clickable { vm.openGuide(TODAY_KEY) }, padding = PaddingValues(horizontal = 30.u, vertical = 26.u)) {
        Row {
            ArcadeTag("01 / TODAY'S QUEST", Modifier.weight(1f))
            HText("+${XP.PRAC} XP", 24, color = if (st.done) hy.ok else hy.blue, weight = FontWeight.Bold)
        }
        HText(t.title, 36, Modifier.padding(top = 10.u), weight = FontWeight.Bold, lineHeight = 1.25f, maxLines = 2)
        HText("주차 중에만 · 약 1분", 26, Modifier.padding(top = 4.u), color = hy.sub)
        VSpacer1()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.u)) {
                repeat(t.practice.size) { i ->
                    Box(Modifier.width(56.u).height(14.u).background(if (i < done) hy.ok else hy.s2))
                }
            }
            HText(
                if (st.done) "완료" else if (done > 0) "이어서 하기 ›" else "지금 해보기 ›",
                30, color = if (st.done) hy.ok else hy.blue, weight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DriveLogCard(vm: HyViewModel, modifier: Modifier) {
    val log = vm.lastDrive
    HCard(modifier, padding = PaddingValues(horizontal = 30.u, vertical = 26.u)) {
        ArcadeTag("02 / DRIVE LOG")
        if (log == null) {
            HText("아직 주행 기록이 없어요", 36, Modifier.padding(top = 10.u), weight = FontWeight.Bold, lineHeight = 1.25f)
            HText("주행을 마치면 여기에 요약이 보여요.", 26, Modifier.padding(top = 4.u), color = hy.sub)
        } else {
            HText("최근 주행", 36, Modifier.padding(top = 10.u), weight = FontWeight.Bold)
            HText("국민대학교 → 양재 본사 · 21.4km", 26, Modifier.padding(top = 4.u), color = hy.sub, maxLines = 1)
            VSpacer1()
            Row(horizontalArrangement = Arrangement.spacedBy(12.u)) {
                HChip("지킴 ${log.ok}", height = 60, textSize = 28, leading = { OxMark('S') })
                HChip("놓침 ${log.miss}", height = 60, textSize = 28, leading = { OxMark('F') })
            }
            HText(
                "+${log.gain} XP" + if (log.grads > 0) " · 졸업 ${log.grads}개" else "",
                30, Modifier.padding(top = 12.u), color = hy.ok, weight = FontWeight.Bold,
            )
        }
    }
}

/** 내 정보: 라이선스 단계 · XP · 누적 기록 */
@Composable
private fun PlayerCard(vm: HyViewModel, modifier: Modifier) {
    val u = vm.user
    val tier = tierOf(u.xp)
    val next = nextTier(u.xp)
    val lo = tier?.second ?: 0
    val hi = next?.second ?: (u.xp + 1)
    val p = ((u.xp - lo).toFloat() / (hi - lo)).coerceIn(0f, 1f)
    HCard(modifier, padding = PaddingValues(horizontal = 32.u, vertical = 30.u)) {
        ArcadeTag("PLAYER / LICENSE")
        Row(Modifier.padding(top = 14.u), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.u)) {
            HText(tier?.first ?: "—", 96, weight = FontWeight.Black, family = DisplayFont, lineHeight = 1f)
            Column(Modifier.padding(bottom = 10.u)) {
                HText("A씨", 32, weight = FontWeight.Bold)
                HText(
                    when {
                        tier == null -> "${TIERS.first().second} XP에 첫 라이선스"
                        tier.first.startsWith("L") -> "Learner 단계"
                        tier.first.startsWith("P") -> "Provisional 단계"
                        else -> "Full 단계"
                    },
                    26, color = hy.sub,
                )
            }
        }
        PixelGauge(base = p, gained = p, modifier = Modifier.fillMaxWidth().padding(top = 18.u))
        HText(
            "XP ${u.xp}" + (next?.let { " · ${it.first}까지 ${it.second - u.xp}" } ?: " · 최고 단계"),
            26, Modifier.padding(top = 10.u), color = hy.sub,
        )

        Column(Modifier.padding(top = 26.u), verticalArrangement = Arrangement.spacedBy(14.u)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.u)) {
                Stat("누적 주행", "${u.drives}회", Modifier.weight(1f))
                Stat("연속 기록", "${u.streak}일", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.u)) {
                Stat("익힌 가이드", "${u.st.values.count { it.status == "graduated" }}개", Modifier.weight(1f))
                Stat("남은 가이드", "${remaining(u.st)}개", Modifier.weight(1f))
            }
        }
        VSpacer1()
        HButton("내 라이선스", vm::openLicense, Modifier.fillMaxWidth(), icon = HyIcons.Star)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(24.u)).background(hy.s2).padding(horizontal = 22.u, vertical = 18.u)) {
        HText(label, 24, color = hy.sub)
        HText(value, 40, weight = FontWeight.Bold, family = DisplayFont, lineHeight = 1.1f)
    }
}

private class Tower(val x: Float, val w: Float, val h: Float, val far: Boolean, val lit: List<Boolean>)

/** 밤 도시 픽셀 스카이라인 + 도로 위 자동차 (장식용). 격자 단위는 높이의 1/60. */
@Composable
private fun PixelSkyline(modifier: Modifier) {
    val pal = hy
    val towers = remember {
        val r = Random(7)
        var x = 0f
        buildList {
            while (x < 260f) {
                val w = 8f + r.nextInt(4) * 2f
                val h = 12f + r.nextInt(10) * 2f
                add(Tower(x, w, h, r.nextBoolean(), List(((w / 3).toInt()) * ((h / 4).toInt())) { r.nextFloat() < 0.22f }))
                x += w + r.nextInt(3)
            }
        }
    }
    Canvas(modifier) {
        val px = size.height / 60f
        val ground = size.height - 11f * px
        fun cell(c: Color, x: Float, y: Float, w: Float, h: Float) = drawRect(c, Offset(x * px, y), Size(w * px, h * px))
        // 건물 (글자가 놓이는 왼쪽 위를 피해서 아래쪽에만)
        towers.forEach { t ->
            val top = ground - t.h * px
            cell(if (t.far) pal.s1 else pal.s2, t.x, top, t.w, t.h)
            val cols = (t.w / 3).toInt()
            t.lit.forEachIndexed { i, on ->
                val cx = i % cols
                val cy = i / cols
                cell(if (on) pal.ok.copy(alpha = .85f) else pal.line.copy(alpha = .55f), t.x + 1f + cx * 3f, top + (2f + cy * 4f) * px, 1.4f, 1.6f)
            }
        }
        // 지면 · 도로 · 차선
        drawRect(pal.s1, Offset(0f, ground), Size(size.width, 11f * px))
        drawRect(pal.mapCase, Offset(0f, ground + 3f * px), Size(size.width, 8f * px))
        drawRect(pal.ok, Offset(0f, ground + 3f * px), Size(size.width, 0.5f * px))
        var lx = 0f
        while (lx * px < size.width) {
            cell(pal.line, lx, ground + 6.6f * px, 5f, 0.6f)
            lx += 10f
        }
        // 자동차
        val cx = size.width / px * 0.74f
        val cy = ground + 3.2f * px
        cell(pal.ok, cx, cy, 16f, 3.4f)
        cell(pal.ok, cx + 3f, cy - 2.6f * px, 9f, 2.6f)
        cell(pal.mapCase, cx + 4f, cy - 2f * px, 7f, 1.8f)
        cell(Color.White, cx + 8f, cy - 2f * px, 0.6f, 1.8f)
        cell(pal.amber, cx + 15f, cy + 1f * px, 1.4f, 1f)
        cell(pal.mapCase, cx + 2f, cy + 3f * px, 3f, 1.6f)
        cell(pal.mapCase, cx + 11f, cy + 3f * px, 3f, 1.6f)
    }
}
