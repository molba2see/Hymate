package com.example.hymate

import com.example.hymate.data.*
import com.example.hymate.navigation.*
import com.example.hymate.vehicle.*
import org.junit.Assert.*
import org.junit.Test

class LiveQuestFlowTest {
    private val user = seedUser("L0")
    private fun base(consent: String = "on") = LiveQuestPlan.create(user, emptySet(), consent)
    private fun route() = RoadRoute(
        listOf(GeoPoint(37.0, 127.0), GeoPoint(37.01, 127.0)), 1000f, 100f,
        listOf(
            RoadManeuver(200f, "실제 좌회전 도로", "turn", "left"),
            RoadManeuver(500f, "실제 합류 도로", "merge", "right"),
            RoadManeuver(800f, "직진 도로", "turn", "straight"),
        ), listOf(0f to 0f, 1000f to 100f), GeoPoint(37.0, 127.0),
    )

    private inner class Run(onRoute: Boolean = false) {
        var now = 1_000_000_000L
        var vehicle = Vehicle(speedMps = 0f, speedUpdatedAtNanos = now, parked = false,
            signal = "OFF", headlight = "AUTO", lowBeamOn = true,
            knownSignals = VehicleSignal.entries.toSet())
        val session = LiveDriveSession()
        val evaluator = LiveQuestEvaluator()
        var quests = if (onRoute) LiveQuestPlan.bindRoute(base(), route(), 1, 0f) else base()
        private val version = if (onRoute) 1 else null
        init {
            session.tick(vehicle, now)
            quests = evaluator.update(quests, vehicle, session, version?.let { QuestRouteState(it, 0f, true) })
        }
        fun tick(speed: Float = vehicle.speedMps ?: 0f, progress: Float? = null, valid: Boolean = true,
                 seconds: Float = .1f, freshTimestamp: Boolean = true) {
            now += (seconds * 1e9).toLong()
            vehicle = vehicle.copy(speedMps = speed,
                speedUpdatedAtNanos = if (freshTimestamp) now else vehicle.speedUpdatedAtNanos)
            session.tick(vehicle, now)
            quests = evaluator.update(quests, vehicle, session,
                version?.let { QuestRouteState(it, progress ?: session.distanceMeters, valid) })
        }
        fun q(key: String) = quests.first { it.g == key && !it.notApplicable }
    }

    @Test fun everyPreDriveGuideHasARealChecklistEntryWithNoCannedSuccess() {
        val quests = base()
        assertEquals(GUIDES.keys, quests.map { it.g }.toSet())
        assertEquals(20, quests.size)
        assertTrue(quests.all { it.res == 'N' })
        assertTrue(quests.first { it.g == "SPEED" }.selfCheck)
        assertFalse(quests.first { it.g == "SEATBELT" }.selfCheck)
    }

    @Test fun routeQuestsComeFromActualManeuversAndIgnoreStraightRoads() {
        val qs = LiveQuestPlan.bindRoute(base(), route(), 1, 0f)
        assertEquals(listOf(200), qs.filter { it.g == "TURN" }.map { it.d })
        assertEquals(listOf(500), qs.filter { it.g == "MERGE" }.map { it.d })
        assertEquals(2, qs.count { it.g == "SIG_CANCEL" })
        assertEquals("L", qs.first { it.g == "TURN" }.dir)
        assertNotNull(qs.first { it.g == "TURN" }.coordinate)
        assertEquals(GUIDES.keys, qs.map { it.g }.toSet())
    }

    @Test fun rerouteKeepsCompletedObservationsButReplacesUnvisitedPoints() {
        val first = LiveQuestPlan.bindRoute(base(), route(), 1, 0f)
        val completed = first.first { it.g == "TURN" }.copy(res = 'F', completed = true)
        val next = LiveQuestPlan.bindRoute(first.map { if (it.id == completed.id) completed else it }, route(), 2, 0f)
        assertEquals('F', next.first { it.id == completed.id }.res)
        assertTrue(next.none { it.routeVersion == 1 && !it.completed })
        assertTrue(next.any { it.routeVersion == 2 && !it.completed })
    }

    @Test fun registrationTooCloseToTurnNeedsDirectCheckInsteadOfInventedFailure() {
        val qs = LiveQuestPlan.bindRoute(base(), route(), 1, 190f)
        val q = qs.first { it.g == "TURN" }
        assertTrue(q.selfCheck)
        assertEquals('H', LiveQuestEvaluator().finish(listOf(q)).single().res)
    }

    @Test fun signalAndTurnSpeedAreCheckedAtNavigationDistanceNotFixedDemoDistance() {
        val run = Run(true)
        for (p in 1..220) {
            if (p == 120) run.vehicle = run.vehicle.copy(signal = "L")
            run.tick(5f, p.toFloat()) // odometer is only 110m; navigation has crossed the actual 200m turn.
        }
        assertEquals('S', run.q("TURN").res)
        assertEquals('S', run.q("TURN_SPEED").res)
        assertTrue(run.session.distanceMeters < 150)
    }

    @Test fun lateSignalAndExcessTurnSpeedAreActualFailures() {
        val run = Run(true)
        for (p in 1..220) {
            if (p == 180) run.vehicle = run.vehicle.copy(signal = "L")
            run.tick(10f, p.toFloat())
        }
        assertEquals('F', run.q("TURN").res)
        assertEquals('F', run.q("TURN_SPEED").res)
    }

