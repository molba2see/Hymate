package com.example.hymate.vehicle

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SeatBeltNotBuckledWarningListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import com.example.hymate.data.Vehicle
import ai.pleos.playground.vehicle.Vehicle as PleosVehicle

/** 차량 데이터 연결 상태 */
sealed interface VehicleLink {
    /** SDK를 아직 시작하지 않음 — 버튼 시뮬레이션만 */
    data object Simulated : VehicleLink
    data object Connecting : VehicleLink
    /** Vehicle Service에서 값을 한 번 이상 받음 */
    data object Connected : VehicleLink
    /** Pleos 차량·에뮬레이터가 아니거나 권한·서비스 문제 */
    data class Unavailable(val reason: String) : VehicleLink
}

/**
 * Pleos Connect Vehicle SDK(v2.3.2) → 앱의 [Vehicle] 상태.
 *
 * - 방향지시등 · 브레이크 페달 · 운전석 안전벨트 경고 · 기어: 콜백으로 받아요.
 * - 전조등 · 와이퍼의 **스위치 위치**(AUTO 여부)는 SDK에 변화 콜백이 없어서,
 *   점등/작동 상태 콜백이 올 때와 1초 간격 조회로 읽어요.
 * - 차속은 Vehicle SDK에 없어요. (AAOS CarPropertyManager 등 별도 연동 필요)
 *
 * 콜백 스레드가 문서에 명시돼 있지 않아서 모든 결과를 메인 스레드로 넘겨요.
 */
