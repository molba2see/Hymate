package com.example.hymate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import com.example.hymate.ui.HyApp
import com.example.hymate.ui.theme.HyTheme
import com.example.hymate.vehicle.PleosVehicleSource
import com.example.hymate.vehicle.UxRestrictionsSource

class MainActivity : ComponentActivity() {
    private val vm: HyViewModel by viewModels()

    /** Pleos Connect Vehicle SDK → ViewModel. 화면이 보이는 동안만 연결해요. */
    private val vehicleSource by lazy {
        PleosVehicleSource(this, onSignal = vm::onVehicleSignal, onLink = vm::onVehicleLink)
    }

    /** AAOS CarUxRestrictions → ViewModel. 주행 중 화면 잠금을 시스템 규제에 맞춰요. */
    private val uxSource by lazy { UxRestrictionsSource(this, onUx = vm::onUxRestrictions) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                HyTheme { HyApp(vm) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vehicleSource.start()
        uxSource.start()
    }

    override fun onStop() {
        vehicleSource.stop()
        uxSource.stop()
        super.onStop()
    }
}
