package com.example.hymate.data

import kotlin.math.cos
import kotlin.math.sqrt

/**
 * 실제 경로(국민대 → 내부순환로 → 영동대교 → 현대차 양재 본사)의 근사 위경도 앵커.
 * 경로거리 d(m)와 짝지어 차량 GPS를 "경로 위 거리"로 변환해요(지도 스냅).
 * 데모 정밀도의 손도화 좌표라 실도로와 수십 m 오차가 있을 수 있어요 — 허용 오차로 흡수해요.
 */
private class GeoAnchor(val d: Float, val lat: Double, val lng: Double)

data class GeoPoint(val lat: Double, val lng: Double) {
    val valid: Boolean get() = lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0
}

/** 지도에 그리는 데모 근사 경로. 실제 길찾기 결과가 아니에요. */
val demoRoutePoints: List<GeoPoint> get() = GEO.map { GeoPoint(it.lat, it.lng) }

/** 차속으로 적분한 데모 진행도에 대응하는 좌표. 시스템 GPS는 바꾸지 않아요. */
fun routePointAt(meters: Float): GeoPoint {
    val distance = if (meters.isFinite()) meters.coerceIn(0f, GEO.last().d) else 0f
    val end = GEO.indexOfFirst { it.d >= distance }.coerceAtLeast(0)
    if (end == 0) return GeoPoint(GEO.first().lat, GEO.first().lng)
    val a = GEO[end - 1]
    val b = GEO[end]
    val t = (distance - a.d) / (b.d - a.d)
    return GeoPoint(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)
}

private val GEO = listOf(
    GeoAnchor(0f, 37.6109, 126.9973), // 국민대학교
    GeoAnchor(650f, 37.6052, 126.9938), // 정문 앞 교차로
    GeoAnchor(1500f, 37.5990, 126.9925), // 정릉 어린이보호구역
    GeoAnchor(2300f, 37.5922, 126.9924), // 정릉삼거리
    GeoAnchor(3100f, 37.5878, 126.9960), // 내부순환 정릉 진입
    GeoAnchor(3800f, 37.5869, 127.0030), // 정릉터널
    GeoAnchor(4600f, 37.5872, 127.0115),
    GeoAnchor(6400f, 37.5810, 127.0255),
    GeoAnchor(9200f, 37.5633, 127.0375), // 성수JC
    GeoAnchor(11000f, 37.5400, 127.0555), // 영동대교 북단
    GeoAnchor(11800f, 37.5280, 127.0575), // 영동대교 남단
    GeoAnchor(12600f, 37.5195, 127.0530), // 청담사거리
    GeoAnchor(14500f, 37.5085, 127.0600),
    GeoAnchor(16800f, 37.4967, 127.0707), // 학여울역
    GeoAnchor(18500f, 37.4875, 127.0585), // 도곡동
    GeoAnchor(20400f, 37.4702, 127.0372), // 양재 시민의숲
    GeoAnchor(21250f, 37.4650, 127.0395), // 본사 앞
    GeoAnchor(21420f, 37.4637, 127.0403), // 현대자동차 양재 본사
)

/**
 * GPS를 경로에 수선 투영해서, 경로에서 [toleranceMeters] 안이면 경로거리(m)를 반환해요.
 * 경로를 벗어났으면 null — 호출자는 차속 적분을 그대로 유지해요.
 */
fun routeDistanceOf(lat: Double, lng: Double, toleranceMeters: Float = 150f): Float? {
    val mPerDegLat = 111_320.0
    val mPerDegLng = 111_320.0 * cos(Math.toRadians(lat))
    var best: Float? = null
    var bestPerp = toleranceMeters
    for (i in 1 until GEO.size) {
        val a = GEO[i - 1]
        val b = GEO[i]
        val bx = ((b.lng - a.lng) * mPerDegLng).toFloat()
        val by = ((b.lat - a.lat) * mPerDegLat).toFloat()
        val px = ((lng - a.lng) * mPerDegLng).toFloat()
        val py = ((lat - a.lat) * mPerDegLat).toFloat()
        val len2 = bx * bx + by * by
        if (len2 < 1f) continue
        val t = ((px * bx + py * by) / len2).coerceIn(0f, 1f)
        val dx = px - t * bx
        val dy = py - t * by
        val perp = sqrt(dx * dx + dy * dy)
        if (perp <= bestPerp) {
            bestPerp = perp
            best = a.d + t * (b.d - a.d)
        }
    }
    return best
}
