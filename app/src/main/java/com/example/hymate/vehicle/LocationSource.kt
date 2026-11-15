package com.example.hymate.vehicle

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData

/**
 * Pleos FusedLocation(v1.1.3) → 차량 위경도.
 * 지도 스냅(경로 위 실제 위치 표시)에 써요. Pleos 환경이 아니면 조용히 Unavailable로 둬요.
 * 좌표는 지도 표시·경로거리 변환에 쓰고 저장하지 않아요. 지도 타일은 별도 렌더러가 요청해요.
 */
class LocationSource(
    context: Context,
    private val onFix: (Double, Double) -> Unit,
    private val onLink: (VehicleLink) -> Unit,
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var client: FusedLocation? = null
    private var received = false

    private val listener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            if (location == null) return
            main.post {
                if (client == null) return@post
                if (!received) {
                    received = true
                    onLink(VehicleLink.Connected)
                }
                onFix(location.latitude, location.longitude)
            }
        }

        override fun onError(exception: Exception) {
            Log.w(TAG, "위치 수신 실패: ${exception.message}")
            main.post {
                if (client != null && !received) onLink(VehicleLink.Unavailable(exception.message ?: "위치 오류"))
            }
        }
    }

    fun start() {
        if (client != null) return
        if (ContextCompat.checkSelfPermission(appContext, PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            onLink(VehicleLink.Unavailable("차량 위치 권한이 필요해요"))
            return
        }
        onLink(VehicleLink.Connecting)
        var starting: FusedLocation? = null
        try {
            val loc = FusedLocation(appContext)
            starting = loc
            loc.initialize()
            if (!loc.isSupported()) {
                loc.release()
                onLink(VehicleLink.Unavailable("위치 서비스 미지원"))
                return
            }
            client = loc
            loc.registerFusedLocationCallback(listener)
        } catch (t: Throwable) {
            Log.w(TAG, "FusedLocation 시작 실패", t)
            client = null
            runCatching { starting?.release() }
            onLink(VehicleLink.Unavailable(t.message ?: t.javaClass.simpleName))
        }
    }

    fun stop() {
        val loc = client ?: return
        client = null
        received = false
        try {
            loc.unregisterFusedLocationCallback(listener)
            loc.release()
        } catch (t: Throwable) {
            Log.w(TAG, "FusedLocation 해제 실패", t)
        }
        onLink(VehicleLink.Simulated)
    }

    companion object {
        const val PERMISSION = "pleos.car.permission.FUSED_LOCATION"
        private const val TAG = "HyLocation"
    }
}
