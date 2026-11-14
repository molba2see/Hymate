package com.example.hymate.data

data class Growth(
    val st: Map<String, GuideState>,
    val grads: List<String>,
    val rets: List<String>,
    val gain: Int,
    val remAfter: Int,
)

/**
 * 한 주행의 판정 결과를 성장 상태에 반영해요. (순수 함수 — 단위 테스트 대상)
 *
 * 졸업 (파이프라인 v2): Lv1~2는 1회 성공, Lv3~5는 서로 다른 3지점 성공 · 놓치면 −1.
 * 복귀: Lv1~2는 1회 놓침, 나머지는 최근 5회 중 2회 놓침. 기록·경험형은 졸업하지 않아요.
 *
 * - 지점별 성공/놓침은 순서대로 누적하지만, 졸업·복귀는 주행이 끝난 뒤 가이드마다 한 번만 판정해요.
 * - count는 항상 places.size와 같아요. 놓치면 그 지점(없으면 가장 최근에 익힌 지점)을 빼요.
 * - 이의가 반영된 놓침, 보류('H'), 해당 없음('N')은 성공/놓침으로 확정하지 않아요.
 *
 * XP: 성공(기록·경험 포함)이 한 번이라도 있는 가이드마다 [Guide.pts] + 도착·리포트 [XP.ARRIVE] + 출발 전 학습.
 * 가이드당 주행 1회만 지급해요. (지점마다가 아니라 가이드 단위 — 주행당 약 50 XP 가정에 맞춘 해석)
 */
fun computeGrowth(user: UserProfile, quests: List<Quest>, practiceXp: Int): Growth {
    val st = user.st.toMutableMap()
    val earned = linkedSetOf<String>()
    val failed = mutableSetOf<String>()
    val judged = linkedSetOf<String>()
    for (q in quests) {
        if (q.disputed && q.res == 'F') continue
        if (q.res == 'H' || q.res == 'N') continue
        val g = guide(q.g)
        var s = st.getValue(q.g).copy(seen = true)
        if (q.res == 'R' || q.res == 'E') { earned += q.g; st[q.g] = s; continue }
        if (g.type != GType.AUTO) continue
        judged += q.g
        val places = if (q.res == 'S') {
            earned += q.g
            if (q.place in s.places) s.places else s.places + q.place
        } else {
            failed += q.g
            if (q.place in s.places) s.places - q.place else s.places.dropLast(1)
        }
        s = s.copy(places = places, count = places.size, recent = (s.recent + q.res).takeLast(5))
        st[q.g] = s
    }

    val grads = mutableListOf<String>()
    val rets = mutableListOf<String>()
    for (k in judged) {
        val g = guide(k)
        var s = st.getValue(k)
        val fail = k in failed
        if (s.status == "graduated") {
            if (fail && (g.lv <= 2 || s.recent.count { it == 'F' } >= 2)) {
                s = s.copy(status = "returned", count = 0, places = emptyList())
                rets += k
            }
        } else if (needGrad(g.lv, s)) {
            s = s.copy(status = "graduated")
            grads += k
        }
        st[k] = s.copy(lastFail = fail)
    }

    val gain = earned.sumOf { guide(it).pts } + XP.ARRIVE + practiceXp
    return Growth(st, grads, rets, gain, remaining(st))
}

/** 보류('H') 퀘스트에 운전자가 답할 때 보여줄 선택지: (예, 아니오) */
fun holdLabels(g: String): Pair<String, String> = when (g) {
    "RED_RIGHT_STOP" -> "빨간불이었어요" to "빨간불 아니었어요"
    else -> "해봤어요" to "안 했어요"
}

/**
 * 운전자 응답으로 보류 퀘스트를 확정해요 (self_check). 자동 측정이 아니라 자기보고라는 걸 근거에 남겨요.
 * - RED_RIGHT_STOP: 빨간불이었으면 놓침, 아니었으면 해당 없음
 * - 경험형: 해봤으면 경험, 아니면 해당 없음
 */
fun resolveHold(q: Quest, yes: Boolean): Quest {
    if (q.res != 'H') return q
    return when {
        guide(q.g).type == GType.EXPERIENCE ->
            if (yes) q.copy(res = 'E', ev = "직접 알려줌 · 해봤어요") else q.copy(res = 'N', ev = "직접 알려줌 · 이번엔 안 했어요")
        yes -> q.copy(res = 'F', ev = "일시정지 없이 우회전 · 빨간불이었다고 직접 알려줌")
        else -> q.copy(res = 'N', ev = "빨간불이 아니었다고 직접 알려줌 · 판정하지 않아요")
    }
}