    @Test fun indicatorSwitchOffIsCheckedAfterObservedTurn() {
        val run = Run(true)
        for (p in 1..220) {
            if (p == 120) run.vehicle = run.vehicle.copy(signal = "L")
            if (p == 220) run.vehicle = run.vehicle.copy(signal = "OFF")
            run.tick(5f, p.toFloat())
        }
        assertEquals('S', run.q("SIG_CANCEL").res)
    }

    @Test fun neverUsingIndicatorDoesNotCountAsSuccessfullyCancellingIt() {
        val run = Run(true)
        for (p in 1..220) run.tick(5f, p.toFloat())
        val cancel = run.quests.first { it.g == "SIG_CANCEL" }
        assertEquals('N', cancel.res)
        assertTrue(cancel.notApplicable)
        assertEquals('F', run.q("TURN").res)
    }

    @Test fun gpsJumpAcrossTurnProducesDirectCheckRatherThanFalseFailure() {
        val run = Run(true)
        run.tick(5f, 220f)
        val q = run.q("TURN")
        assertTrue(q.selfCheck)
        assertEquals('H', run.evaluator.finish(listOf(q)).single().res)
    }

    @Test fun missingIndicatorDataCannotBePassedOrFailed() {
        val run = Run(true)
        run.vehicle = run.vehicle.copy(knownSignals = run.vehicle.knownSignals - VehicleSignal.TURN)
        for (p in 1..220) run.tick(5f, p.toFloat())
        assertEquals('N', run.q("TURN").res)
        assertTrue(run.q("TURN").selfCheck)
    }

    @Test fun headlightsMustActuallyBeSetToAuto() {
        for ((setting, expected) in listOf("ON" to 'F', "AUTO" to 'S', "OFF" to 'F')) {
            val run = Run()
            run.vehicle = run.vehicle.copy(headlight = setting)
            repeat(125) { run.tick(5f) }
            assertEquals(expected, run.q("HEADLIGHT").res)
        }
    }

    @Test fun measuredGentleStartAndStopAreCheckedWithoutScenarioCoordinates() {
        val run = Run()
        for (i in 1..40) run.tick(i / 10f)
        for (i in 39 downTo 0) run.tick(i / 10f)
        assertEquals('S', run.q("SMOOTH_START").res)
        assertEquals('S', run.q("SMOOTH").res)
    }

    @Test fun sharpStartAndStopAreRecordedAsObservedFailures() {
        val run = Run()
        run.tick(10f)
        run.tick(0f)
        assertEquals('F', run.q("SMOOTH_START").res)
        assertEquals('F', run.q("SMOOTH").res)
    }

    @Test fun missingAndRepeatedSpeedSamplesCannotInventSmoothStopsOrStarts() {
        val run = Run()
        run.tick(10f, freshTimestamp = false)
        assertEquals('N', run.q("SMOOTH_START").res)
        val gap = Run()
        gap.tick(10f)
        gap.tick(0f, seconds = 3f)
        assertEquals('N', gap.q("SMOOTH").res)
        assertEquals('H', gap.evaluator.finish(listOf(gap.q("SMOOTH"))).single().res)
    }

    @Test fun rainUsesWiperObservationAndActualLampNotJustAutoSwitch() {
        for ((lamp, expected) in listOf(true to 'S', false to 'F')) {
            val run = Run()
            run.vehicle = run.vehicle.copy(wiping = true, lowBeamOn = lamp, headlight = "AUTO")
            repeat(120) { run.tick() }
            assertEquals(expected, run.q("RAIN").res)
        }
    }

    @Test fun missingLampDataDefersRainAndUnconsentedGuideIsNotEvaluated() {
        val run = Run()
        run.vehicle = run.vehicle.copy(wiping = true, lowBeamOn = null)
        repeat(120) { run.tick() }
        assertTrue(run.q("RAIN").selfCheck)
        val consent = LiveQuestEvaluator().finish(base("ask"))
        assertTrue(consent.filter { it.g in setOf("RAIN", "RAIN_SLOW") }.all { it.notApplicable && it.res == 'N' })
    }

    @Test fun everyUnobservedEntryCanBeCheckedAfterDriveAndDoesNotDisappear() {
        val finished = LiveQuestEvaluator().finish(base())
        assertEquals(GUIDES.keys, finished.map { it.g }.toSet())
        assertTrue(finished.all { it.res == 'H' && it.selfCheck })
        assertEquals(listOf("HS_NSCC"), base().filter { it.g == "HS_NSCC" }.map { it.g })
    }

    @Test fun selfCheckIsRecordedSeparatelyAndCannotGraduateAutomaticGuide() {
        val pending = LiveQuestEvaluator().finish(base()).first { it.g == "SPEED" }
        val yes = resolveHold(pending, true)
        val no = resolveHold(pending, false)
        assertEquals('R', yes.res)
        assertEquals(true, yes.selfAnswer)
        assertEquals('N', no.res)
        assertEquals(false, no.selfAnswer)
        val growth = computeGrowth(user, listOf(yes), 0)
        assertEquals(XP.ARRIVE, growth.gain)
        assertTrue(growth.grads.isEmpty())
        assertEquals(user.st, growth.st)
    }

    @Test fun genericCruiseActivityDoesNotPretendToProveNscc2Experience() {
        val run = Run()
        run.vehicle = run.vehicle.copy(cruise = "ACTIVE")
        repeat(20) { run.tick() }
        val q = run.evaluator.finish(run.quests).first { it.g == "HS_NSCC" }
        assertEquals('H', q.res)
        assertTrue(q.ev.contains("관측"))
        assertEquals('E', resolveHold(q, true).res)
    }
}
