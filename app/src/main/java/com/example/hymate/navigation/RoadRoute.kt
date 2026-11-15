package com.example.hymate.navigation

import com.example.hymate.data.GeoPoint
import kotlin.math.*

val NAV_ORIGIN = GeoPoint(37.609717, 126.996661) // 국민대 정문 진입 도로
val NAV_DESTINATION = GeoPoint(37.4637, 127.0403) // 기존 양재 본사 목적지

fun distanceBetween(a: GeoPoint, b: GeoPoint): Double {
    val dy = Math.toRadians(b.lat - a.lat)
    val dx = Math.toRadians(b.lng - a.lng)
    val h = sin(dy / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dx / 2).pow(2)
    return 6_371_000.0 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

data class RoadManeuver(
    val atMeters: Float,
    val name: String,
    val type: String,
    val modifier: String,
    val exit: Int? = null,
) {
    val instruction: String get() = when {
        type == "arrive" -> "목적지 도착"
        type in setOf("exit roundabout", "exit rotary") -> "회전교차로 진출"
        type in setOf("roundabout", "rotary", "roundabout turn") ->
            exit?.let { "회전교차로 ${it}번째 출구" } ?: "회전교차로 진입"
        modifier == "uturn" -> "유턴"
        type == "merge" -> if (modifier.contains("left")) "왼쪽 합류" else "오른쪽 합류"
        type in setOf("on ramp", "off ramp", "fork") -> when {
            modifier.contains("left") -> "왼쪽 분기"
            modifier.contains("right") -> "오른쪽 분기"
            else -> "분기 진입"
        }
        modifier.contains("left") -> if (modifier.contains("slight")) "완만한 좌회전" else "좌회전"
        modifier.contains("right") -> if (modifier.contains("slight")) "완만한 우회전" else "우회전"
        else -> "직진"
    }
}

data class RouteProjection(val distance: Float, val offset: Double, val point: GeoPoint)

/** 탐색된 도로 폴리라인과 OSRM 거리·구간별 예상 시간. 손도화 경로와 독립적이에요. */
class RoadRoute(
    val points: List<GeoPoint>,
    val distance: Float,
    val duration: Float,
    val maneuvers: List<RoadManeuver>,
    val timing: List<Pair<Float, Float>>,
    val requestedOrigin: GeoPoint,
) {
    private val cumulative = DoubleArray(points.size).apply {
        for (i in 1 until size) this[i] = this[i - 1] + distanceBetween(points[i - 1], points[i])
    }
    private val length = cumulative.last()
    init {
        require(points.size >= 2 && points.all { it.valid })
        require(distance.isFinite() && distance >= 0 && duration.isFinite() && duration >= 0 && (length > 0 || distance == 0f))
    }

    fun pointAt(meters: Float): GeoPoint {
        if (distance == 0f || length == 0.0) return points.last()
        val target = (if (meters.isFinite()) meters else 0f).coerceIn(0f, distance) / distance * length
        val end = cumulative.binarySearch(target).let { if (it >= 0) it else -it - 1 }.coerceIn(1, points.lastIndex)
        val span = cumulative[end] - cumulative[end - 1]
        val t = if (span > 0) ((target - cumulative[end - 1]) / span).coerceIn(0.0, 1.0) else 0.0
        val a = points[end - 1]
        val b = points[end]
        return GeoPoint(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)
    }

    fun project(point: GeoPoint, previous: Float? = null): RouteProjection {
        if (length == 0.0) return RouteProjection(0f, distanceBetween(point, points.last()), points.last())
        val xScale = 111_320.0 * cos(Math.toRadians(point.lat))
        var best: RouteProjection? = null
        var local: RouteProjection? = null
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val bx = (b.lng - a.lng) * xScale
            val by = (b.lat - a.lat) * 111_320.0
            val len2 = bx * bx + by * by
            if (len2 < .01) continue
            val px = (point.lng - a.lng) * xScale
            val py = (point.lat - a.lat) * 111_320.0
            val t = ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
            val offset = hypot(px - bx * t, py - by * t)
            val d = ((cumulative[i - 1] + t * (cumulative[i] - cumulative[i - 1])) / length * distance).toFloat()
            val candidate = RouteProjection(d, offset, GeoPoint(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t))
            if (best == null || offset < best.offset) best = candidate
            // 겹치는 교차로·평행 구간은 직전 진행도 주변을 우선해 순간 점프를 줄여요.
            if (previous != null && abs(d - previous) <= 120f && (local == null || offset < local.offset)) local = candidate
        }
        val nearest = requireNotNull(best)
        return local?.takeIf { it.offset < 35 && it.offset <= nearest.offset + 10 } ?: nearest
    }

    fun nextManeuver(progress: Float): RoadManeuver? =
        maneuvers.firstOrNull { it.type !in setOf("depart", "new name", "notification") && it.atMeters >= progress - 5f }

    fun remainingDuration(progress: Float): Float {
        if (distance == 0f) return 0f
        val d = progress.coerceIn(0f, distance)
        val index = timing.indexOfLast { it.first <= d }.coerceAtLeast(0)
        val start = timing.getOrNull(index) ?: return duration * (1 - d / distance)
        val end = timing.getOrNull(index + 1) ?: (distance to duration)
        val fraction = if (end.first > start.first) (d - start.first) / (end.first - start.first) else 1f
        return (duration - (start.second + fraction * (end.second - start.second))).coerceAtLeast(0f)
    }
}