class PleosVehicleSource(
    context: Context,
    private val onSignal: ((Vehicle) -> Vehicle) -> Unit,
    private val onLink: (VehicleLink) -> Unit,
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var vehicle: PleosVehicle? = null
    private var connected = false

    private val driverSeat = SeatArea(SeatValue.ROW_1_LEFT)
    private val windshield = WindowArea(WindowValue.FRONT_WINDSHIELD)

    private val turnListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) = update { it.copy(signal = state.toApp(it.signal)) }
        override fun onFailed(e: Exception) = failed("TurnSignal", e)
    }
    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(pressed: Boolean?) = update { it.copy(brake = pressed ?: it.brake) }
        override fun onFailed(e: Exception) = failed("Brake", e)
    }
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = update { it.copy(parked = gear.isParked(it.parked)) }
        override fun onFailed(e: Exception) = failed("Gear", e)
    }
    private val beltListener = object : SeatBeltNotBuckledWarningListener {
        // 경고가 켜져 있으면 벨트를 매지 않은 상태
        override fun onSeatBeltNotBuckledWarningUpdated(warning: Boolean?) = update { it.copy(belt = warning?.not() ?: it.belt) }
        override fun onFailed(e: Exception) = failed("Safety", e)
    }
    // 점등·작동 상태가 바뀌면 스위치 위치도 바뀌었을 수 있으니 바로 다시 읽어요
    private val lightListener = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) { main.post { readSwitches() } }
        override fun onFailed(e: Exception) = failed("Light", e)
    }
    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) { main.post { readSwitches() } }
        override fun onFailed(e: Exception) = failed("Wiper", e)
    }

    private val poll = object : Runnable {
        override fun run() {
            readSwitches()
            // 연결 전에는 느리게 다시 시도해요
            main.postDelayed(this, if (connected) 1_000L else 5_000L)
        }
    }

    fun start() {
        if (vehicle != null) return
        onLink(VehicleLink.Connecting)
        try {
            val v = PleosVehicle(appContext)
            v.initialize()
            vehicle = v
            v.getTurnSignal().registerTurnSignalState(turnListener)
            v.getBrake().registerBrakePedalStatus(brakeListener)
            v.getSafety().registerSeatBeltNotBuckledWarning(driverSeat, beltListener)
            v.getLight().registerLowBeamLightState(lightListener)
            v.getWiper().registerWiperState(windshield, wiperListener)
            v.getGear().registerGearSelection(gearListener)

            v.getGear().getCurrentGearSelectionState(
                { g -> update { it.copy(parked = g.isParked(it.parked)) } },
                { e -> failed("Gear", e) },
            )
            v.getTurnSignal().getCurrentTurnSignalState(
                { s -> update { it.copy(signal = s.toApp(it.signal)) } },
                { e -> failed("TurnSignal", e) },
            )
            v.getBrake().getCurrentBrakePedalStatus(
                { p -> update { it.copy(brake = p ?: it.brake) } },
                { e -> failed("Brake", e) },
            )
            v.getSafety().hasSeatBeltNotBuckledWarning(
                driverSeat,
                { w -> update { it.copy(belt = w?.not() ?: it.belt) } },
                { e -> failed("Safety", e) },
            )
            main.post(poll)
        } catch (t: Throwable) {
            Log.w(TAG, "Vehicle SDK를 시작하지 못했어요", t)
            vehicle = null
            onLink(VehicleLink.Unavailable(t.message ?: t.javaClass.simpleName))
        }
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        val v = vehicle ?: return
        vehicle = null
        connected = false
        try {
            v.getTurnSignal().unregisterTurnSignalState(turnListener)
            v.getBrake().unregisterBrakePedalStatus(brakeListener)
            v.getSafety().unregisterSeatBeltNotBuckledWarning(driverSeat, beltListener)
            v.getLight().unregisterLowBeamLightState(lightListener)
            v.getWiper().unregisterWiperState(windshield, wiperListener)
            v.getGear().unregisterGearSelection(gearListener)
            v.release()
        } catch (t: Throwable) {
            Log.w(TAG, "Vehicle SDK 해제 중 오류", t)
        }
        onLink(VehicleLink.Simulated)
    }

    private fun readSwitches() {
        val v = vehicle ?: return
        try {
            v.getLight().getLowBeamLightSwitch(
                { sw -> update { it.copy(headlight = sw.toApp(it.headlight)) } },
                { e -> failed("Light", e) },
            )
            v.getWiper().getCurrentWiperSwitch(
                windshield,
                { sw -> update { it.copy(wiper = sw.toApp(it.wiper)) } },
                { e -> failed("Wiper", e) },
            )
        } catch (t: Throwable) {
            failed("switch", if (t is Exception) t else RuntimeException(t))
        }
    }

    /** 값을 받았다 = 연결됨. 메인 스레드에서 앱 상태에 반영해요. */
    private fun update(change: (Vehicle) -> Vehicle) {
        main.post {
            if (vehicle == null) return@post
            if (!connected) {
                connected = true
                onLink(VehicleLink.Connected)
            }
            onSignal(change)
        }
    }

    private fun failed(what: String, e: Exception) {
        Log.w(TAG, "$what 조회 실패: ${e.message}")
        main.post {
            if (vehicle != null && !connected) onLink(VehicleLink.Unavailable(e.message ?: e.javaClass.simpleName))
        }
    }

    private companion object {
        const val TAG = "HyVehicle"
    }
}

private fun TurnSignalValue?.toApp(prev: String) = when (this) {
    TurnSignalValue.LEFT -> "L"
    TurnSignalValue.RIGHT -> "R"
    TurnSignalValue.OFF -> "OFF"
    null -> prev
}

/** 기어를 모르면(null · UNKNOWN) 이전 값을 유지해요 */
private fun VehicleGear?.isParked(prev: Boolean) = when (this) {
    VehicleGear.GEAR_PARK -> true
    null, VehicleGear.GEAR_UNKNOWN -> prev
    else -> false
}

private fun LightSwitch?.toApp(prev: String) = when (this) {
    LightSwitch.AUTOMATIC -> "AUTO"
    LightSwitch.ON -> "ON"
    LightSwitch.OFF, LightSwitch.DAYTIME_RUNNING -> "OFF"
    null -> prev
}

private fun WiperSwitch?.toApp(prev: String) = when (this) {
    WiperSwitch.AUTO -> "AUTO"
    WiperSwitch.OFF -> "OFF"
    null, WiperSwitch.OTHER, WiperSwitch.SERVICE -> prev
    else -> "LOW" // MIST · 간헐 · 연속 = 수동으로 작동 중
}
