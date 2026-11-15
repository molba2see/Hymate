package com.example.hymate.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.hymate.HyViewModel
import com.example.hymate.PreGuide
import com.example.hymate.data.GD
import com.example.hymate.data.LV_NAME
import com.example.hymate.data.TODAY_KEY
import com.example.hymate.data.XP
import com.example.hymate.data.guide
import com.example.hymate.data.lvLabel
import com.example.hymate.data.remaining
import com.example.hymate.data.routeCount
import com.example.hymate.data.todayQuest
import com.example.hymate.ui.components.ArcadeTag
import com.example.hymate.ui.components.BtnKind
import com.example.hymate.ui.components.Eyebrow
import com.example.hymate.ui.components.H1
import com.example.hymate.ui.components.HButton
import com.example.hymate.ui.components.HCard
import com.example.hymate.ui.components.HChip
import com.example.hymate.ui.components.HIcon
import com.example.hymate.ui.components.HText
import com.example.hymate.ui.components.SquareButton
import com.example.hymate.ui.components.VSpacer1
import com.example.hymate.ui.theme.HyIcons
import com.example.hymate.ui.theme.OnOk
import com.example.hymate.ui.theme.hy
import com.example.hymate.ui.theme.mix
import com.example.hymate.ui.theme.u

@Composable
fun ScreenFrame(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(hy.app).padding(56.u)) { content() }
}

@Composable
fun PreDriveScreen(vm: HyViewModel) {
    val u = vm.user
    val list = vm.preGuides()
    ScreenFrame {
        Column(verticalArrangement = Arrangement.spacedBy(32.u)) {
            // ── 헤더 ──
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(40.u)) {
                Column(Modifier.weight(1f)) {
                    ArcadeTag("01 / PRE-DRIVE", Modifier.padding(bottom = 6.u))
                    Eyebrow("출발 전 가이드 · " + if (u.routeMemory) "자주 다닌 길 ${u.drives}회" else "처음 가는 길")
                    H1("국민대학교 → 현대자동차 양재 본사")
                    Row(Modifier.padding(top = 20.u), horizontalArrangement = Arrangement.spacedBy(16.u)) {
                        HChip("21.4km · 약 42분")
                        HChip(if (vm.rainy) "비 예보 · 08:30부터" else "맑음", if (vm.rainy) HyIcons.Rain else HyIcons.Sun)
                        HChip("주간", HyIcons.Sun)
                        HChip("남은 가이드 ${remaining(u.st)}개", color = hy.ok)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.u)) {
                    HButton("A씨", vm::openLicense, icon = HyIcons.User, trailing = HyIcons.ChevD, textSize = 32)
                    SquareButton(HyIcons.Gear, vm::openSettings)
                }
            }
            // ── 본문 ──
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(40.u)) {
                GuideList(vm, list, Modifier.weight(1000f))
                SidePanel(vm, Modifier.weight(548f))
            }
        }
    }
}

@Composable
private fun GuideList(vm: HyViewModel, list: List<PreGuide>, modifier: Modifier) {
    val learning = list.count { it.tag != "grad" && it.tag != "off" && it.tag != "exp" }
    val grad = list.count { it.tag == "grad" }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.u)) {
        ArcadeTag("QUEST LOG")
        Row(verticalAlignment = Alignment.Bottom) {
            HText("오늘 챙길 가이드 ${list.size}개", 34, Modifier.weight(1f), weight = FontWeight.Bold)
            HText("익히는 중 ${learning}개 · 졸업 ${grad}개 · 눌러서 연습해요", 28, color = hy.sub, weight = FontWeight.Medium)
        }
        val grouped = list.groupBy { guide(it.key).lv }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.u)) {
            grouped.forEach { (lv, items) ->
                stickyHeader(key = "lv$lv") {
                    Row(
                        Modifier.fillMaxWidth().background(hy.app).padding(start = 8.u, end = 8.u, top = 12.u, bottom = 4.u),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(16.u),
                    ) {
                        HText(lvLabel(lv), 26, weight = FontWeight.Black)
                        HText(LV_NAME[lv].orEmpty(), 28, color = hy.sub, weight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        val g = items.count { it.tag == "grad" }
                        HText("${items.size}개" + if (g > 0) " · 졸업 $g" else "", 24, color = hy.sub)
                    }
                }
                items(items, key = { it.key }) { GuideRow(vm, it) }
            }
        }
    }
}

