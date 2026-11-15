package com.example.hymate.vehicle

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.reflect.Proxy

/**
 * AAOS CarUxRestrictions → 앱 주행 잠금.
 *
 * Pleos Connect(AAOS)에서 distractionOptimized 앱은 시스템이 주는 CarUxRestrictions를 직접 확인해
 * 제한을 적용해야 해요(Pleos 주행 규제 가이드 · 필수). android.car는 표준 Android SDK에 없어
 * 리플렉션으로 접근하고, 차량이 아닌 기기(개발용 태블릿·에뮬레이터)에선 조용히 비활성(available=false)돼
 * 기어(P) 폴백으로 동작해요. 값을 못 읽으면 안전하게 '제한됨'으로 둬요.
 *
 * @param onUx (available, requiresDistractionOptimization) — 메인 스레드로 전달돼요.
 */
class UxRestrictionsSource(
    context: Context,
    private val onUx: (Boolean, Boolean) -> Unit,
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var car: Any? = null
    @Volatile private var manager: Any? = null
    @Volatile private var listener: Any? = null

    private fun deliver(available: Boolean, requiresDO: Boolean) {
        main.post { onUx(available, requiresDO) }
    }

    /** 차량 서비스 연결은 느릴 수 있어 백그라운드에서 시도하고 결과만 메인으로 전달해요. */
    fun start() {
        Thread({ connect() }, "HyUx").start()
    }

    private fun connect() {
        try {
            val carCls = Class.forName("android.car.Car")
            val c = carCls.getMethod("createCar", Context::class.java).invoke(null, appContext)
                ?: return deliver(false, false)
            car = c
            val svc = carCls.getField("CAR_UX_RESTRICTION_SERVICE").get(null) as String
            val mgr = carCls.getMethod("getCarManager", String::class.java).invoke(c, svc)
                ?: return deliver(false, false)
            manager = mgr
            val listenerCls = Class.forName(
                "android.car.drivingstate.CarUxRestrictionsManager\$OnUxRestrictionsChangedListener",
            )
            val proxy = Proxy.newProxyInstance(listenerCls.classLoader, arrayOf(listenerCls)) { p, method, args ->
                when (method.name) {
                    "onUxRestrictionsChanged" -> { emit(args?.getOrNull(0)); null }
                    "hashCode" -> System.identityHashCode(p)
                    "equals" -> p === args?.getOrNull(0)
                    "toString" -> "HyUxListener"
                    else -> null
                }
            }
            listener = proxy
            mgr.javaClass.getMethod("registerListener", listenerCls).invoke(mgr, proxy)
            emit(mgr.javaClass.getMethod("getCurrentCarUxRestrictions").invoke(mgr)) // 초기값
        } catch (t: Throwable) {
            Log.i(TAG, "CarUxRestrictions 미사용(차량 아님/서비스 없음): ${t.message}")
            deliver(false, false)
        }
    }

    private fun emit(restrictions: Any?) {
        val requires = try {
            if (restrictions == null) true // 값을 못 받으면 안전하게 제한됨
            else restrictions.javaClass.getMethod("isRequiresDistractionOptimization").invoke(restrictions) as Boolean
        } catch (t: Throwable) {
            true
        }
        deliver(true, requires)
    }

    fun stop() {
        val mgr = manager
        try {
            if (mgr != null && listener != null) mgr.javaClass.getMethod("unregisterListener").invoke(mgr)
            car?.javaClass?.getMethod("disconnect")?.invoke(car)
        } catch (t: Throwable) {
            Log.i(TAG, "CarUxRestrictions 해제 중: ${t.message}")
        }
        car = null; manager = null; listener = null
    }

    private companion object {
        const val TAG = "HyUxRestrictions"
    }
}
