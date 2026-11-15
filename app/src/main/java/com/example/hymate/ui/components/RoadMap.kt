package com.example.hymate.ui.components

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import com.example.hymate.data.GeoPoint
import com.example.hymate.navigation.RoadRoute
import com.example.hymate.navigation.NAV_ORIGIN
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.File

/** OSM 도로·지명 위에 실제 탐색된 자동차 경로를 표시해요. */
@Composable
fun RoadMap(position: () -> GeoPoint?, route: RoadRoute?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var status by remember { mutableStateOf<String?>("지도를 불러오는 중") }
    val view = remember(context) { RoadMapView(context) { status = it } }
    DisposableEffect(view, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view.start()
                Lifecycle.Event.ON_RESUME -> view.resume()
                Lifecycle.Event.ON_PAUSE -> view.pause()
                Lifecycle.Event.ON_STOP -> view.stop()
                Lifecycle.Event.ON_DESTROY -> view.release()
                else -> Unit
            }
        }
        context.applicationContext.registerComponentCallbacks(view)
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            context.applicationContext.unregisterComponentCallbacks(view)
            view.release()
        }
    }
    Box(modifier) {
        AndroidView(
            factory = { view },
            modifier = Modifier.fillMaxSize(),
            update = { it.show(position(), route) },
        )
        status?.let {
            HText(
                it, 28, Modifier.align(Alignment.Center)
                    .background(Color.White.copy(alpha = .94f)).padding(12.dp),
                color = Color(0xFF1B2129),
            )
        }
        // ETA/상태 카드와 겹치지 않게 항상 노출해요.
        HText(
            "© OpenStreetMap contributors", 22,
            Modifier.align(Alignment.BottomCenter)
                .background(Color.White.copy(alpha = .94f)).padding(horizontal = 6.dp, vertical = 2.dp),
            color = Color(0xFF1B2129),
        )
    }
}

private class RoadMapView(context: Context, private val onStatus: (String?) -> Unit) :
    FrameLayout(context), ComponentCallbacks {
    private val mapView: MapView
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var position: GeoPoint? = null
    private var route: RoadRoute? = null
    private var renderedRoute: RoadRoute? = null
    private var started = false
    private var resumed = false
    private var destroyed = false
    private var lastCameraPoint: GeoPoint? = null
    private var lastCameraAt = 0L

    init {
        MapLibre.getInstance(context.applicationContext)
        MapNetwork.initialize(context.applicationContext)
        mapView = MapView(
            context, MapLibreMapOptions.createFromAttributes(context)
                .textureMode(true)
                .attributionEnabled(false) // 위의 항상 보이는 크레딧으로 대신해요.
                .logoEnabled(false)
                .compassEnabled(false)
                .apply { prefetchZoomDelta = 0 },
        )
        addView(mapView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        mapView.onCreate(null)
        mapView.addOnDidFailLoadingMapListener {
            if (!destroyed) onStatus("지도를 불러오지 못했어요 · 인터넷 연결 확인")
        }
        mapView.addOnDidFinishRenderingMapListener { fully ->
            if (fully && !destroyed) onStatus(null)
        }
        mapView.getMapAsync { ready ->
            if (destroyed) return@getMapAsync
            map = ready
            ready.setPrefetchZoomDelta(0)
            ready.uiSettings.setAllGesturesEnabled(false)
            ready.cameraPosition = CameraPosition.Builder()
                .target(NAV_ORIGIN.toLatLng()).zoom(16.5).build()
            ready.setStyle(Style.Builder().fromJson(mapStyle())) { loaded ->
                if (destroyed) return@setStyle
                style = loaded
                render(force = true)
            }
        }
    }

    fun show(point: GeoPoint?, nextRoute: RoadRoute?) {
        if (destroyed) return
        if (position == point && route === nextRoute) return
        position = point?.takeIf { it.valid }
        route = nextRoute
        render()
    }

    private fun render(force: Boolean = false) {
        val loaded = style ?: return
        if (force || renderedRoute !== route) {
            val coordinates = route?.points?.joinToString(",") { "[${it.lng},${it.lat}]" }
            loaded.getSourceAs<GeoJsonSource>("route")?.setGeoJson(
                if (coordinates == null) """{"type":"FeatureCollection","features":[]}"""
                else """{"type":"LineString","coordinates":[$coordinates]}""",
            )
            renderedRoute = route
        }
        val point = position
        loaded.getSourceAs<GeoJsonSource>("car")?.setGeoJson(
            if (point == null) """{"type":"FeatureCollection","features":[]}"""
            else """{"type":"Point","coordinates":[${point.lng},${point.lat}]}""",
        )
        if (point == null) return
        val now = SystemClock.elapsedRealtime()
        // SDK/카메라 갱신을 2Hz로 제한해요. Compose의 100ms 주행 루프와 분리해요.
        if (!force && lastCameraPoint != null && now - lastCameraAt < 500L) return
        if (!force && point == lastCameraPoint) return
        lastCameraPoint = point
        lastCameraAt = now
        map?.cameraPosition = CameraPosition.Builder()
            .target(point.toLatLng()).zoom(16.5).build()
    }

    fun start() { if (!destroyed && !started) { mapView.onStart(); started = true } }
    fun resume() { if (!destroyed && !resumed) { start(); mapView.onResume(); resumed = true } }
    fun pause() { if (!destroyed && resumed) { mapView.onPause(); resumed = false } }
    fun stop() { pause(); if (!destroyed && started) { mapView.onStop(); started = false } }
    fun release() {
        if (destroyed) return
        stop()
        destroyed = true
        map = null
        style = null
        mapView.onDestroy()
    }
    override fun onLowMemory() { if (!destroyed) mapView.onLowMemory() }
    override fun onConfigurationChanged(newConfig: Configuration) = Unit
}

private fun GeoPoint.toLatLng() = LatLng(lat, lng)

/** 앱 수명 동안 공유. OSM 정책에 따라 식별 헤더·HTTP 디스크 캐시·뷰포트 한정 요청을 사용해요. */
private object MapNetwork {
    private var initialized = false
    fun initialize(context: Context) {
        if (initialized) return
        val client = OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "map-http"), 50L * 1024 * 1024))
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder()
                    .header("User-Agent", "Hymate/1.0 (ai.hymate.app; Android)")
                    .build())
            }.build()
        HttpRequestUtil.setOkHttpClient(client)
        initialized = true
    }
}

private fun mapStyle(): String {
    return """
        {
          "version":8,
          "sources":{
            "osm":{"type":"raster","tiles":["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],"tileSize":256,"maxzoom":19,
              "attribution":"© OpenStreetMap contributors"},
            "route":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}},
            "car":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}}
          },
          "layers":[
            {"id":"background","type":"background","paint":{"background-color":"#EAEEF3"}},
            {"id":"roads","type":"raster","source":"osm"},
            {"id":"navigation-route","type":"line","source":"route","layout":{"line-cap":"round","line-join":"round"},
              "paint":{"line-color":"#0066FF","line-width":6}},
            {"id":"car-halo","type":"circle","source":"car","paint":{"circle-radius":18,"circle-color":"#0066FF","circle-opacity":0.15}},
            {"id":"car-point","type":"circle","source":"car","paint":{"circle-radius":8,"circle-color":"#0066FF","circle-stroke-width":3,"circle-stroke-color":"#FFFFFF"}}
          ]
        }
    """.trimIndent()
}
