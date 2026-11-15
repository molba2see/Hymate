package com.example.hymate.vehicle

import android.annotation.SuppressLint
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat

/** Pleos SDK에 없는 차속을 AAOS에서 읽어요. 일반 기기에서는 Car 클래스를 로드하지 않아요. */
class CarSpeedSource(
    private val context: Context,
    private val onSpeed: (Float?, Long) -> Unit,
    private val onLink: (VehicleLink) -> Unit,
) {
    private interface Connection { fun start(); fun stop() }
    private var connection: Connection? = null

    fun start() {
        if (connection != null) return
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) ||
            Build.VERSION.SDK_INT < 29
        ) {
            onLink(VehicleLink.Unavailable("AAOS 차속 서비스를 사용할 수 없어요"))
            return
        }
        if (ContextCompat.checkSelfPermission(context, SPEED_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            onLink(VehicleLink.Unavailable("차량 속도 권한이 필요해요"))
            return
        }
        onLink(VehicleLink.Connecting)
        try {
            connection = AutomotiveConnection(context.applicationContext, onSpeed, onLink)
            connection?.start()
        } catch (e: Exception) {
            stop()
            onLink(VehicleLink.Unavailable(e.message ?: "차속 연결 실패"))
        } catch (e: LinkageError) {
            onLink(VehicleLink.Unavailable("AAOS 차속 라이브러리가 없어요"))
        }
    }

    fun stop() {
        connection?.stop()
        connection = null
        onSpeed(null, SystemClock.elapsedRealtimeNanos())
        onLink(VehicleLink.Simulated)
    }

    @androidx.annotation.RequiresApi(29)
    private class AutomotiveConnection(
        private val context: Context,
        private val onSpeed: (Float?, Long) -> Unit,
        private val onLink: (VehicleLink) -> Unit,
    ) : Connection {
        private val main = Handler(Looper.getMainLooper())
        private var car: Car? = null
        private var manager: CarPropertyManager? = null
        private var active = false
        private var receiving = false
        private val timeout = Runnable { unavailable("차속 수신이 중단됐어요") }

        private val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                if (!active || value.propertyId != VehiclePropertyIds.PERF_VEHICLE_SPEED) return
                val speed = value.value as? Float
                val now = SystemClock.elapsedRealtimeNanos()
                if (value.status != CarPropertyValue.STATUS_AVAILABLE || speed == null || !speed.isFinite() ||
                    value.timestamp <= 0L || now - value.timestamp !in 0..SPEED_TIMEOUT_NANOS
                ) {
                    unavailable("유효한 차속을 기다리고 있어요")
                    return
                }
                onSpeed(speed, value.timestamp)
                if (!receiving) {
                    receiving = true
                    onLink(VehicleLink.Connected)
                }
                main.removeCallbacks(timeout)
                main.postDelayed(timeout, SPEED_TIMEOUT_NANOS / 1_000_000)
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                if (propertyId == VehiclePropertyIds.PERF_VEHICLE_SPEED) unavailable("차속 조회 오류")
            }
        }

        @SuppressLint("MissingPermission")
        @Suppress("DEPRECATION")
        override fun start() {
            active = true
            // 차량 서비스 시작을 메인 스레드에서 기다리지 않아요.
            car = Car.createCar(context, main, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { service, ready ->
                if (!active) return@createCar
                if (!ready) {
                    manager = null
                    unavailable("차량 서비스 연결이 끊겼어요")
                    return@createCar
                }
                try {
                    if (ContextCompat.checkSelfPermission(context, SPEED_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
                        unavailable("차량 속도 권한이 필요해요")
                        return@createCar
                    }
                    manager?.unregisterCallback(callback)
                    val properties = service.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                    manager = properties
                    // 구형 Pleos 이미지도 지원. 동일 속도에서도 주기적으로 수신해 단절을 감지해요.
                    if (!properties.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, 10f)) {
                        unavailable("이 차량은 차속 조회를 지원하지 않아요")
                    }
                } catch (e: Exception) {
                    Log.w("HyVehicle", "차속 구독 실패", e)
                    unavailable(e.message ?: "차속 조회 실패")
                }
            }
        }

        private fun unavailable(reason: String) {
            if (!active) return
            receiving = false
            main.removeCallbacks(timeout)
            onSpeed(null, SystemClock.elapsedRealtimeNanos())
            onLink(VehicleLink.Unavailable(reason))
        }

        override fun stop() {
            active = false
            main.removeCallbacksAndMessages(null)
            try {
                manager?.unregisterCallback(callback)
            } catch (e: Exception) {
                Log.w("HyVehicle", "차속 구독 해제 실패", e)
            } finally {
                manager = null
                try {
                    car?.disconnect()
                } catch (e: Exception) {
                    Log.w("HyVehicle", "차량 서비스 해제 실패", e)
                }
                car = null
            }
        }
    }

    companion object {
        const val SPEED_PERMISSION = "android.car.permission.CAR_SPEED"
    }
}
