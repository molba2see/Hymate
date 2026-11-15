package com.example.hymate.vehicle

import com.example.hymate.data.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

data class QuestRouteState(val version: Int, val progress: Float, val valid: Boolean)

/** 실제 차량 관측과 탐색된 경로만 사용해요. 불충분한 자료는 주차 후 직접 체크로 넘겨요. */
class LiveQuestEvaluator {
    private val evaluated = mutableSetOf<String>()
    private val incomplete = mutableSetOf<String>()
    private val approachObserved = mutableSetOf<String>()
    private val indicatorObserved = mutableSetOf<String>()
    private val cancelStarted = mutableMapOf<String, Float>()
    private val peakSpeeds = mutableMapOf<String, Float>()
    private var direction: String? = null
    private var directionSince = 0f
    private var departedAt: Float? = null
    private var lastRouteVersion: Int? = null
    private var lastRouteProgress: Float? = null
    private var lastElapsed = 0f
    private var cruiseActiveSeconds = 0f
    private var rainSince: Float? = null
    private var tripPeak = 0f

    private var motionAt = 0L
    private var motionSpeed = 0f
    private var sawMoving = false
    private var brakingPeak = 0f
    private var stopCount = 0
    private var worstBraking = 0f
    private var startAt: Float? = null
    private var startingPeak = 0f
    private var startCount = 0
    private var worstStarting = 0f