@Composable
private fun GuideRow(vm: HyViewModel, x: PreGuide) {
    val g = guide(x.key)
    val (tagText, tagColor) = when (x.tag) {
        "fail" -> "지난 주행에서 놓쳤어요" to hy.bad
        "ret" -> "한동안 잘하다가 다시 놓쳤어요" to hy.amber
        "new" -> "오늘 처음" to hy.blue
        "off" -> "비 오는 날에만 판정해요" to hy.sub
        "grad" -> "졸업 · 판정은 계속해요" to hy.ok
        "cond" -> "오늘 비 예보로 추가됐어요" to hy.gleo
        "exp" -> "경험형 · 판정하지 않아요" to hy.gleo
        else -> "익히는 중" to hy.sub
    }
    val known = vm.user.routeMemory || x.key in setOf("RAIN", "RAIN_SLOW", "UNPROT_TIME", "HEADLIGHT", "SEATBELT", "SPEED", "HS_NSCC", "HS_GLEO", "HS_RSPA")
    val sub = if (known) routeCount(x.key, vm.rainy) else "처음 가는 길이라 지점 수는 주행 중에 확인해요"
    val done = vm.practiceState(x.key).done
    val hasPractice = GD[x.key]?.practice?.isNotEmpty() == true
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (x.tag == "grad" || x.tag == "off") 0.6f else 1f)
            .clip(RoundedCornerShape(28.u))
            .background(hy.s1)
            .clickable { vm.openGuide(x.key) }
            .padding(start = 30.u, end = 20.u, top = 18.u, bottom = 18.u),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.u),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.u)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.u)) {
                Box(Modifier.clip(RoundedCornerShape(10.u)).background(hy.s2).padding(horizontal = 12.u, vertical = 2.u)) {
                    HText(g.lvLabel, 22, color = hy.sub, weight = FontWeight.Bold)
                }
                HText(tagText, 24, color = tagColor, weight = FontWeight.Bold)
            }
            HText(g.title, 34, weight = FontWeight.Bold, lineHeight = 1.25f, maxLines = 1)
            HText(sub, 26, color = hy.sub, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.u)) {
            val chipColor = if (done) hy.ok else hy.blue
            Box(Modifier.clip(RoundedCornerShape(12.u)).background(mix(chipColor, 0.18f, hy.s2)).padding(horizontal = 14.u, vertical = 6.u)) {
                HText(
                    if (done) (if (hasPractice) "연습 완료" else "확인 완료") else if (hasPractice) "주차 중 연습" else "내용 확인",
                    22, color = chipColor, weight = FontWeight.Bold,
                )
            }
            HText(g.src, 24, color = hy.sub, maxLines = 1)
        }
        Box(Modifier.size(56.u).clip(RoundedCornerShape(18.u)).background(hy.s2), contentAlignment = Alignment.Center) {
            HIcon(HyIcons.ChevR, 34)
        }
    }
}

@Composable
private fun SidePanel(vm: HyViewModel, modifier: Modifier) {
    val t = todayQuest(vm.rainy)
    val ts = vm.practiceState(TODAY_KEY)
    val tdone = if (ts.done) t.practice.size else ts.step
    val c = vm.consents
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.u)) {
        // 오늘의 실습 퀘스트
        val accent = if (ts.done) hy.ok else hy.blue
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.u))
                .background(
                    if (ts.done) Brush.linearGradient(listOf(mix(hy.ok, 0.14f, hy.s1), mix(hy.ok, 0.14f, hy.s1)))
                    else Brush.linearGradient(listOf(mix(hy.blue, 0.26f, hy.s1), mix(hy.gleo, 0.22f, hy.s1))),
                )
                .border(3.u, mix(accent, 0.55f, Color.Transparent), RoundedCornerShape(32.u))
                .clickable { vm.openGuide(TODAY_KEY) }
                .padding(horizontal = 28.u, vertical = 22.u),
            verticalArrangement = Arrangement.spacedBy(10.u),
        ) {
            Row {
                HText("오늘의 실습 퀘스트 · 주차 중에만", 24, Modifier.weight(1f), color = accent, weight = FontWeight.Bold)
                HText("+${XP.PRAC} XP", 24, color = accent, weight = FontWeight.Bold)
            }
            HText(t.title, 36, weight = FontWeight.Bold, lineHeight = 1.25f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.u)) {
                    repeat(t.practice.size) { i ->
                        Box(Modifier.width(48.u).height(12.u).clip(RoundedCornerShape(8.u)).background(if (i < tdone) hy.ok else hy.s2))
                    }
                }
                Row(
                    Modifier.height(80.u).clip(RoundedCornerShape(24.u)).background(accent).padding(horizontal = 30.u),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.u),
                ) {
                    val fg = if (ts.done) OnOk else Color.White
                    HIcon(if (ts.done) HyIcons.Check else HyIcons.Play, 32, fg)
                    HText(if (ts.done) "완료" else if (tdone > 0) "이어서 하기" else "지금 해보기", 30, color = fg, weight = FontWeight.Bold)
                }
            }
        }

        // 동의 상태
        HCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 28.u, vertical = 20.u)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HText("동의 상태", 30, Modifier.weight(1f), weight = FontWeight.Bold)
                HButton("바꾸기", vm::openSettings, kind = BtnKind.Ghost, height = 72, textSize = 28, hPad = 12)
            }
            ConsentRow("정차 중 피드백", c.feedback)
            ConsentRow("상황 가이드", c.cond, cond = true)
            ConsentRow("접근 알림음", c.chime)
        }

        VSpacer1()

        // 시작
        Column(verticalArrangement = Arrangement.spacedBy(10.u)) {
            HButton("퀘스트 시작", { vm.startDrive(true) }, Modifier.fillMaxWidth(), icon = HyIcons.Play, kind = BtnKind.Primary, textSize = 42)
            HButton("기록 없이 출발", { vm.startDrive(false) }, Modifier.fillMaxWidth(), height = 88, textSize = 28, hPad = 16, container = hy.s2, content = hy.sub)
            HText("출발하면 Hy! mate는 화면을 쓰지 않아요. 판정은 뒤에서 해요.", 24, Modifier.fillMaxWidth(), color = hy.sub, align = TextAlign.Center)
        }
    }
}

@Composable
fun ConsentRow(label: String, v: String, cond: Boolean = false) {
    val (text, color) = when (v) {
        "on" -> (if (cond) "포함" else "켜짐") to hy.ok
        "off" -> (if (cond) "제외" else "꺼짐") to hy.sub
        else -> "Hy! mate가 물어보기" to hy.gleo
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.u), verticalAlignment = Alignment.CenterVertically) {
        HText(label, 28, Modifier.weight(1f))
        HText(text, 28, color = color, weight = FontWeight.Bold)
    }
}
