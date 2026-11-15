package com.example.hymate.data

import kotlin.math.roundToInt

/**
 * 발표용으로 가이드마다 한 건을 만들어요. 호출부는 디버그 에뮬레이터로 제한해요.
 * 약 80%는 성공/기록/경험, 나머지는 놓침/미경험으로 분산하고 모든 근거에 데모를 표시해요.
 */
fun buildDemoQuestResults(quests: List<Quest>, driveNumber: Int): List<Quest> {
    require(driveNumber > 0)
    val guides = quests.distinctBy { it.g }
    if (guides.isEmpty()) return emptyList()
    val count = guides.size
    val misses = count - (count * .8f).roundToInt()
    return guides.mapIndexed { index, q ->
        val rank = (index + (driveNumber - 1) % count) % count
        val success = (rank + 1) * misses / count == rank * misses / count
        val type = guide(q.g).type
        val result = when {
            !success -> if (type == GType.AUTO) 'F' else 'N'
            type == GType.EXPERIENCE -> 'E'
            type == GType.RECORD -> 'R'
            else -> 'S'
        }
        q.copy(
            id = "DEMO-$driveNumber-${q.g}", place = "데모 ${driveNumber}회 · ${guide(q.g).short}",
            d = 0, res = result,
            ev = "발표용 데모 생성 · ${if (success) "성공/수행" else "놓침/미수행"} · 실제 차량 판정 아님",
            fb = false, consent = false, disputed = false,
            routeVersion = null, coordinate = null, completed = true,
            selfCheck = false, selfAnswer = null, notApplicable = !success && type != GType.AUTO,
        )
    }
}
