package com.example.hymate

import com.example.hymate.data.GeoPoint
import com.example.hymate.navigation.*
import org.junit.Assert.*
import org.junit.Test

class NavigationTest {
    private val origin = GeoPoint(37.0, 127.0)
    private fun road() = RoadRoute(
        listOf(origin, GeoPoint(37.01, 127.0)), 1000f, 100f,
        listOf(RoadManeuver(400f, "실제 도로", "turn", "left"), RoadManeuver(1000f, "", "arrive", "")),
        listOf(0f to 0f, 400f to 20f, 1000f to 100f), origin,
    )
    private fun tracker() = RouteTracker().apply { install(road()) }

    @Test fun zeroLengthRouteAtDestinationCanBeProjectedWithoutNaN() {
        val route = RoadRoute(listOf(origin, origin), 0f, 0f,
            listOf(RoadManeuver(0f, "", "arrive", "")), listOf(0f to 0f), origin)
        assertEquals(origin, route.pointAt(0f))
        assertEquals(0f, route.project(origin).distance, 0f)
        assertEquals(0f, route.remainingDuration(0f), 0f)
    }

    @Test fun initialOffRoadAccessDoesNotCauseEndlessRerouting() {
        val points = listOf(GeoPoint(37.0, 127.001), GeoPoint(37.01, 127.001))
        val route = RoadRoute(points, 1000f, 100f, emptyList(), emptyList(), origin)
        val tracker = RouteTracker().apply { install(route) }
        tracker.observe(origin, 1000)
        tracker.observe(origin, 6000)
        assertFalse(tracker.offRoute)
        tracker.observe(points.first(), 7000) // 도로 진입 이후에는 60m 규칙으로 전환
        tracker.observe(origin, 8000)
        tracker.observe(origin, 12000)
        assertTrue(tracker.offRoute)
        assertTrue(tracker.shouldRequest(12000, false))
    }

    @Test fun roadPositionAndProjectionRoundTrip() {
        val route = road()
        for (d in listOf(0f, 100f, 500f, 999f, 1000f)) {
            assertEquals(d, route.project(route.pointAt(d)).distance, .1f)
        }
        assertEquals(origin, route.pointAt(-10f))
        assertEquals(route.points.last(), route.pointAt(2000f))
    }

    @Test fun etaUsesRouteSegmentTimesAndManeuverUsesRouteDistance() {
        val route = road()
        assertEquals(90f, route.remainingDuration(200f), .01f)
        assertEquals(40f, route.remainingDuration(700f), .01f)
        assertEquals(0f, route.remainingDuration(1000f), .01f)
        assertEquals("좌회전", route.nextManeuver(200f)!!.instruction)
        assertEquals("arrive", route.nextManeuver(410f)!!.type)
    }

    @Test fun singleGpsOutlierDoesNotTriggerReroute() {
        val tracker = tracker()
        tracker.observe(GeoPoint(37.002, 127.0), 1000)
        tracker.observe(GeoPoint(37.002, 127.002), 2000)
        assertTrue(tracker.offRoute)
        assertFalse(tracker.shouldRequest(6000, false))
        tracker.observe(GeoPoint(37.002, 127.0), 6500)
        assertFalse(tracker.offRoute)
        assertFalse(tracker.shouldRequest(10000, false))
    }

    @Test fun sustainedOffRouteTriggersOneRequestAndHonorsCooldown() {
        val tracker = tracker()
        tracker.markRequested(0)
        tracker.observe(GeoPoint(37.002, 127.0), 1000)
        tracker.observe(GeoPoint(37.002, 127.002), 16000)
        assertFalse(tracker.shouldRequest(17000, false))
        tracker.observe(GeoPoint(37.002, 127.002), 19500)
        assertTrue(tracker.shouldRequest(19500, false))
        assertFalse(tracker.shouldRequest(19500, true))
        tracker.markRequested(19500)
        assertFalse(tracker.shouldRequest(30000, false))
        assertTrue(tracker.shouldRequest(35000, false))
    }

