package com.example.hymate

import com.example.hymate.data.*
import com.example.hymate.vehicle.LiveQuestPlan
import org.junit.Assert.*
import org.junit.Test

class DemoQuestResultsTest {
    private val user = seedUser("L0")
    private fun quests() = LiveQuestPlan.create(user, emptySet(), "ask")

    @Test fun fullDemoHasSixteenSuccessfulGuidesWithoutPendingAnswers() {
        val input = quests()
        val output = buildDemoQuestResults(input, 1)
        assertEquals(20, output.size)
        assertEquals(16, output.count { it.res in "SRE" })
        assertEquals(4, output.count { it.res in "FN" })
        assertTrue(output.all { it.completed && !it.selfCheck && !it.consent && !it.disputed })
        assertTrue(output.none { it.res == 'H' })
        assertTrue(output.all { it.ev.contains("데모") && it.ev.contains("실제 차량 판정 아님") })
        assertTrue(input.all { it.res == 'N' })
    }

    @Test fun generatedResultsUseExistingXpGraduationAndLicenseRules() {
        val results = buildDemoQuestResults(quests(), 1)
        val growth = computeGrowth(user, results, 0)
        assertEquals("L0", tierOf(user.xp + growth.gain)?.first)
        assertTrue(growth.grads.isNotEmpty())
        val experienced = seedUser("L1")
        val next = computeGrowth(experienced, results, 0)
        assertEquals("L1", tierOf(experienced.xp + next.gain)?.first)
    }

    @Test fun routeDuplicatesAreGroupedAndSyntheticPointsDoNotClaimActualCoordinates() {
        val input = quests()
        val turn = input.first { it.g == "TURN" }
        val output = buildDemoQuestResults(input + turn.copy(
            id = "another-turn", routeVersion = 4, coordinate = GeoPoint(37.0, 127.0),
            selfCheck = true, disputed = true,
        ), 2)
        assertEquals(input.size, output.size)
        assertEquals(output.size, output.map { it.id }.toSet().size)
        assertTrue(output.all { it.routeVersion == null && it.coordinate == null })
        assertTrue(output.map { it.place }.intersect(buildDemoQuestResults(input, 1).map { it.place }.toSet()).isEmpty())
    }

    @Test fun unavailableGuidesRemainExcludedAndSmallListsRoundTheSuccessRatio() {
        val input = quests().take(7)
        val output = buildDemoQuestResults(input, 1)
        assertEquals(input.map { it.g }, output.map { it.g })
        assertEquals(6, output.count { it.res in "SRE" })
        assertTrue(buildDemoQuestResults(emptyList(), 1).isEmpty())
    }
}
