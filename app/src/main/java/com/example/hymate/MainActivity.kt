package com.example.hymate

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.content.pm.ApplicationInfo
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
import com.example.hymate.speech.PleosSpeechEngine
import com.example.hymate.ui.HyApp
import com.example.hymate.ui.theme.HyTheme
import com.example.hymate.vehicle.CarSpeedSource
import com.example.hymate.vehicle.LocationSource
import com.example.hymate.vehicle.PleosVehicleSource
import com.example.hymate.vehicle.UxRestrictionsSource
import com.example.hymate.vehicle.VehicleLink

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

    private val speechEngine by lazy { PleosSpeechEngine(applicationContext) }

    /** Pleos FusedLocation → 경로 스냅. 왼쪽 차량 지도와 앱 지도의 위치를 맞춰요. */
    private val locationSource by lazy {
        LocationSource(this, onFix = vm::onLocationFix, onLink = vm::onLocationLink)
    }

    private var permissionDialog: AlertDialog? = null

    private val vehiclePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            startGrantedVehicleSources()
            val denied = result.filterValues { !it }.keys
            if (denied.any { !shouldShowRequestPermissionRationale(it) }) showVehiclePermissionSettings()
            else if (denied.isNotEmpty()) vm.showToast("차량 속도·위치 권한을 허용해야 해당 데이터를 연결할 수 있어요.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm.configureEmulatorDemo(
            applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
                (Build.HARDWARE in setOf("ranchu", "goldfish") || Build.FINGERPRINT.contains("crp_avd")),
        )
        setContent {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                HyTheme { HyApp(vm, onRequestVehicleAccess = ::requestVehicleAccess) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onNavigationVisibility(true)
        vm.attachSpeech(speechEngine)
        vehicleSource.start()
        uxSource.start()
        // 선언만으로 허용되지 않는 차속·위치 권한을 각각 확인해요.
        startGrantedVehicleSources()
    }

    override fun onStop() {
        vm.onNavigationVisibility(false)
        vm.detachSpeech(speechEngine)
        permissionDialog?.dismiss()
        permissionDialog = null
        speedSource.stop()
        vehicleSource.stop()
        uxSource.stop()
        locationSource.stop()
        super.onStop()
    }

    /** 주행 진입 또는 연결 재시도에서 필요한 권한만 요청해요. */
    private fun requestVehicleAccess() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            vm.onSpeedLink(VehicleLink.Unavailable("차량 서비스가 필요해요"))
            vm.onLocationLink(VehicleLink.Unavailable("차량 위치 서비스를 사용할 수 없어요"))
            return
        }
        startGrantedVehicleSources()
        val required = listOf(CarSpeedSource.SPEED_PERMISSION) +
            if (vm.driveUsesDemo) emptyList() else listOf(LocationSource.PERMISSION)
        val missing = required.filterNot(::hasPermission)
        if (missing.isEmpty()) return
        val preferences = getPreferences(MODE_PRIVATE)
        when {
            missing.any { shouldShowRequestPermissionRationale(it) } -> {
                permissionDialog?.dismiss()
                permissionDialog = AlertDialog.Builder(this)
                    .setTitle("차량 데이터 연결")
                    .setMessage("차량 속도는 주행·정차 판정에, 차량 위치는 지도 위치 동기화에 사용해요.")
                    .setPositiveButton("계속") { _, _ -> launchVehiclePermissions(missing) }
                    .setNegativeButton("나중에", null).show()
            }
            missing.any { preferences.getBoolean("requested:$it", false) } -> showVehiclePermissionSettings()
            else -> launchVehiclePermissions(missing)
        }
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun startGrantedVehicleSources() {
        if (hasPermission(CarSpeedSource.SPEED_PERMISSION)) speedSource.start()
        if (hasPermission(LocationSource.PERMISSION)) locationSource.start()
        else vm.onLocationLink(VehicleLink.Unavailable("차량 위치 권한이 필요해요"))
    }

    private fun launchVehiclePermissions(permissions: List<String>) {
        val editor = getPreferences(MODE_PRIVATE).edit()
        permissions.forEach { editor.putBoolean("requested:$it", true) }
        editor.apply()
        vehiclePermissions.launch(permissions.toTypedArray())
    }

    private fun showVehiclePermissionSettings() {
        permissionDialog?.dismiss()
        permissionDialog = AlertDialog.Builder(this)
            .setTitle("차량 데이터 권한")
            .setMessage("차량 속도·위치를 연결하려면 앱 설정에서 허용하지 않은 권한을 켜 주세요.")
            .setPositiveButton("설정 열기") { _, _ ->
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
                )
            }
            .setNegativeButton("나중에", null)
            .show()
    }
}
