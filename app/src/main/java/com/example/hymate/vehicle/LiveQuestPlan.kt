package com.example.hymate.vehicle

import com.example.hymate.data.*
import com.example.hymate.navigation.RoadRoute
import kotlin.math.roundToInt

/** 출발 전 목록과 동일한 가이드를 생성해요. 지도/센서가 없는 항목도 직접 확인할 수 있어요. */
object LiveQuestPlan {
    val routeKeys = setOf("TURN", "MERGE", "TURN_SPEED", "SIG_CANCEL")
    private val vehicleKeys = setOf("SEATBELT", "HEADLIGHT", "SMOOTH", "SMOOTH_START", "RAIN")

    fun checkLabel(key: String): String = when (key) {
        in routeKeys -> "실제 경로에서 자동 체크"
        in vehicleKeys -> if (key == "RAIN") "와이퍼 작동 시 자동 체크" else "차량 데이터로 자동 체크"
        "HS_NSCC" -> "크루즈 관측 + 사용 여부 직접 확인"
        else -> "주차 후 직접 체크"
    }

    fun create(user: UserProfile, unsupported: Set<String>, consent: String): List<Quest> =
        GUIDES.values.filterNot { it.key in unsupported }.map { guide ->
            val off = guide.cond != null && consent == "off"
            Quest(
                id = "LIVE-${guide.key}", g = guide.key, place = "이번 주행", short = guide.short,
                d = 0, res = 'N', ev = if (off) "상황 가이드를 꺼 두어 이번 주행에서는 제외해요"
                    else if (guide.key in routeKeys) "경로 탐색 후 실제 판정 지점을 연결해요"
                    else checkLabel(guide.key),
                fb = user.st[guide.key]?.status != "graduated",
                consent = guide.cond != null && consent == "ask",
                selfCheck = guide.key !in vehicleKeys && guide.key !in routeKeys,
                routeVersion = if (guide.key in routeKeys) 0 else null,
                completed = off, notApplicable = off,
            )
        }

    fun bindRoute(quests: List<Quest>, route: RoadRoute, version: Int, progress: Float): List<Quest> {
        val available = quests.map { it.g }.toSet()
        // 이전 경로에서 완료한 관측은 보존하고, 아직 통과하지 않은 지점만 교체해요.
        val kept = quests.filter { it.g !in routeKeys || (it.completed && !it.notApplicable) }
        val generated = mutableListOf<Quest>()
        route.maneuvers.forEachIndexed { index, turn ->
            if (turn.type in setOf("depart", "arrive", "new name", "notification")) return@forEachIndexed
            val direction = when {
                turn.modifier.contains("left") || turn.modifier == "uturn" -> "L"
                turn.modifier.contains("right") -> "R"
                else -> return@forEachIndexed // 직진·방향 불명 지점에 방향지시등 판정을 만들지 않아요.
            }
            val merge = turn.type in setOf("merge", "on ramp", "off ramp", "fork")
            val key = if (merge) "MERGE" else "TURN"
            val at = turn.atMeters.roundToInt()
            val name = turn.name.ifBlank { turn.instruction }
            for (g in listOf(key, "TURN_SPEED", "SIG_CANCEL")) {
                if (g !in available || (g == "TURN_SPEED" && merge)) continue
                val passed = at - progress < 35f // 지점 등록 전에 30m 사전 관측이 불가능하면 자동 판정하지 않아요.
                val template = quests.first { it.g == g }
                generated += Quest(
                    id = "R$version-$index-$g", g = g, place = name, short = turn.instruction,
                    d = at, res = 'N', ev = if (passed) "판정 지점 등록 전에 접근 구간을 지나 직접 확인이 필요해요"
                        else "실제 탐색 경로의 ${turn.instruction} 지점 · 차량 데이터를 관측해요",
                    dir = direction, lab = if (merge) turn.instruction else null, fb = template.fb,
                    routeVersion = version, coordinate = route.pointAt(turn.atMeters),
                    selfCheck = passed, completed = passed,
                )
            }
        }
        for (key in routeKeys.intersect(available)) {
            if (generated.none { it.g == key } && kept.none { it.g == key }) {
                val original = quests.first { it.g == key }
                generated += original.copy(
                    completed = true, notApplicable = true,
                    ev = "탐색된 경로에 자동 판정 가능한 ${guide(key).short} 지점이 없어요",
                )
            }
        }
        return kept + generated
    }
}
