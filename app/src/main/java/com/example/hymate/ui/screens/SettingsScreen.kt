package com.example.hymate.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.example.hymate.HyViewModel
import com.example.hymate.VoiceUi
import com.example.hymate.ui.components.HButton
import com.example.hymate.ui.components.ArcadeTag
import com.example.hymate.ui.components.Eyebrow
import com.example.hymate.ui.components.GleoOrb
import com.example.hymate.ui.components.H1
import com.example.hymate.ui.components.HCard
import com.example.hymate.ui.components.HChip
import com.example.hymate.ui.components.HIcon
import com.example.hymate.ui.components.HSeg
import com.example.hymate.ui.components.HText
import com.example.hymate.ui.components.SquareButton
import com.example.hymate.ui.theme.HyIcons
import com.example.hymate.ui.theme.hy
import com.example.hymate.ui.theme.mix
import com.example.hymate.ui.theme.u

@Composable
fun SettingsScreen(vm: HyViewModel) {
    val c = vm.consents
    ScreenFrame {
        Column(verticalArrangement = Arrangement.spacedBy(32.u)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.u)) {
                SquareButton(HyIcons.ChevL, vm::back)
                Column(Modifier.weight(1f)) {
                    ArcadeTag("00 / SETTINGS", Modifier.padding(bottom = 6.u))
                    Eyebrow("동승 설정", hy.gleo)
                    H1("동의는 필요한 순간에, 고른 것만")
                }
                HChip("Hy! mate 음성", HyIcons.Mic, color = hy.gleo, bg = mix(hy.gleo, 0.16f, hy.s2))
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(40.u)) {
                Column(
                    Modifier.weight(960f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.u),
                ) {
                    if (vm.emulatorDemoAvailable) {
                        SettingItem(
                            "지도 위치 · 다음 주행",
                            "국민대 출발 데모는 매번 0m에서 시작해 차량 속도로 이동해요. 실제 GPS는 차량의 현재 위치를 표시해요.",
                        ) {
                            HSeg(
                                listOf("demo" to "국민대 출발 데모", "gps" to "실제 GPS"),
                                if (vm.demoRoute) "demo" else "gps",
                                { vm.selectDemoRoute(it == "demo") }, Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    SettingItem(
                        "음성 안내·응답 테스트",
                        "질문을 끝까지 들은 뒤 ‘응’ 또는 ‘아니’라고 말해 보세요. 듣는 중에만 차량 마이크를 사용해요.",
                    ) {
                        HText(vm.voiceTestStatus, 28, color = hy.sub)
                        HButton(
                            if (vm.voiceTestRunning) "테스트 중단" else "음성 테스트",
                            if (vm.voiceTestRunning) vm::cancelVoice else vm::testVoice,
                            icon = HyIcons.Mic, height = 88, textSize = 30,
                        )
                        if ((vm.voice as? VoiceUi.Question)?.listening == true) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.u)) {
                                HButton("응", { vm.answer(true) }, height = 80, textSize = 28)
                                HButton("아니", { vm.answer(false) }, height = 80, textSize = 28)
                            }
                        }
                    }
                    SettingItem(
                        "정차 중 실시간 피드백",
                        "브레이크를 3초 넘게 밟고 서 있을 때, 직전 구간에서 놓친 가이드 1개를 Hy! mate가 위쪽에 짧게 알려줘요. 주행 중에 따로 묻지 않아요.",
                    ) { HSeg(listOf("off" to "끄기", "on" to "켜기"), c.feedback, { vm.setConsent("feedback", it) }, Modifier.fillMaxWidth()) }
                    SettingItem(
                        "비·야간 같은 상황 가이드",
                        "주행 중에 비가 오거나 어두워지면, 그 상황의 가이드를 오늘 퀘스트에 넣을지 정해요. 동의 전 기록은 차 안에만 잠시 둬요.",
                    ) { HSeg(listOf("off" to "제외", "on" to "포함", "ask" to "Hy! mate가 물어보기"), c.cond, { vm.setConsent("cond", it) }, Modifier.fillMaxWidth()) }
                    SettingItem(
                        "어려운 지점 접근 알림음",
                        "전에 놓친 합류·분기 지점 200m 전에 ‘띵’ 소리만 내요. 지점이나 내용은 말하지 않아요. 처음 기본값은 꺼짐이고, 여기서 켠 경우에만 울려요.",
                    ) { HSeg(listOf("off" to "끄기", "on" to "켜기"), c.chime, { vm.setConsent("chime", it) }, Modifier.fillMaxWidth()) }
                    SettingItem("퀘스트 판정", null) { Fixed("매 주행 출발 전 ‘퀘스트 시작’을 눌렀을 때만 판정하고 기록해요") }
                    SettingItem("서비스 성격 안내", "운전 보조와 학습을 위한 서비스예요. 운전 판단의 책임은 운전자에게 있어요.") {
                        Fixed("확인함 · 2026.11.04 08:05 · v0.3.0")
                    }
                }
                Column(Modifier.weight(588f), verticalArrangement = Arrangement.spacedBy(24.u)) {
                    HCard(
                        Modifier.fillMaxWidth(),
                        color = mix(hy.gleo, 0.12f, hy.s1),
                        borderColor = mix(hy.gleo, 0.5f, Color.Transparent),
                        padding = PaddingValues(horizontal = 32.u, vertical = 28.u),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.u)) {
                            GleoOrb(110, breathe = false)
                            HText("“국민대 정문 앞 교차로에서 좌회전 방향지시등을 30m 전에 켜셔야 해요.”", 32, Modifier.weight(1f), weight = FontWeight.SemiBold, lineHeight = 1.4f)
                        }
                    }
                    HCard(Modifier.fillMaxWidth()) {
                        HText("Hy! mate가 말하는 방식", 32, Modifier.padding(bottom = 20.u), weight = FontWeight.Bold)
                        Column(verticalArrangement = Arrangement.spacedBy(14.u)) {
                            Rule(HyIcons.Clock, "속도 0.5km/h 이하에서 브레이크를 3초 넘게 밟고 있을 때만 말해요.")
                            Rule(HyIcons.Speaker, "정차 한 번에 피드백 1개, 같은 가이드는 한 주행에 1번만 말해요.")
                            Rule(HyIcons.Play, "다시 출발하면 바로 멈춰요.")
                            Rule(HyIcons.Mic, "상황 가이드를 ‘Hy! mate가 물어보기’로 두면, 비가 올 때만 짧게 묻고 6초 동안 대답을 들어요.")
                        }
                    }
                    HCard(Modifier.fillMaxWidth().weight(1f)) {
                        HText("최근 동의 기록", 30, Modifier.padding(bottom = 16.u), weight = FontWeight.Bold)
                        Column(verticalArrangement = Arrangement.spacedBy(10.u)) {
                            vm.consentLog.takeLast(4).reversed().forEach { r ->
                                Column {
                                    HText("${r.item} · ${r.value}", 26, weight = FontWeight.SemiBold, maxLines = 1)
                                    HText(r.channel, 26, color = hy.sub, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingItem(title: String, desc: String?, control: @Composable () -> Unit) {
    HCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 36.u, vertical = 32.u)) {
        Column(verticalArrangement = Arrangement.spacedBy(20.u)) {
            HText(title, 38, weight = FontWeight.Bold)
            if (desc != null) HText(desc, 28, color = hy.sub, lineHeight = 1.45f)
            control()
        }
    }
}

@Composable
private fun Fixed(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.u)) {
        HIcon(HyIcons.Check, 36, hy.ok)
        HText(text, 28, color = hy.ok, weight = FontWeight.SemiBold)
    }
}

@Composable
private fun Rule(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.u)) {
        HIcon(icon, 34, hy.gleo, Modifier.padding(top = 4.u))
        HText(text, 28, color = hy.sub, lineHeight = 1.4f)
    }
}
