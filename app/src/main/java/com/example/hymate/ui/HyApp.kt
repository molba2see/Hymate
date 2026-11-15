package com.example.hymate.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import com.example.hymate.HyViewModel
import com.example.hymate.Screen
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import com.example.hymate.ui.components.HButton
import com.example.hymate.ui.components.HText
import com.example.hymate.ui.components.HyMark
import com.example.hymate.ui.theme.HyIcons
import com.example.hymate.ui.license.LicenseCeremony
import com.example.hymate.ui.license.LicenseSheet
import com.example.hymate.ui.screens.DriveScreen
import com.example.hymate.ui.screens.GuideDetailScreen
import com.example.hymate.ui.screens.HomeScreen
import com.example.hymate.ui.screens.PreDriveScreen
import com.example.hymate.ui.screens.ReportScreen
import com.example.hymate.ui.screens.SettingsScreen
import com.example.hymate.ui.theme.hy
import com.example.hymate.ui.theme.u

/** 읽을거리·조작이 많아서 실제 차량이 주행 중(기어 P 아님)일 때는 잠그는 화면 */
private val ParkedOnly = setOf(Screen.Settings, Screen.Guide, Screen.Report)

/** 주행 중 잠금: 시스템 차단 화면 대신 앱이 스스로 가려요. 기어를 P에 두면 바로 풀려요. */
@Composable
private fun DrivingLock(vm: HyViewModel) {
    Box(
        Modifier.fillMaxSize().background(hy.app)
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.u)) {
            HyMark(120, stop = true)
            HText("주행 중이에요", 56, weight = FontWeight.Bold)
            HText("설정·연습·리포트는 주차(P)하면 이어서 볼 수 있어요. 판정은 뒤에서 계속해요.", 32, color = hy.sub)
            if (vm.screen != Screen.Report) HButton("돌아가기", vm::back, Modifier.padding(top = 16.u), icon = HyIcons.ChevL)
        }
    }
}

/**
 * Hy! mate 앱 영역(application 1700×1184 · radius 32 · padding 56).
 * 상태 표시줄 · 좌측 driving view · 하단 GNB는 시스템 UI라 앱이 그리지 않아요.
 */
@Composable
fun HyApp(vm: HyViewModel, onRequestVehicleAccess: () -> Unit = {}) {
    LaunchedEffect(vm.screen) {
        if (vm.screen == Screen.Drive) onRequestVehicleAccess()
    }
    BackHandler(enabled = vm.canGoBack()) { vm.back() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(32.u)).background(hy.app)) {
            Crossfade(vm.screen, animationSpec = tween(250), label = "screen") { s ->
                when (s) {
                    Screen.Home -> HomeScreen(vm)
                    Screen.Settings -> SettingsScreen(vm)
                    Screen.Pre -> PreDriveScreen(vm)
                    Screen.Guide -> GuideDetailScreen(vm)
                    Screen.Drive -> DriveScreen(vm, onRequestVehicleAccess)
                    Screen.Report -> ReportScreen(vm)
                }
            }
            if (vm.driving && vm.screen in ParkedOnly) DrivingLock(vm)
            LicenseSheet(vm)
            LicenseCeremony(vm)
        }
        AnimatedVisibility(
            visible = vm.toast != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.u),
            enter = fadeIn(), exit = fadeOut(),
        ) {
            Box(
                Modifier.shadow(20.u, RoundedCornerShape(24.u)).clip(RoundedCornerShape(24.u))
                    .background(Color(0xFF1D2029)).padding(horizontal = 32.u, vertical = 20.u),
            ) { HText(vm.toast.orEmpty(), 28, color = Color.White) }
        }
    }
}
