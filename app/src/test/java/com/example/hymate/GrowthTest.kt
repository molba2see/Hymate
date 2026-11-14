package com.example.hymate

import com.example.hymate.data.GD
import com.example.hymate.data.GUIDES
import com.example.hymate.data.Quest
import com.example.hymate.data.UserProfile
import com.example.hymate.data.Vehicle
import com.example.hymate.data.XP
import com.example.hymate.data.buildQuests
import com.example.hymate.data.computeGrowth
import com.example.hymate.data.guide
import com.example.hymate.data.pts
import com.example.hymate.data.resolveHold
import com.example.hymate.data.seedUser
import com.example.hymate.data.tierOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthTest {
    private fun quests(user: UserProfile) = buildQuests("L0", user, Vehicle(), "off")
    private fun UserProfile.after(qs: List<Quest>) = copy(st = computeGrowth(this, qs, 0).st)

    /** 파이프라인: 가이드 20개(일반 17 + Hyundai ONLY 3), 일반 가이드 Lv 합 45, 모두 상세 내용이 있어요. */
    @Test
    fun catalogMatchesPipeline() {
        assertEquals(20, GUIDES.size)
        assertEquals(3, GUIDES.values.count { it.pts == XP.HS && it.key.startsWith("HS_") })
        assertEquals(45, GUIDES.values.filterNot { it.key.startsWith("HS_") }.sumOf { it.lv })
        assertEquals(GUIDES.keys, GD.keys)
        assertTrue(GD.values.all { it.how.size >= 2 })
    }

    /** L0 시나리오의 SMOOTH는 성공 → 놓침 → 성공. 졸업은 한 번만, 복귀 칩과 함께 뜨지 않아야 해요. */
    @Test
    fun graduationIsJudgedOncePerDrive() {
        val user = seedUser("L0")
        val g = computeGrowth(user, quests(user), 0)

        assertEquals(1, g.grads.count { it == "SMOOTH" })
        assertFalse("SMOOTH" in g.rets)
        assertEquals(g.grads.size, g.grads.distinct().size)
        assertEquals("graduated", g.st.getValue("SMOOTH").status)
    }

    /** XP: 성공·기록·경험이 있는 가이드마다 한 번씩 Lv(HS는 3) + 도착 5 + 출발 전 학습. 졸업 보너스는 없어요. */
    @Test
    fun xpFollowsPipelineRewards() {
        val user = seedUser("L0")
        val qs = quests(user)
        val earned = qs.filter { it.res in "SRE" }.map { it.g }.distinct()

        assertEquals(earned.sumOf { guide(it).pts } + XP.ARRIVE, computeGrowth(user, qs, 0).gain)
        assertEquals(computeGrowth(user, qs, 0).gain + XP.PRE, computeGrowth(user, qs, XP.PRE).gain)
        assertTrue("가이드당 주행 1회만 지급", computeGrowth(user, qs, 0).gain <= 45 + 3 * XP.HS + XP.ARRIVE)
    }

    /** 첫 라이선스 L0는 30 XP부터. 첫 주행을 마치면 발급돼요. */
    @Test
    fun firstLicenseIsIssuedAfterFirstDrive() {
        val user = seedUser("L0")
        assertNull(tierOf(user.xp))
        assertEquals("L0", tierOf(user.xp + computeGrowth(user, quests(user), 0).gain)?.first)
    }

    /** 같은 길에서 한 지점을 계속 놓쳐도 이미 익힌 지점 수가 깎여 나가지 않고, 그 지점을 지키면 졸업해요. */
    @Test
    fun sameRouteCanStillGraduate() {
        var user = seedUser("L0")
        user = user.after(quests(user)) // 국민대 정문 놓침, 나머지 2곳 지킴
        assertEquals(2, user.st.getValue("TURN").count)

        user = user.after(quests(user)) // 같은 결과로 한 번 더
        val turn = user.st.getValue("TURN")
        assertEquals(2, turn.count)
        assertEquals(turn.count, turn.places.size)
        assertEquals("learning", turn.status)

        val clean = quests(user).map { if (it.g == "TURN") it.copy(res = 'S') else it }
        val g = computeGrowth(user, clean, 0)
        assertTrue("TURN" in g.grads)
        assertEquals(3, g.st.getValue("TURN").count)
    }

    /** 이의가 반영된 놓침은 판정에서 빠져요. */
    @Test
    fun disputedFailureIsExcluded() {
        val user = seedUser("L0")
        val qs = quests(user).map { if (it.g == "SCHOOL") it.copy(disputed = true) else it }
        val s = computeGrowth(user, qs, 0).st.getValue("SCHOOL")
        assertFalse(s.lastFail)
        assertTrue(s.recent.isEmpty())
    }

    /** 보류(자가 체크)는 운전자가 답하기 전에는 성공·놓침으로 확정하지 않아요. */
    @Test
    fun holdIsNotJudgedUntilAnswered() {
        val user = seedUser("L0")
        val qs = quests(user)
        val hold = qs.first { it.g == "RED_RIGHT_STOP" && it.res == 'H' }

        // 답하기 전: 본사 앞 성공 1곳만 반영
        assertEquals(listOf('S'), computeGrowth(user, qs, 0).st.getValue("RED_RIGHT_STOP").recent)

        val red = qs.map { if (it.id == hold.id) resolveHold(it, yes = true) else it }
        assertTrue(computeGrowth(user, red, 0).st.getValue("RED_RIGHT_STOP").lastFail)

        val notRed = qs.map { if (it.id == hold.id) resolveHold(it, yes = false) else it }
        assertFalse(computeGrowth(user, notRed, 0).st.getValue("RED_RIGHT_STOP").lastFail)

        // 경험형 자기보고: 해봤다고 답하면 +3 XP
        val rspa = qs.first { it.g == "HS_RSPA" }
        val done = qs.map { if (it.id == rspa.id) resolveHold(it, yes = true) else it }
        assertEquals(computeGrowth(user, qs, 0).gain + XP.HS, computeGrowth(user, done, 0).gain)
    }
}
