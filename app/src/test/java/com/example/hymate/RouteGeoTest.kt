package com.example.hymate

import com.example.hymate.data.GeoPoint
import com.example.hymate.data.routeDistanceOf
import com.example.hymate.data.routePointAt
import org.junit.Assert.*
import org.junit.Test

class RouteGeoTest {
    @Test fun demoAlwaysStartsAtKookminAndClampsAtDestination() {
        assertEquals(GeoPoint(37.6109, 126.9973), routePointAt(0f))
        assertEquals(routePointAt(0f), routePointAt(-100f))
        assertEquals(routePointAt(0f), routePointAt(Float.NaN))
        assertEquals(GeoPoint(37.4637, 127.0403), routePointAt(100000f))
    }

    @Test fun interpolatedDemoPositionProjectsBackToItsProgress() {
        for (distance in listOf(0f, 300f, 1500f, 5600f, 11000f, 21000f, 21420f)) {
            val point = routePointAt(distance)
            assertEquals(distance, routeDistanceOf(point.lat, point.lng)!!, 1f)
        }
    }

    @Test fun offRouteGpsIsNotMistakenForProgress() {
        assertNull(routeDistanceOf(37.421998, -122.084))
    }

    @Test fun invalidPositionsCannotEnterTheMap() {
        assertFalse(GeoPoint(Double.NaN, 127.0).valid)
        assertFalse(GeoPoint(37.0, Double.POSITIVE_INFINITY).valid)
        assertFalse(GeoPoint(91.0, 127.0).valid)
        assertFalse(GeoPoint(37.0, 181.0).valid)
        assertTrue(routePointAt(0f).valid)
    }
}
