package com.example.hymate.vehicle

import com.example.hymate.data.Quest
import com.example.hymate.data.Vehicle
import com.example.hymate.data.VehicleSignal
import java.util.Locale

/** 예시 경로의 판정 지점에 도달했을 때 관측한 값만 사용해요. 미연동 항목은 성공/실패를 만들지 않아요. */
class LiveQuestEvaluator {
    private val evaluated = mutableSetOf<String>()
    private var direction: String? = null
    private var directionSince = 0f
    private var departedAt: Float? = null
    private val cancelStarted = mutableMapOf<String, Float>()
    private val peakSpeeds = mutableMapOf<String, Float>()
    private val incomplete = mutableSetOf<String>()

    // 크루즈(HS_NSCC): 주행 내내 사용 시간을 누적하고, 도착 시 finish()에서 확정해요.
    private var lastElapsed = 0f
    private var cruiseObserved = false
    private var cruiseActiveSeconds = 0f

    fun update(quests: List<Quest>, vehicle: Vehicle, session: LiveDriveSession): List<Quest> {
        val distance = session.distanceMeters
        val dt = (session.elapsedSeconds - lastElapsed).coerceIn(0f, 1f)
        lastElapsed = session.elapsedSeconds
        if (VehicleSignal.CRUISE in vehicle.knownSignals) {
            cruiseObserved = true
            if (vehicle.cruise == "ACTIVE") cruiseActiveSeconds += dt
        }
        if (!session.speedAvailable) {
            quests.filter { it.g == "SPEED" && it.id !in evaluated && distance >= speedStart(it) && distance <= it.d }
                .forEach { incomplete += it.id }
            direction = null
            return quests
        }
        if (distance > 0f && departedAt == null) departedAt = session.elapsedSeconds
        val turn = vehicle.signal.takeIf { VehicleSignal.TURN in vehicle.knownSignals }
        if (turn != direction) {
            direction = turn
            directionSince = distance
        }
        quests.filter { it.g == "SPEED" && it.id !in evaluated }.forEach { q ->
            if (distance >= speedStart(q)) {
                if (!session.speedAvailable) incomplete += q.id
                else peakSpeeds[q.id] = maxOf(peakSpeeds[q.id] ?: 0f, (vehicle.speedMps ?: 0f) * 3.6f)
            }
        }
        return quests.map { q ->
            if (q.id in evaluated) return@map q
            if (q.g == "HS_NSCC") return@map q // 지점 판정이 아니라 주행 전체를 보고 finish()에서 확정해요
            val ready = when (q.g) {
                "SEATBELT" -> distance > 0f
                "HEADLIGHT" -> departedAt?.let { session.elapsedSeconds - it >= 10f } == true
                else -> distance > 0f && distance >= q.d
            }
            if (!ready) return@map q
            if (q.g == "SIG_CANCEL") {
                val since = cancelStarted.getOrPut(q.id) { session.elapsedSeconds }
                if (turn != "OFF" && session.elapsedSeconds - since < 10f) return@map q
            }
            evaluated += q.id
            judge(q, vehicle)
        }
    }

    private fun speedStart(q: Quest): Int = when (q.limit) { 70 -> 3100; 60 -> 9200; else -> 11000 }

    /** 도착 시 호출. 주행 전체 관측이 필요한 퀘스트(HS_NSCC)를 확정해요. */
    fun finish(quests: List<Quest>): List<Quest> = quests.map { q ->
        if (q.g != "HS_NSCC") return@map q
        when {
            cruiseActiveSeconds >= 1f -> q.copy(
                res = 'E',
                ev = "크루즈 작동 ${cruiseActiveSeconds.toInt() / 60}분 ${cruiseActiveSeconds.toInt() % 60}초 관측 · 직접 켜고 끔",
            )
            cruiseObserved -> q.copy(res = 'H', ev = "크루즈 작동이 관측되지 않았어요 · 사용했다면 직접 알려주세요")
            else -> q.copy(res = 'H', ev = "크루즈 상태 미수신 · 직접 알려주세요")
        }
    }

    private fun judge(q: Quest, v: Vehicle): Quest {
        fun result(ok: Boolean, evidence: String) = q.copy(res = if (ok) 'S' else 'F', ev = evidence)
        fun missing(reason: String) = q.copy(res = 'N', ev = reason)
        fun known(signal: VehicleSignal) = signal in v.knownSignals
        fun speed(limit: Int): Quest {
            val kph = (v.speedMps ?: return missing("판정 시 차속을 수신하지 못했어요")) * 3.6f
            return result(kph <= limit, "통과 속도 ${"%.1f".format(Locale.US, kph)}km/h · 기준 ${limit}km/h")
        }
        return when (q.g) {
            "SEATBELT" -> if (known(VehicleSignal.BELT)) {
                result(v.belt, if (v.belt) "출발 시 운전석 벨트 경고 꺼짐" else "출발 시 운전석 벨트 경고 켜짐")
            } else missing("출발 시 안전벨트 경고를 수신하지 못했어요")
            "HEADLIGHT" -> if (known(VehicleSignal.HEADLIGHT)) {
                result(v.headlight != "OFF", "출발 10초 후 전조등 스위치 ${v.headlight}")
            } else missing("전조등 스위치를 수신하지 못했어요")
            "TURN", "MERGE", "UNPROT_SIG" -> if (known(VehicleSignal.TURN)) {
                val early = direction == q.dir && q.d - directionSince >= 30f
                result(early, if (direction == q.dir) {
                    "관측된 방향지시등 점등 거리 ${(q.d - directionSince).coerceAtLeast(0f).toInt()}m · 기준 30m"
                } else "통과 시 방향지시등 ${v.signal} · 필요한 방향 ${q.dir}")
            } else missing("방향지시등을 수신하지 못했어요")
            "SIG_CANCEL" -> if (known(VehicleSignal.TURN)) {
                result(v.signal == "OFF", if (v.signal == "OFF") "통과 후 10초 안에 방향지시등 OFF" else "통과 후 10초 동안 방향지시등 켜짐")
            } else missing("방향지시등 해제 상태를 수신하지 못했어요")
            "SCHOOL" -> speed(30)
            "TURN_SPEED" -> speed(20)
            "SPEED" -> {
                val peak = peakSpeeds[q.id]
                if (peak == null || q.id in incomplete) missing("구간의 연속된 차속이 없어 판정을 보류했어요")
                else result(peak <= q.limit, "관측 구간 최고 ${"%.1f".format(Locale.US, peak)}km/h · 기준 ${q.limit}km/h")
            }
            "RED_RIGHT_STOP" -> q.copy(res = 'H', ev = "신호 색·교차로 정지선이 미연동이에요. 직접 확인해 주세요")
            "RAIN", "RAIN_SLOW" -> missing("실제 강우 정보가 연결되지 않아 판정하지 않았어요")
            "TUNNEL" -> missing("전조등 실제 점등 상태가 연결되지 않아 판정하지 않았어요")
            "SMOOTH", "SMOOTH_START" -> missing("실제 정차·출발 이벤트의 가속도 판정은 아직 연결되지 않았어요")
            "WEAVE_EXIT" -> missing("차로 위치 정보가 연결되지 않아 판정하지 않았어요")
            else -> missing("이 항목의 실제 차량 이벤트가 연결되지 않았어요")
        }
    }
}
