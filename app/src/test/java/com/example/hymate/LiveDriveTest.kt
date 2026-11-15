package com.example.hymate

import com.example.hymate.data.Quest
import com.example.hymate.data.Vehicle
import com.example.hymate.data.VehicleSignal
import com.example.hymate.vehicle.LiveDriveSession
import com.example.hymate.vehicle.LiveQuestEvaluator
import org.junit.Assert.*
import org.junit.Test

class LiveDriveTest {
    private var now = 1_000_000_000L
    private val session = LiveDriveSession()
    private fun vehicle(speed: Float? = 10f, parked: Boolean = false, brake: Boolean = false) = Vehicle(
        speedMps = speed, speedUpdatedAtNanos = now, parked = parked, brake = brake,
        knownSignals = setOf(VehicleSignal.GEAR, VehicleSignal.BRAKE),
    )

    private fun tick(v: Vehicle, steps: Int = 1, each: (Vehicle) -> Unit = {}) {
        repeat(steps) {
            now += 100_000_000L
            val sample = v.copy(speedUpdatedAtNanos = now)
            session.tick(sample, now)
            each(sample)
        }
    }

    private fun quest(g: String, d: Int = 0, dir: String? = null) =
        Quest("q", g, "예시 지점", "예시", d, 'N', "미판정", dir = dir)

    @Test fun demoIgnoresRetainedGpsAndStartsAtZero() {
        val demo = LiveDriveSession(allowLocationSnap = false)
        demo.syncDistance(5600f) // 이전 에뮬레이터 GPS가 경로 중간이어도 출발점을 유지해요.
        assertEquals(0f, demo.distanceMeters, 0f)
        demo.tick(vehicle(0f), now)
        now += 100_000_000L
        demo.tick(vehicle(0f), now)
        assertEquals(0f, demo.distanceMeters, 0f)
        demo.syncDistance(12000f)
        assertEquals(0f, demo.distanceMeters, 0f)
    }

    @Test fun demoMovesFromOriginWithVehicleSpeedAndResetsForNextTrip() {
        val demo = LiveDriveSession(allowLocationSnap = false)
        demo.tick(vehicle(), now)
        repeat(10) {
            now += 100_000_000L
            demo.tick(vehicle(), now)
            demo.syncDistance(5600f)
        }
        assertEquals(10f, demo.distanceMeters, .01f)
        val next = LiveDriveSession(allowLocationSnap = false)
        next.syncDistance(5600f)
        assertEquals(0f, next.distanceMeters, 0f)
    }

    @Test fun realGpsCorrectsLargeBackwardErrorButIgnoresSmallJitter() {
        session.syncDistance(5600f)
        session.syncDistance(5580f)
        assertEquals(5600f, session.distanceMeters, 0f)
        session.syncDistance(0f)
        assertEquals(0f, session.distanceMeters, 0f)
        session.syncDistance(Float.NaN)
        session.syncDistance(-1f)
        assertEquals(0f, session.distanceMeters, 0f)
    }

    @Test fun noSpeedNeverMovesOrPretendsToStop() {
        session.tick(vehicle(null), now)
        tick(vehicle(null), 100)
        assertEquals(0f, session.distanceMeters, 0f)
        assertFalse(session.stopped)
        assertFalse(session.speedAvailable)
        assertNull(session.remainingSeconds(1000f, vehicle(null)))
    }

    @Test fun thirtySixKphForTenSecondsTravelsOneHundredMeters() {
        val v = vehicle()
        session.tick(v, now)
        tick(v, 100)
        assertEquals(100f, session.distanceMeters, 0.01f)
        assertEquals(10f, session.elapsedSeconds, 0.01f)
        assertEquals(90f, session.remainingSeconds(1000f, v)!!, 0.01f)
    }

    @Test fun zeroSpeedFreezesDistance() {
        session.tick(vehicle(), now)
        tick(vehicle(), 10)
        val distance = session.distanceMeters
        tick(vehicle(0f), 100)
        assertEquals(distance, session.distanceMeters, 0f)
        assertNull(session.remainingSeconds(1000f, vehicle(0f)))
    }

    @Test fun parkAndReverseDoNotAdvanceTheRoute() {
        session.tick(vehicle(), now)
        tick(vehicle(10f, parked = true), 20)
        tick(vehicle(-10f), 20)
        assertEquals(0f, session.distanceMeters, 0f)
        assertFalse(session.stopped)
    }

    @Test fun speedWorksWithoutPleosGearData() {
        val v = vehicle().copy(knownSignals = emptySet(), parked = true)
        session.tick(v, now)
        tick(v, 10)
        assertEquals(10f, session.distanceMeters, 0.01f)
    }