    fun update(quests: List<Quest>, vehicle: Vehicle, session: LiveDriveSession, route: QuestRouteState? = null): List<Quest> {
        val dt = (session.elapsedSeconds - lastElapsed).coerceIn(0f, 1f)
        lastElapsed = session.elapsedSeconds
        if (VehicleSignal.CRUISE in vehicle.knownSignals && vehicle.cruise == "ACTIVE" && session.speedAvailable)
            cruiseActiveSeconds += dt
        if (session.speedAvailable) tripPeak = max(tripPeak, (vehicle.speedMps ?: 0f) * 3.6f)
        observeMotion(vehicle, session)

        if (route?.version != lastRouteVersion) {
            direction = null
            lastRouteProgress = null
            lastRouteVersion = route?.version
        }
        val distance = route?.progress ?: session.distanceMeters
        val previous = lastRouteProgress
        val jumped = route != null && previous != null && abs(distance - previous) > 70f
        lastRouteProgress = distance
        val knownTurn = VehicleSignal.TURN in vehicle.knownSignals
        val routeValid = route == null || route.valid
        if (!session.speedAvailable || !knownTurn || !routeValid || jumped) {
            direction = null
            directionSince = distance
        } else if (vehicle.signal != direction) {
            direction = vehicle.signal
            directionSince = distance
        }
        if (session.distanceMeters > 0 && departedAt == null) departedAt = session.elapsedSeconds

        return quests.map { q ->
            if (q.consent || q.notApplicable || q.selfCheck || q.id in evaluated) return@map q
            when (q.g) {
                "SMOOTH" -> return@map motionResult(q, stopCount, worstBraking, 11f / 3.6f, "감속")
                "SMOOTH_START" -> return@map motionResult(q, startCount, worstStarting, 10f / 3.6f, "가속")
                "HS_NSCC" -> return@map q // 일반 크루즈 작동을 NSCC 2 사용으로 단정하지 않아요.
            }
            if (q.g == "RAIN") {
                if (VehicleSignal.WIPER !in vehicle.knownSignals)
                    return@map q.copy(ev = "와이퍼 기반 강우 정보를 수신하지 못했어요")
                if (!vehicle.wiping) { rainSince = null; return@map q }
                val since = rainSince ?: session.elapsedSeconds.also { rainSince = it }
                if (session.elapsedSeconds - since < 10f) return@map q
                evaluated += q.id
                return@map if (vehicle.lowBeamOn == null) missing(q, "와이퍼 작동은 관측했지만 전조등 실제 점등을 수신하지 못했어요")
                else result(q, vehicle.lowBeamOn, "와이퍼 10초 이상 작동으로 우천 추정 · 실제 전조등 ${if (vehicle.lowBeamOn) "점등" else "꺼짐"}")
            }

            if (q.routeVersion != null && q.routeVersion != route?.version) return@map q
            val at = if (q.routeVersion != null) distance else session.distanceMeters
            if (q.routeVersion != null) {
                val approach = at in (q.d - 60f)..(q.d + 15f)
                val crossedByJump = jumped && previous != null && q.d.toFloat() in minOf(previous, at)..maxOf(previous, at)
                if ((approach && (!session.speedAvailable || !routeValid || (!knownTurn && q.g != "TURN_SPEED"))) || crossedByJump) incomplete += q.id
                if (!routeValid) return@map q
            }
            if (!session.speedAvailable) return@map q
            if (knownTurn && at in (q.d - 200f)..(q.d - 30f)) approachObserved += q.id
            if (knownTurn && at in (q.d - 60f)..(q.d + 15f) &&
                vehicle.signal != "OFF" && (q.dir == null || vehicle.signal == q.dir)) indicatorObserved += q.id
            if (q.g == "TURN_SPEED" && at in (q.d - 15f)..(q.d + 15f))
                peakSpeeds[q.id] = max(peakSpeeds[q.id] ?: 0f, (vehicle.speedMps ?: 0f) * 3.6f)
            val ready = when (q.g) {
                "SEATBELT" -> session.distanceMeters > 0
                "HEADLIGHT" -> departedAt?.let { session.elapsedSeconds - it >= 10f } == true
                "TURN_SPEED" -> at >= q.d + if (q.routeVersion != null) 15 else 0
                "SIG_CANCEL" -> at >= q.d + if (q.routeVersion != null) 10 else 0
                else -> at > 0 && at >= q.d
            }
            if (!ready) return@map q
            if (q.id in incomplete) {
                evaluated += q.id
                return@map missing(q, "판정 구간에서 위치·차속·방향지시등 관측이 끊겨 직접 확인이 필요해요")
            }
            if (q.g == "SIG_CANCEL") {
                if (q.id !in indicatorObserved) {
                    evaluated += q.id
                    return@map q.copy(res = 'N', completed = true, notApplicable = true, ev = "이 회전에서 방향지시등 점등이 관측되지 않아 해제 판정은 해당 없어요")
                }
                if (!knownTurn) {
                    evaluated += q.id
                    return@map missing(q, "통과 후 방향지시등 상태를 수신하지 못했어요")
                }
                val since = cancelStarted.getOrPut(q.id) { session.elapsedSeconds }
                if (vehicle.signal != "OFF" && session.elapsedSeconds - since < 10f) return@map q
            }
            evaluated += q.id
            when (q.g) {
                "SEATBELT" -> if (VehicleSignal.BELT in vehicle.knownSignals)
                    result(q, vehicle.belt, "출발 시 운전석 안전벨트 ${if (vehicle.belt) "체결" else "해제"}")
                    else missing(q, "출발 시 안전벨트 체결값을 수신하지 못했어요")
                "HEADLIGHT" -> if (VehicleSignal.HEADLIGHT in vehicle.knownSignals)
                    result(q, vehicle.headlight == "AUTO", "출발 10초 후 전조등 스위치 ${vehicle.headlight} · 기준 AUTO")
                    else missing(q, "전조등 스위치를 수신하지 못했어요")
                "TURN", "MERGE", "UNPROT_SIG" -> if (!knownTurn)
                    missing(q, "방향지시등을 수신하지 못했어요")
                    else if (q.routeVersion != null && q.id !in approachObserved)
                        missing(q, "회전 30m 이전의 연속 관측이 없어 직접 확인이 필요해요")
                    else result(q, direction == q.dir && q.d - directionSince >= 30f,
                        "통과 시 ${vehicle.signal} · 필요한 방향 ${q.dir} · 관측된 사전 점등 ${(q.d - directionSince).coerceAtLeast(0f).toInt()}m")
                "SIG_CANCEL" -> result(q, vehicle.signal == "OFF", "회전 통과 후 10초 안에 방향지시등 ${vehicle.signal}")
                "TURN_SPEED" -> peakSpeeds[q.id]?.let { peak ->
                    result(q, peak <= 20f, "회전 전후 15m 관측 최고 ${format(peak)}km/h · 앱 가이드 기준 20km/h")
                } ?: missing(q, "회전 구간의 차속을 관측하지 못했어요")
                // 고정 시나리오 단위 테스트 호환. 실제 주행에서는 지도 근거가 없는 항목을 SELF로 생성해요.
                "SCHOOL" -> result(q, (vehicle.speedMps ?: 0f) * 3.6f <= 30, "통과 속도 ${format((vehicle.speedMps ?: 0f) * 3.6f)}km/h")
                else -> missing(q, "자동 판정에 필요한 지점·신호 정보가 없어 직접 체크해 주세요")
            }
        }
    }

