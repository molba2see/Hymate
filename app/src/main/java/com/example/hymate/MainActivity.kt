package com.example.hymate

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.example.hymate.ui.HyApp
import com.example.hymate.ui.theme.HyTheme
import com.example.hymate.vehicle.CarSpeedSource
import com.example.hymate.vehicle.PleosVehicleSource
import com.example.hymate.vehicle.UxRestrictionsSource

class MainActivity : ComponentActivity() {
    private val vm: HyViewModel by viewModels()

    /** Pleos Connect Vehicle SDK(레버·벨트·기어 등) → ViewModel. 화면이 보이는 동안만 연결해요. */
    private val vehicleSource by lazy {
        PleosVehicleSource(
            this,
            onSignal = vm::onVehicleSignal,
            onLink = vm::onVehicleLink,
            onGuideSupport = vm::onGuideSupport,
        )
    }

    /** AAOS 차속(PERF_VEHICLE_SPEED) → ViewModel. 권한이 있어야 시작돼요. */
    private val speedSource by lazy {
        CarSpeedSource(this, onSpeed = vm::onVehicleSpeed, onLink = vm::onSpeedLink)
    }

    /** AAOS CarUxRestrictions → ViewModel. 주행 중 화면 잠금을 시스템 규제에 맞춰요. */
    private val uxSource by lazy { UxRestrictionsSource(this, onUx = vm::onUxRestrictions) }

    private var permissionDialog: AlertDialog? = null

    private val speedPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            speedSource.start()
        } else if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            showSpeedPermissionDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                HyTheme { HyApp(vm, onRequestVehicleAccess = ::requestVehicleAccess) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vehicleSource.start()
        uxSource.start()
        // 권한이 이미 있으면 백그라운드 복귀 시 차속 구독을 이어가요.
        if (hasSpeedPermission()) speedSource.start()
    }

    override fun onStop() {
        permissionDialog?.dismiss()
        permissionDialog = null
        speedSource.stop()
        vehicleSource.stop()
        uxSource.stop()
        super.onStop()
    }

    /** 주행 화면에 들어오면 차속 권한을 확인하고, 있으면 차속 구독을 시작해요. */
    private fun requestVehicleAccess() {
        if (hasSpeedPermission()) speedSource.start()
        else speedPermission.launch(CarSpeedSource.SPEED_PERMISSION)
    }

    private fun hasSpeedPermission() =
        ContextCompat.checkSelfPermission(this, CarSpeedSource.SPEED_PERMISSION) == PackageManager.PERMISSION_GRANTED

    private fun showSpeedPermissionDialog() {
        permissionDialog?.dismiss()
        permissionDialog = AlertDialog.Builder(this)
            .setTitle("차량 속도 권한")
            .setMessage("실제 주행 판정을 위해 차량 속도 접근 권한이 필요해요. 설정에서 허용해 주세요.")
            .setPositiveButton("설정 열기") { _, _ ->
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
                )
            }
            .setNegativeButton("나중에", null)
            .show()
    }
}