    @Test fun returnedGpsClearsOffRouteAndSmallBackwardJitterIsIgnored() {
        val tracker = tracker()
        tracker.observe(road().pointAt(500f), 1000)
        tracker.observe(road().pointAt(490f), 2000)
        assertEquals(500f, tracker.progress, .1f)
        tracker.observe(GeoPoint(37.005, 127.002), 3000)
        tracker.observe(road().pointAt(400f), 7000)
        assertFalse(tracker.offRoute)
        assertEquals(400f, tracker.progress, .1f)
    }

    @Test fun arrivalNeedsTwoNearDestinationFixesAndCannotArriveOffRoute() {
        val tracker = tracker()
        tracker.observe(road().pointAt(990f), 1000)
        assertFalse(tracker.arrived)
        tracker.observe(road().pointAt(1000f), 2000)
        assertTrue(tracker.arrived)
        tracker.observe(GeoPoint(37.01, 127.002), 3000)
        assertFalse(tracker.arrived)
    }

    @Test fun reroutedGeometryReplacesOldProgressAndDemoFollowsRoad() {
        val tracker = tracker()
        tracker.advanceDemo(500f)
        assertEquals(500f, tracker.progress, 0f)
        assertEquals(road().pointAt(500f), tracker.position)
        tracker.install(road())
        assertEquals(0f, tracker.progress, 0f)
        tracker.advanceDemo(Float.NaN)
        assertEquals(0f, tracker.progress, 0f)
        tracker.advanceDemo(2000f)
        assertEquals(1000f, tracker.progress, 0f)
        assertTrue(tracker.arrived)
    }

    @Test fun invalidGpsDoesNotChangeNavigation() {
        val tracker = tracker()
        tracker.observe(GeoPoint(Double.NaN, 127.0), 1000)
        assertEquals(0f, tracker.progress, 0f)
        assertFalse(tracker.offRoute)
    }

    @Test fun koreanRoundaboutAndUTurnInstructions() {
        assertEquals("회전교차로 3번째 출구", RoadManeuver(0f, "", "roundabout", "right", 3).instruction)
        assertEquals("유턴", RoadManeuver(0f, "", "turn", "uturn").instruction)
        assertEquals("왼쪽 합류", RoadManeuver(0f, "", "merge", "slight left").instruction)
    }

    private fun response(code: String = "Ok") = """
        {"code":"$code","routes":[{"distance":200,"duration":100,
          "geometry":{"type":"LineString","coordinates":[[127,37],[127.001,37],[127.001,37.001]]},
          "legs":[{"steps":[
            {"distance":80,"duration":20,"name":"정릉로","maneuver":{"type":"depart","modifier":"straight"}},
            {"distance":120,"duration":80,"name":"내부순환로","maneuver":{"type":"turn","modifier":"left"}},
            {"distance":0,"duration":0,"name":"","maneuver":{"type":"arrive"}}
          ]}]}]}
    """.trimIndent()

    @Test fun osrmParserPreservesKoreanRoadsLonLatOrderAndStepOffsets() {
        val route = OsrmRouteParser.parse(response(), origin)
        assertEquals(origin, route.points.first())
        assertEquals(GeoPoint(37.001, 127.001), route.points.last())
        assertEquals(200f, route.distance, 0f)
        assertEquals(100f, route.duration, 0f)
        assertEquals(80f, route.maneuvers[1].atMeters, 0f)
        assertEquals("내부순환로", route.maneuvers[1].name)
        assertEquals("", route.maneuvers.last().modifier)
        assertEquals(90f, route.remainingDuration(40f), .01f)
    }

    @Test fun noRouteAndNoSegmentAreReportedAsRoutingFailures() {
        for (code in listOf("NoRoute", "NoSegment")) {
            try {
                OsrmRouteParser.parse(response(code), origin)
                fail("라우팅 실패를 경로로 표시하면 안 돼요")
            } catch (_: RoutingException) { }
        }
    }

    @Test fun malformedGeometryAndNonFiniteMetricsAreRejected() {
        for (json in listOf("{}", response().replace("[127,37]", "[127,137]"), response().replace("\"distance\":200", "\"distance\":-1"))) {
            try {
                OsrmRouteParser.parse(json, origin)
                fail("잘못된 경로 응답을 수용하면 안 돼요")
            } catch (_: RoutingException) { }
        }
    }
}