/** GPS 오차와 실제 경로 이탈을 구분하고 재탐색 요청 빈도를 제한하는 순수 상태 객체. */
class RouteTracker {
    var route: RoadRoute? = null; private set
    var progress = 0f; private set
    var position: GeoPoint? = null; private set
    var offRoute = false; private set
    var arrived = false; private set
    private var onRoadSeen = false
    private var offSince: Long? = null
    private var offSamples = 0
    private var arrivalSamples = 0
    private var requestAt: Long? = null

    fun install(value: RoadRoute) {
        route = value
        progress = 0f
        position = value.pointAt(0f)
        offRoute = false
        arrived = false
        onRoadSeen = false
        offSince = null
        offSamples = 0
        arrivalSamples = 0
    }

    fun advanceDemo(meters: Float) {
        val current = route ?: return
        if (!meters.isFinite() || meters <= 0) return
        progress = (progress + meters).coerceAtMost(current.distance)
        position = current.pointAt(progress)
        arrived = progress >= current.distance
    }

    fun observe(point: GeoPoint, now: Long) {
        if (!point.valid) return
        val current = route ?: return
        val projection = current.project(point, progress)
        if (projection.offset <= 35) onRoadSeen = true
        val threshold = if (onRoadSeen) 60.0 else maxOf(60.0, distanceBetween(current.requestedOrigin, current.points.first()) + 20)
        offRoute = projection.offset > threshold
        position = if (projection.offset <= 35) projection.point else point
        if (offRoute) {
            offSince = offSince ?: now
            offSamples++
            arrivalSamples = 0
            arrived = false
            return
        }
        offSince = null
        offSamples = 0
        if (projection.distance >= progress || progress - projection.distance > 15) progress = projection.distance
        val nearEnd = current.distance - progress < 40 && distanceBetween(point, current.points.last()) < 40
        arrivalSamples = if (nearEnd) arrivalSamples + 1 else 0
        arrived = arrivalSamples >= 2
    }

    fun shouldRequest(now: Long, pending: Boolean, hasRoute: Boolean = route != null): Boolean {
        if (pending || requestAt?.let { now - it < 15_000L } == true) return false
        if (!hasRoute) return true
        return offRoute && offSamples >= 2 && offSince?.let { now - it >= 3_000L } == true
    }

    fun markRequested(now: Long) { requestAt = now }
}