    private fun observeMotion(vehicle: Vehicle, session: LiveDriveSession) {
        val speed = vehicle.speedMps
        if (!session.speedAvailable || speed == null || speed < 0 ||
            (VehicleSignal.GEAR in vehicle.knownSignals && vehicle.parked)) {
            motionAt = 0; sawMoving = false; startAt = null; brakingPeak = 0f
            return
        }
        val at = vehicle.speedUpdatedAtNanos
        if (motionAt == 0L) { motionAt = at; motionSpeed = speed; return }
        val dt = (at - motionAt) / 1e9f
        if (dt < .09f) return // 동일 샘플을 100ms 루프에서 재사용해 가속도를 만들지 않아요.
        if (dt > 2f) {
            motionAt = at; motionSpeed = speed; sawMoving = false; startAt = null; brakingPeak = 0f
            return
        }
        val acceleration = (speed - motionSpeed) / dt
        if (speed > 1f || motionSpeed > 1f) {
            sawMoving = true
            brakingPeak = max(brakingPeak, -acceleration)
        }
        if (sawMoving && speed <= .5f / 3.6f) {
            stopCount++
            worstBraking = max(worstBraking, brakingPeak)
            sawMoving = false
            brakingPeak = 0f
        }
        if (motionSpeed <= .5f / 3.6f && speed > .5f / 3.6f) {
            startAt = session.elapsedSeconds
            startingPeak = max(0f, acceleration)
        }
        if (startAt != null) {
            startingPeak = max(startingPeak, acceleration)
            if (speed >= 3f || session.elapsedSeconds - startAt!! >= 5f || speed <= .5f / 3.6f) {
                startCount++
                worstStarting = max(worstStarting, startingPeak)
                startAt = null
            }
        }
        motionAt = at
        motionSpeed = speed
    }

    private fun motionResult(q: Quest, count: Int, peak: Float, threshold: Float, label: String): Quest =
        if (count == 0) q.copy(ev = "실제 ${if (q.g == "SMOOTH") "정지" else "출발"} 이벤트 관측 대기")
        else result(q, peak <= threshold, "차속 변화로 관측한 ${count}회 중 최대 $label ${format(peak * 3.6f)}km/h/s · 앱 기준 ${format(threshold * 3.6f)}")

    fun finish(quests: List<Quest>): List<Quest> = quests.map { q ->
        when {
            q.consent -> q.copy(res = 'N', completed = true, notApplicable = true, consent = false, ev = "상황 가이드 동의 전이라 체크하지 않았어요")
            q.notApplicable || q.selfAnswer != null -> q
            q.g == "HS_NSCC" -> q.copy(res = 'H', selfCheck = true, completed = true,
                ev = "일반 크루즈 ACTIVE ${format(cruiseActiveSeconds)}초 관측 · NSCC 2 사용 여부는 직접 확인해 주세요")
            q.selfCheck || q.res == 'N' -> q.copy(res = 'H', selfCheck = true, completed = true,
                ev = q.ev + if (q.g in setOf("SPEED", "RAIN_SLOW", "SCHOOL")) " · 주행 전체 관측 최고 ${format(tripPeak)}km/h (이 지점 속도를 뜻하지 않아요)" else "")
            else -> q.copy(completed = true)
        }
    }

    private fun result(q: Quest, ok: Boolean, evidence: String) =
        q.copy(res = if (ok) 'S' else 'F', ev = evidence, completed = true)
    private fun missing(q: Quest, reason: String) =
        q.copy(res = 'N', ev = reason, selfCheck = true, completed = true)
    private fun format(value: Float) = "%.1f".format(Locale.US, value)
}
