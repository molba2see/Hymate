package com.example.hymate.navigation

import com.example.hymate.data.GeoPoint
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RoutingException(message: String) : IOException(message)

interface RoutingClient {
    suspend fun route(origin: GeoPoint, destination: GeoPoint): RoadRoute
}

/** IO는 OkHttp 비동기 콜백에서 처리하고, 코루틴 취소 시 HTTP 요청도 즉시 취소해요. */
class OsrmRoutingClient(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build(),
) : RoutingClient {
    private val requestGate = Mutex()
    private var lastRequestNanos = 0L
    init { require(baseUrl.toHttpUrl().isHttps) { "라우팅 서버는 HTTPS가 필요해요" } }

    override suspend fun route(origin: GeoPoint, destination: GeoPoint): RoadRoute {
        require(origin.valid && destination.valid)
        requestGate.withLock {
            val waitMillis = if (lastRequestNanos == 0L) 0L else 1100L - (System.nanoTime() - lastRequestNanos) / 1_000_000L
            if (waitMillis > 0) delay(waitMillis)
            lastRequestNanos = System.nanoTime()
        }
        val coordinates = "${origin.lng},${origin.lat};${destination.lng},${destination.lat}"
        val url = "${baseUrl.trimEnd('/')}/route/v1/driving/$coordinates".toHttpUrl().newBuilder()
            .addQueryParameter("steps", "true")
            .addQueryParameter("geometries", "geojson")
            .addQueryParameter("overview", "full")
            .addQueryParameter("alternatives", "false")
            .addQueryParameter("generate_hints", "false")
            .addQueryParameter("radiuses", "150;150")
            .build()
        val request = Request.Builder().url(url).header("User-Agent", "Hymate/1.0 (ai.hymate.app; Android)").build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(RoutingException("경로 서버에 연결하지 못했어요. 자동으로 다시 시도해요."))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (it.code == 429) throw RoutingException("경로 요청이 많아요. 잠시 후 다시 시도해요.")
                            if (!it.isSuccessful) throw RoutingException("경로 서버 응답 오류 (${it.code})")
                            OsrmRouteParser.parse(it.body.string(), origin)
                        }
                    }
                    if (continuation.isActive) result.fold(
                        onSuccess = { continuation.resume(it) },
                        onFailure = { continuation.resumeWithException(it) },
                    )
                }
            })
        }
    }
}

/** Android 런타임에 의존하지 않아 실제 OSRM 응답 규약을 로컬 단위 테스트로 검증할 수 있어요. */
object OsrmRouteParser {
    fun parse(json: String, origin: GeoPoint): RoadRoute {
        try {
            val response = JsonParser.parseString(json).asJsonObject
            when (response.get("code")?.asString) {
                "Ok" -> Unit
                "NoRoute" -> throw RoutingException("목적지까지 자동차 경로를 찾지 못했어요.")
                "NoSegment" -> throw RoutingException("현재 위치 또는 목적지 주변에 진입 가능한 도로가 없어요.")
                else -> throw RoutingException("경로 탐색을 완료하지 못했어요.")
            }
            val route = response.getAsJsonArray("routes").firstOrNull()?.asJsonObject
                ?: throw RoutingException("반환된 경로가 없어요.")
            val distance = route.get("distance").asFloat
            val duration = route.get("duration").asFloat
            val geometry = route.getAsJsonObject("geometry")
            require(geometry.get("type").asString == "LineString")
            val points = geometry.getAsJsonArray("coordinates").map {
                val coordinates = it.asJsonArray
                GeoPoint(coordinates[1].asDouble, coordinates[0].asDouble)
            }
            require(points.size >= 2 && points.all { it.valid })
            val turns = mutableListOf<RoadManeuver>()
            val timing = mutableListOf<Pair<Float, Float>>()
            var meters = 0f
            var seconds = 0f
            for (leg in route.getAsJsonArray("legs")) {
                for (item in leg.asJsonObject.getAsJsonArray("steps")) {
                    val step = item.asJsonObject
                    val maneuver = step.getAsJsonObject("maneuver")
                    turns += RoadManeuver(
                        meters,
                        step.get("name")?.asString?.ifBlank { null } ?: step.get("ref")?.asString.orEmpty(),
                        maneuver.get("type").asString,
                        maneuver.get("modifier")?.asString.orEmpty(),
                        maneuver.get("exit")?.asInt,
                    )
                    timing += meters to seconds
                    val stepDistance = step.get("distance").asFloat
                    val stepDuration = step.get("duration").asFloat
                    require(stepDistance.isFinite() && stepDistance >= 0 && stepDuration.isFinite() && stepDuration >= 0)
                    meters += stepDistance
                    seconds += stepDuration
                }
            }
            require(turns.isNotEmpty() && (meters > 0 || distance == 0f))
            val scale = if (meters > 0) distance / meters else 0f
            val timeScale = if (seconds > 0) duration / seconds else 0f
            return RoadRoute(
                points, distance, duration,
                turns.map { it.copy(atMeters = it.atMeters * scale) },
                timing.map { (d, t) -> d * scale to t * timeScale } + (distance to duration),
                origin,
            )
        } catch (e: RoutingException) {
            throw e
        } catch (e: Exception) {
            throw RoutingException("경로 응답을 해석하지 못했어요.")
        }
    }
}