    @Test fun confirmedStopRequiresThreeSecondsAndBrakeOrPark() {
        val v = vehicle(0f, brake = true)
        session.tick(v, now)
        tick(v, 29)
        assertFalse(session.stopped)
        tick(v)
        assertTrue(session.stopped)
        tick(vehicle(1f, brake = true))
        assertFalse(session.stopped)
    }

    @Test fun stationaryWithoutBrakeDoesNotShowStopFeedback() {
        session.tick(vehicle(0f), now)
        tick(vehicle(0f), 50)
        assertFalse(session.stopped)
        tick(vehicle(0f, parked = true), 31)
        assertTrue(session.stopped)
    }

    @Test fun releasingBrakeResetsStopConfirmation() {
        val v = vehicle(0f, brake = true)
        session.tick(v, now)
        tick(v, 20)
        tick(vehicle(0f))
        tick(v, 20)
        assertFalse(session.stopped)
        tick(v, 11)
        assertTrue(session.stopped)
    }

    @Test fun staleSpeedFreezesAndReconnectDoesNotCatchUp() {
        val v = vehicle()
        session.tick(v, now)
        tick(v, 10)
        val before = session.distanceMeters
        now += 3_000_000_000L
        session.tick(v, now)
        assertFalse(session.speedAvailable)
        assertEquals(before, session.distanceMeters, 0f)
        session.tick(vehicle(), now)
        assertEquals(before, session.distanceMeters, 0f)
        tick(vehicle(), 10)
        assertEquals(before + 10f, session.distanceMeters, 0.01f)
    }

    @Test fun mainThreadGapDoesNotReplayMissedDistance() {
        session.tick(vehicle(), now)
        now += 30_000_000_000L
        session.tick(vehicle(), now)
        assertEquals(0f, session.distanceMeters, 0f)
    }

    @Test fun unknownBeltIsNotAssumedFastened() {
        val evaluator = LiveQuestEvaluator()
        var qs = listOf(quest("SEATBELT"))
        val v = vehicle()
        session.tick(v, now)
        tick(v) { qs = evaluator.update(qs, it, session) }
        assertEquals('N', qs.single().res)
        assertTrue(qs.single().ev.contains("수신하지 못"))
    }

    @Test fun actualBeltWarningDeterminesDepartureResult() {
        val evaluator = LiveQuestEvaluator()
        var qs = listOf(quest("SEATBELT"))
        val v = vehicle().copy(belt = false, knownSignals = vehicle().knownSignals + VehicleSignal.BELT)
        session.tick(v, now)
        tick(v) { qs = evaluator.update(qs, it, session) }
        assertEquals('F', qs.single().res)
        // 나중에 벨트를 매도 출발 당시의 결과를 바꾸지 않아요.
        tick(v.copy(belt = true)) { qs = evaluator.update(qs, it, session) }
        assertEquals('F', qs.single().res)
    }

    @Test fun crossingSpeedUsesTheReceivedSpeed() {
        val evaluator = LiveQuestEvaluator()
        var qs = listOf(quest("SCHOOL", 10))
        val v = vehicle(10f) // 36km/h
        session.tick(v, now)
        tick(v, 9) { qs = evaluator.update(qs, it, session) }
        assertEquals('N', qs.single().res)
        tick(v) { qs = evaluator.update(qs, it, session) }
        assertEquals('F', qs.single().res)
        assertTrue(qs.single().ev.contains("36.0"))
    }

    @Test fun turnMustBeSignalledAtLeastThirtyMetersEarly() {
        val evaluator = LiveQuestEvaluator()
        var qs = listOf(quest("TURN", 100, "L"))
        val v = vehicle().copy(signal = "OFF", knownSignals = vehicle().knownSignals + VehicleSignal.TURN)
        session.tick(v, now)
        tick(v, 80) { qs = evaluator.update(qs, it, session) }
        tick(v.copy(signal = "L"), 20) { qs = evaluator.update(qs, it, session) }
        assertEquals('F', qs.single().res)
    }

    @Test fun unconnectedRainSensorCannotCreateCannedFailure() {
        val evaluator = LiveQuestEvaluator()
        var qs = listOf(quest("RAIN", 10))
        val v = vehicle().copy(wiper = "OFF")
        session.tick(v, now)
        tick(v, 10) { qs = evaluator.update(qs, it, session) }
        assertEquals('N', qs.single().res)
        assertTrue(qs.single().ev.contains("강우 정보"))
    }
}
