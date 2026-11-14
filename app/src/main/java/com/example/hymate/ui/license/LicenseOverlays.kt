package com.example.hymate.ui.license

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.hymate.HyViewModel
import com.example.hymate.data.TIERS
import com.example.hymate.data.UserProfile
import com.example.hymate.data.tierOf
import com.example.hymate.ui.components.BtnKind
import com.example.hymate.ui.components.HButton
import com.example.hymate.ui.components.HSeg
import com.example.hymate.ui.components.HText
import com.example.hymate.ui.components.SpinningRays
import com.example.hymate.ui.theme.DisplayFont
import com.example.hymate.ui.theme.HyIcons
import com.example.hymate.ui.theme.u

private fun UserProfile.licenseStats() = LicenseStats(
    guides = st.values.count { it.status == "graduated" },
    drives = drives,
    streak = streak,
)

private val Scrim = Color(0xDB04050A)

@Composable
private fun Modifier.blockTouches() = clickable(remember { MutableInteractionSource() }, indication = null) {}

/** 주차 후 승급 시 라이선스 발급 세리머니 (주차 중에만 표시) */
@Composable
fun LicenseCeremony(vm: HyViewModel) {
    val code = vm.ceremony ?: return
    var big by remember { mutableStateOf(false) }
    val fam = when (code.first()) { 'L' -> "Learner"; 'P' -> "Provisional"; else -> "Full" }
    Box(Modifier.fillMaxSize().background(Scrim).blockTouches(), contentAlignment = Alignment.Center) {
        SpinningRays(Modifier.requiredSize(2400.u))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(40.u)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HText("새 라이선스가 나왔어요 · $code", 56, color = Color.White, weight = FontWeight.Bold, align = TextAlign.Center)
                HText("$fam 단계 · 주차 중에만 볼 수 있어요", 30, Modifier.padding(top = 10.u), color = Color(0xFFC9CBE0), weight = FontWeight.Medium)
            }
            LicenseCard(code, if (big) 1000.u else 832.u, vm.user.licenseStats(), idle = false, enter = true)
            Row(horizontalArrangement = Arrangement.spacedBy(20.u)) {
                HButton(if (big) "작게 보기" else "크게 보기", { big = !big }, icon = HyIcons.Star)
                HButton("실물 카드 신청", { vm.showToast("실물 카드 신청은 주차 중 앱에서 이어서 할 수 있어요") })
                HButton("다음 주행 준비", vm::nextDrive, kind = BtnKind.Primary)
            }
        }
    }
}

/** 프로필 버튼 → 내 라이선스 (드래그해서 홀로그램 확인) + 데모용 운전자 전환 */
@Composable
fun LicenseSheet(vm: HyViewModel) {
    if (!vm.licenseOpen) return
    val tier = tierOf(vm.user.xp)?.first // 첫 라이선스(L0) 전이면 null
    Box(Modifier.fillMaxSize().background(Scrim).clickable(remember { MutableInteractionSource() }, null, onClick = vm::closeLicense), contentAlignment = Alignment.Center) {
        Column(
            Modifier.blockTouches(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.u),
        ) {
            HText(if (tier != null) "내 라이선스 · $tier" else "아직 라이선스가 없어요", 48, color = Color.White, weight = FontWeight.Bold)
            LicenseCard(tier ?: TIERS.first().first, 780.u, vm.user.licenseStats(), modifier = Modifier.alpha(if (tier != null) 1f else 0.35f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.u)) {
                TIERS.forEach { (t, _) ->
                    val col = when (t.first()) { 'L' -> Color(0xFF7FE8CF) to Color(0xFF215A4D); 'P' -> Color(0xFFA99CFF) to Color(0xFF3B3478); else -> Color(0xFFF2CF7C) to Color(0xFF5B4A22) }
                    Box(
                        Modifier.clip(RoundedCornerShape(12.u))
                            .background(if (t == tier) col.second else Color.Transparent)
                            .border(2.u, col.second, RoundedCornerShape(12.u))
                            .padding(horizontal = 16.u, vertical = 6.u),
                    ) { HText(t, 24, color = col.first, weight = FontWeight.Bold, family = DisplayFont) }
                }
            }
            HText(
                if (tier != null) "XP ${vm.user.xp} · 카드를 손가락으로 움직여 보세요"
                else "XP ${vm.user.xp} · ${TIERS.first().second} XP를 모으면 첫 라이선스 ${TIERS.first().first}가 발급돼요",
                26, color = Color(0xFFC9CBE0),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.u)) {
                HSeg(
                    listOf("L0" to "첫 주행 · 처음 가는 길", "L2" to "10번째 주행 · 자주 다닌 길"),
                    vm.profile, vm::switchProfile, Modifier.requiredSize(900.u, 112.u), gleoValue = null,
                )
                HButton("닫기", vm::closeLicense, kind = BtnKind.Default)
            }
        }
    }
}
