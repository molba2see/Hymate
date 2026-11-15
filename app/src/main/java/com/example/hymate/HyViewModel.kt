package com.example.hymate

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hymate.data.DEST
import com.example.hymate.data.GeoPoint
import com.example.hymate.navigation.NAV_ORIGIN
import com.example.hymate.navigation.NAV_DESTINATION
import com.example.hymate.navigation.RoadRoute
import com.example.hymate.navigation.RouteTracker
import com.example.hymate.navigation.OsrmRoutingClient
import kotlinx.coroutines.CancellationException
import com.example.hymate.data.GD
import com.example.hymate.data.GType
import com.example.hymate.data.GUIDES
import com.example.hymate.data.Growth
import com.example.hymate.data.PStep
import com.example.hymate.data.Quest
import com.example.hymate.data.TODAY_KEY
import com.example.hymate.data.UserProfile
import com.example.hymate.data.Vehicle
import com.example.hymate.data.VehicleSignal
import com.example.hymate.data.XP
import com.example.hymate.data.buildQuests
import com.example.hymate.data.fbText
import com.example.hymate.data.guide
import com.example.hymate.data.seedUser
import com.example.hymate.data.tierOf
import com.example.hymate.data.todayQuest
import com.example.hymate.vehicle.VehicleLink
import com.example.hymate.vehicle.LiveDriveSession
import com.example.hymate.vehicle.LiveQuestEvaluator
import com.example.hymate.vehicle.LiveQuestPlan
import com.example.hymate.vehicle.QuestRouteState
import com.example.hymate.vehicle.hasFreshSpeed
import kotlin.math.abs
import com.example.hymate.speech.SpeechEngine
import com.example.hymate.speech.SpeechConversation
import com.example.hymate.speech.VoiceAnswer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen { Home, Settings, Pre, Guide, Drive, Report }

/** 홈 화면에 보여줄 직전 주행 요약 */
data class DriveLog(val ok: Int, val miss: Int, val gain: Int, val grads: Int,
    val total: Int = 0, val pending: Int = 0, val selfChecked: Int = 0, val demo: Boolean = false)

/** 동의 값: on / off / ask(Hy! mate가 물어보기) */
data class Consents(val feedback: String = "on", val cond: String = "ask", val chime: String = "on")

data class ConsentRecord(val item: String, val value: String, val channel: String)

data class PracticeState(val step: Int = 0, val done: Boolean = false)

sealed interface VoiceUi {
    data class Feedback(val text: String) : VoiceUi
    data class Question(val text: String, val listening: Boolean, val remain: Float) : VoiceUi
    data class Confirm(val text: String) : VoiceUi
}

/**
 * 화면 상태 + 실제 차량 속도에 따른 주행 기록.
 * 실제 지도 위에 Pleos GPS 또는 차속 기반 데모 위치를 표시해요.
 */
class HyViewModel : ViewModel() {
    var screen by mutableStateOf(Screen.Home); private set
    private var settingsFrom = Screen.Home
    private var guideFrom = Screen.Pre
    var lastDrive by mutableStateOf<DriveLog?>(null); private set
    var profile by mutableStateOf("L0"); private set
    private val users = mutableMapOf<String, UserProfile>()
    var user by mutableStateOf(seedUser("L0")); private set

    /** 우천 추정: 날씨 서버 미연동이라 기획서 폴백대로 와이퍼 실제 작동(WiperState.ON)으로 판단해요. */
    val rainy: Boolean get() = vehicle.wiping
    var consents by mutableStateOf(Consents()); private set
    var consentLog by mutableStateOf(
        listOf(
            ConsentRecord("서비스 성격 고지", "확인함", "터치 · 최초 실행"),
            ConsentRecord("실시간 피드백", "켜기", "터치 · 동승 설정"),
            ConsentRecord("상황 가이드", "Hy! mate가 물어보기", "터치 · 동승 설정"),
            ConsentRecord("접근 알림음", "켜기", "터치 · 동승 설정"),
        ),
    ); private set

    var vehicle by mutableStateOf(Vehicle()); private set
    /** 레버·벨트 등 Pleos 차량 데이터 연결 상태. */
    var vehicleLink by mutableStateOf<VehicleLink>(VehicleLink.Simulated); private set
    var speedLink by mutableStateOf<VehicleLink>(VehicleLink.Simulated); private set
    var locationLink by mutableStateOf<VehicleLink>(VehicleLink.Simulated); private set
    var gpsPosition by mutableStateOf<GeoPoint?>(null); private set
    var emulatorDemoAvailable by mutableStateOf(false); private set
    var demoRoute by mutableStateOf(false); private set
    var driveUsesDemo by mutableStateOf(false); private set
    private var mapModeInitialized = false
    var roadRoute by mutableStateOf<RoadRoute?>(null); private set
    var navigationProgress by mutableFloatStateOf(0f); private set
    var navigationPosition by mutableStateOf<GeoPoint?>(null); private set
    var navigationOffRoute by mutableStateOf(false); private set
    var routeLoading by mutableStateOf(false); private set
    var routeError by mutableStateOf<String?>(null); private set
    var navigationFixFresh by mutableStateOf(false); private set
    var arrivalPredictionMillis by mutableStateOf<Long?>(null); private set
    private var tracker = RouteTracker()
    private var questRouteVersion = 0
    private val router = OsrmRoutingClient(BuildConfig.ROUTING_BASE_URL)
    private var routeJob: Job? = null
    private var driveGeneration = 0L
    private var routeRetryAt = 0L
    private var gpsReceivedAt = 0L
    private var lastEtaUpdate = 0L
    private var navigationVisible = false

    fun onNavigationVisibility(visible: Boolean) {
        navigationVisible = visible
        if (!visible) {
            driveGeneration++
            routeJob?.cancel()
            routeLoading = false
        }
    }
    val mapPosition: GeoPoint? get() = if (driveUsesDemo) navigationPosition ?: NAV_ORIGIN
        else gpsPosition?.let { navigationPosition ?: it }
    val navigationReady: Boolean get() = roadRoute != null && !routeLoading && !navigationOffRoute &&
        (driveUsesDemo || navigationFixFresh)
    val navigationRemainingMeters: Float? get() = if (navigationReady)
        roadRoute?.let { (it.distance - navigationProgress).coerceAtLeast(0f) } else null
    val navigationStatus: String get() = when {
        !driveUsesDemo && !navigationFixFresh -> "실제 GPS · 위치 수신 대기"
        routeLoading -> if (roadRoute == null) "도로 경로 탐색 중" else "경로 이탈 · 재탐색 중"
        routeError != null -> routeError!!
        navigationOffRoute -> "경로 이탈 · 재탐색 대기"
        driveUsesDemo -> "도로 주행 데모 · 차속 연동 · 교통정보 미반영"
        else -> "실제 GPS · 양재 본사 안내 · 교통정보 미반영"
    }

    /** 디버그 에뮬레이터에서만 기본값을 데모로 둬요. 실차는 항상 실제 GPS로 시작해요. */
    fun configureEmulatorDemo(available: Boolean) {
        if (mapModeInitialized) return
        mapModeInitialized = true
        emulatorDemoAvailable = available
        demoRoute = available
    }

    fun selectDemoRoute(enabled: Boolean) {
        if (!emulatorDemoAvailable) return
        demoRoute = enabled
        showToast("다음 주행부터 ${if (enabled) "국민대 출발 데모" else "실제 GPS"}를 사용해요.")
    }
    var motionAvailable by mutableStateOf(false); private set
    var remainingSeconds by mutableStateOf<Float?>(null); private set
    var arrivedAtMillis by mutableStateOf(0L); private set
    var practice by mutableStateOf(mapOf<String, PracticeState>()); private set
    var selectedGuide by mutableStateOf<String?>(null); private set

    /** 차종별 미지원 가이드 (기획서: 지원 기능 확인 후 자동 제외). capability 조회 결과로 채워요. */
    var unsupportedGuides by mutableStateOf(setOf<String>()); private set

    /** Vehicle SDK check...Capabilities 결과 (메인 스레드) */
    fun onGuideSupport(key: String, supported: Boolean) {
        unsupportedGuides = if (supported) unsupportedGuides - key else unsupportedGuides + key
    }

    // 주행
    private val _progress = mutableFloatStateOf(0f)
    /** 경로 진행 거리(m). 쓰기는 ViewModel 내부에서만, 화면은 읽기만 해요. */
    val progress: Float get() = _progress.floatValue
    var questStarted by mutableStateOf(false); private set
    var demoReport by mutableStateOf(false); private set
    val demoResultsAvailable: Boolean get() = BuildConfig.DEBUG && emulatorDemoAvailable &&
        questStarted && screen == Screen.Drive
    /** Pleos Seat API의 실제 운전석 버클 체결값. 미수신 기본값은 체결로 표시하지 않아요. */
    val seatBeltStatus: String get() = when {
        VehicleSignal.BELT !in vehicle.knownSignals -> "운전석 벨트 · 수신 대기"
        vehicle.belt -> "운전석 벨트 · 체결"
        else -> "운전석 벨트 · 해제"
    }
    var stopped by mutableStateOf(false); private set
    /** 주행 중 비 오버레이: 와이퍼 작동으로 추정 */
    val raining: Boolean get() = screen == Screen.Drive && vehicle.wiping
    var voice by mutableStateOf<VoiceUi?>(null); private set
    var quests by mutableStateOf(listOf<Quest>()); private set
    var arrivedSec by mutableFloatStateOf(0f); private set
    private var driveJob: Job? = null
    private var feedbackJob: Job? = null
    private var speechEngine: SpeechEngine? = null
    private var pendingAnswer: CompletableDeferred<Boolean>? = null
    private var manualVoiceAnswer = false
    private var voiceGeneration = 0L
    var voiceTestRunning by mutableStateOf(false); private set
    var voiceTestStatus by mutableStateOf("정차 중 안내를 듣고 ‘응’ 또는 ‘아니’로 답해 보세요."); private set

    fun attachSpeech(engine: SpeechEngine) { speechEngine = engine }

    fun detachSpeech(engine: SpeechEngine) {
        if (speechEngine !== engine) return
        cancelVoice()
        speechEngine = null
    }

    fun cancelVoice() {
        voiceGeneration++
        feedbackJob?.cancel()
        pendingAnswer?.cancel()
        pendingAnswer = null
        speechEngine?.stop()
        voice = null
        if (voiceTestRunning) voiceTestStatus = "음성 테스트를 중단했어요."
        voiceTestRunning = false
    }
    private var session = LiveDriveSession()
    private var evaluator = LiveQuestEvaluator()

    // 리포트
    var reportDetail by mutableStateOf<String?>(null); private set
    var ceremony by mutableStateOf<String?>(null); private set
    var licenseOpen by mutableStateOf(false); private set
    var toast by mutableStateOf<String?>(null); private set
    private var toastJob: Job? = null

    // ───────── 화면 이동 ─────────
    fun openSettings() {
        cancelVoice()
        if (screen != Screen.Settings) settingsFrom = screen
        screen = Screen.Settings
    }
    fun openPre() { screen = Screen.Pre }
    fun back() {
        if (screen == Screen.Settings) cancelVoice()
        when {
            licenseOpen -> licenseOpen = false
            screen == Screen.Settings -> screen = settingsFrom
            screen == Screen.Guide -> { screen = guideFrom; selectedGuide = null }
            screen == Screen.Pre -> screen = Screen.Home
            screen == Screen.Report && reportDetail != null -> reportDetail = null
        }
    }
    fun canGoBack() = licenseOpen || screen == Screen.Settings || screen == Screen.Guide || screen == Screen.Pre ||
        (screen == Screen.Report && reportDetail != null)

    // AAOS CarUxRestrictions 상태. 쓸 수 있으면 이걸 따르고(Pleos 필수), 없으면 기어(P)로 폴백해요.
    var uxAvailable by mutableStateOf(false); private set
    var uxRequiresDO by mutableStateOf(false); private set

    /** AAOS CarUxRestrictions 콜백 (PleosVehicleSource와 별개 · 메인 스레드) */
    fun onUxRestrictions(available: Boolean, requiresDO: Boolean) {
        uxAvailable = available
        uxRequiresDO = requiresDO
        if (screen == Screen.Settings && driving) cancelVoice()
    }

    /**
     * 읽을거리·조작이 많은 화면을 잠글지. distractionOptimized 앱은 시스템 CarUxRestrictions를 따라야 해요.
     * CarUxRestrictions를 받을 수 있으면 isRequiresDistractionOptimization()을, 아니면 기어 P 여부로 판단해요.
     */
    val driving: Boolean get() =
        if (uxAvailable) uxRequiresDO
        else vehicleLink == VehicleLink.Connected && !vehicle.parked

    fun openLicense() { licenseOpen = true }
    fun closeLicense() { licenseOpen = false }

    fun switchProfile(p: String) {
        if (p == profile) return
        users[profile] = user
        profile = p
        user = users[p] ?: seedUser(p)
        practice = emptyMap()
        lastDrive = null
        showToast(if (p == "L0") "첫 주행 · 처음 가는 길로 바꿨어요" else "10번째 주행 · 자주 다닌 길로 바꿨어요")
    }

    fun setConsent(key: String, v: String) {
        val label = mapOf("feedback" to "실시간 피드백", "cond" to "상황 가이드", "chime" to "접근 알림음").getValue(key)
        consents = when (key) {
            "feedback" -> consents.copy(feedback = v)
            "cond" -> consents.copy(cond = v)
            else -> consents.copy(chime = v)
        }
        val vt = when (v) { "on" -> if (key == "cond") "포함" else "켜기"; "off" -> if (key == "cond") "제외" else "끄기"; else -> "Hy! mate가 물어보기" }
        consentLog = consentLog + ConsentRecord(label, vt, "터치 · 동승 설정")
    }

    // ───────── 가이드 · 주차 중 연습 ─────────
    fun openGuide(k: String) {
        if (screen != Screen.Guide) guideFrom = screen
        selectedGuide = k
        screen = Screen.Guide
    }

    fun steps(k: String): List<PStep> = if (k == TODAY_KEY) todayQuest(rainy).practice else GD[k]?.practice.orEmpty()
    fun practiceState(k: String) = practice[k] ?: PracticeState()

    fun onVehicleLink(link: VehicleLink) {
        vehicleLink = link
        if (link != VehicleLink.Connected) vehicle = vehicle.copy(knownSignals = emptySet(), lowBeamOn = null)
        silenceWhenMoving()
    }

    fun onSpeedLink(link: VehicleLink) {
        speedLink = link
        if (link != VehicleLink.Connected) {
            vehicle = vehicle.copy(speedMps = null, speedUpdatedAtNanos = 0L)
            motionAvailable = false
            remainingSeconds = null
            silenceWhenMoving()
        }
    }

    fun onLocationLink(link: VehicleLink) {
        locationLink = link
        if (link != VehicleLink.Connected) {
            gpsPosition = null
            navigationFixFresh = false
        }
    }

    /** Pleos GPS를 탐색된 실제 도로에 투영해 진행도·경로 이탈을 판정해요. */
    fun onLocationFix(lat: Double, lng: Double) {
        val point = GeoPoint(lat, lng)
        if (!point.valid) return
        gpsPosition = point
        gpsReceivedAt = SystemClock.elapsedRealtime()
        navigationFixFresh = true
        if (screen != Screen.Drive || driveUsesDemo) return
        tracker.observe(point, gpsReceivedAt)
        publishNavigation()
        requestNavigationIfNeeded()
    }

    private fun publishNavigation() {
        navigationProgress = tracker.progress
        navigationPosition = tracker.position
        navigationOffRoute = tracker.offRoute
    }

    /** 중복 요청 방지·15초 재탐색 제한·실패 시 30초 백오프. 새 주행의 늦은 응답은 버려요. */
    private fun requestNavigationIfNeeded() {
        if (!navigationVisible || screen != Screen.Drive || (!driveUsesDemo && !navigationFixFresh)) return
        val now = SystemClock.elapsedRealtime()
        if (now < routeRetryAt || !tracker.shouldRequest(now, routeJob?.isActive == true)) return
        val origin = if (driveUsesDemo) NAV_ORIGIN else gpsPosition ?: return
        tracker.markRequested(now)
        routeLoading = true
        routeError = null
        remainingSeconds = null
        arrivalPredictionMillis = null
        lastEtaUpdate = 0L
        val generation = driveGeneration
        routeJob = viewModelScope.launch {
            try {
                val value = router.route(origin, NAV_DESTINATION)
                coroutineContext.ensureActive()
                if (generation != driveGeneration || screen != Screen.Drive) return@launch
                tracker.install(value)
                roadRoute = value
                questRouteVersion++
                if (!driveUsesDemo) gpsPosition?.let { tracker.observe(it, SystemClock.elapsedRealtime()) }
                publishNavigation()
                if (questStarted) quests = LiveQuestPlan.bindRoute(quests, value, questRouteVersion, tracker.progress)
                routeError = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == driveGeneration) {
                    routeError = error.message ?: "경로 탐색에 실패했어요. 자동으로 다시 시도해요."
                    routeRetryAt = SystemClock.elapsedRealtime() + 30_000L
                }
            } finally {
                if (generation == driveGeneration) routeLoading = false
            }
        }
    }

    fun onVehicleSpeed(speedMps: Float?, sampledAtNanos: Long) {
        vehicle = vehicle.copy(speedMps = speedMps, speedUpdatedAtNanos = sampledAtNanos)
        motionAvailable = vehicle.hasFreshSpeed(SystemClock.elapsedRealtimeNanos())
        silenceWhenMoving()
    }

    private fun silenceWhenMoving() {
        val holding = (VehicleSignal.GEAR in vehicle.knownSignals && vehicle.parked) ||
            (VehicleSignal.BRAKE in vehicle.knownSignals && vehicle.brake)
        if (!vehicle.hasFreshSpeed(SystemClock.elapsedRealtimeNanos()) ||
            abs(vehicle.speedMps ?: 0f) > 0.5f / 3.6f || !holding
        ) {
            stopped = false
            val moving = vehicle.hasFreshSpeed(SystemClock.elapsedRealtimeNanos()) &&
                abs(vehicle.speedMps ?: 0f) > 0.5f / 3.6f
            if ((screen != Screen.Settings || driving || moving) &&
                (voice != null || feedbackJob?.isActive == true)
            ) cancelVoice()
        }
    }

    /** Vehicle SDK에서 실제 레버·다이얼·벨트 값이 들어오면 연습 단계를 바로 확인해요. (메인 스레드) */
    fun onVehicleSignal(change: (Vehicle) -> Vehicle) {
        val next = change(vehicle)
        if (next == vehicle) return
        vehicle = next
        silenceWhenMoving()
        if (!driving) checkPractice() // 실습은 주차 중에만
    }

    /** 차량이 연결되지 않았을 때(개발 기기)는 버튼으로 조작을 흉내 내요. */
    fun simulateStep() {
        if (vehicleLink == VehicleLink.Connected || speedLink == VehicleLink.Connected) return
        val k = selectedGuide ?: return
        val st = practiceState(k)
        val cur = steps(k).getOrNull(st.step) ?: return
        vehicle = cur.simulate(vehicle)
        checkPractice()
    }

    fun confirmCheck() {
        val k = selectedGuide ?: return
        practice = practice + (k to PracticeState(done = true))
    }

    private fun checkPractice() {
        val k = selectedGuide ?: return
        val list = steps(k)
        var st = practiceState(k)
        if (st.done) return
        while (st.step < list.size && list[st.step].ok(vehicle)) st = st.copy(step = st.step + 1)
        if (st.step >= list.size) st = st.copy(done = true)
        practice = practice + (k to st)
    }

    // ───────── 주행 ─────────
    fun startDrive(withQuest: Boolean) {
        if (screen == Screen.Drive) return
        cancelVoice()
        questStarted = withQuest
        demoReport = false
        quests = if (withQuest) LiveQuestPlan.create(user, unsupportedGuides, consents.cond) else emptyList()
        questRouteVersion = 0
        driveUsesDemo = demoRoute
        driveGeneration++
        routeJob?.cancel()
        routeJob = null
        tracker = RouteTracker()
        roadRoute = null
        navigationProgress = 0f
        navigationPosition = null
        navigationOffRoute = false
        routeLoading = false
        routeError = null
        routeRetryAt = 0L
        arrivalPredictionMillis = null
        session = LiveDriveSession(allowLocationSnap = false)
        evaluator = LiveQuestEvaluator()
        _progress.floatValue = 0f
        stopped = false; voice = null
        remainingSeconds = null
        screen = Screen.Drive
        driveJob?.cancel()
        feedbackJob?.cancel()
        driveJob = viewModelScope.launch { runDrive() }
    }

    /** 실제 정차 중 사용자가 기록을 끝낼 수 있어요. 목적지까지 이동한 것으로 만들지 않아요. */
    fun finishDrive() {
        if (screen != Screen.Drive || driving) return
        driveJob?.cancel()
        arrive()
    }

    /** 디버그 에뮬레이터의 발표용 결과. 실제 센서 값과 주행 거리는 바꾸지 않아요. */
    fun finishDemoDrive() {
        if (!demoResultsAvailable || driving) return
        driveJob?.cancel()
        cancelVoice()
        quests = com.example.hymate.data.buildDemoQuestResults(quests, user.drives + 1)
        demoReport = true
        arrive()
    }

    fun answer(yes: Boolean) {
        if ((voice as? VoiceUi.Question)?.listening != true) return
        manualVoiceAnswer = true
        pendingAnswer?.complete(yes)
    }

    fun testVoice() {
        val moving = vehicle.hasFreshSpeed(SystemClock.elapsedRealtimeNanos()) &&
            abs(vehicle.speedMps ?: 0f) > 0.5f / 3.6f
        if (screen != Screen.Settings || driving || moving || voiceTestRunning) return
        cancelVoice()
        val generation = voiceGeneration
        voiceTestRunning = true
        feedbackJob = viewModelScope.launch {
            try {
                val answer = askVoice("음성 안내 테스트예요. 안내가 잘 들리나요? 응 또는 아니라고 말씀해 주세요.")
                coroutineContext.ensureActive()
                val text = when (answer) {
                    VoiceAnswer.YES -> if (manualVoiceAnswer) "화면 응답을 확인했어요. 음성 응답은 다시 말로 테스트해 주세요."
                        else "네, 음성 안내와 응답 인식이 연결됐어요."
                    VoiceAnswer.NO -> "아니라고 답하셨어요. 음량과 마이크를 확인해 주세요."
                    VoiceAnswer.NO_RESPONSE -> "명확한 응답을 듣지 못했어요. 다시 테스트할 수 있어요."
                    VoiceAnswer.UNAVAILABLE -> "음성 서비스를 연결하지 못했어요. Pleos 실행 환경과 음성 권한을 확인해 주세요."
                }
                voiceTestStatus = text
                if (answer != VoiceAnswer.UNAVAILABLE) say(VoiceUi.Confirm(text))
            } finally {
                if (generation == voiceGeneration) {
                    voice = null
                    voiceTestRunning = false
                }
            }
        }
    }

    private suspend fun say(message: VoiceUi): Boolean {
        val generation = voiceGeneration
        voice = message
        val text = when (message) {
            is VoiceUi.Feedback -> message.text
            is VoiceUi.Question -> message.text
            is VoiceUi.Confirm -> message.text
        }
        val played = speechEngine?.speak(text) == true
        coroutineContext.ensureActive()
        if (!played) {
            showToast("음성 안내를 재생하지 못했어요. 화면의 안내를 확인해 주세요.")
            delay(2500) // 연결 실패 시에만 화면으로 읽을 시간을 제공해요.
        }
        if (generation == voiceGeneration) voice = null
        return played
    }

    private suspend fun askVoice(text: String): VoiceAnswer {
        val engine = speechEngine ?: return VoiceAnswer.UNAVAILABLE
        val generation = voiceGeneration
        voice = VoiceUi.Question(text, listening = false, remain = 1f)
        voiceTestStatus = "질문을 읽고 있어요."
        val manual = CompletableDeferred<Boolean>()
        pendingAnswer = manual
        manualVoiceAnswer = false
        var countdown: Job? = null
        try {
            return SpeechConversation(engine).ask(text, manual) listening@{
                if (generation != voiceGeneration) return@listening
                voiceTestStatus = "듣는 중 · ‘응’ 또는 ‘아니’라고 답해 주세요."
                voice = VoiceUi.Question(text, listening = true, remain = 1f)
                countdown = viewModelScope.launch {
                    val start = SystemClock.elapsedRealtime()
                    while (generation == voiceGeneration) {
                        val remain = (1f - (SystemClock.elapsedRealtime() - start) / 6000f).coerceIn(0f, 1f)
                        voice = VoiceUi.Question(text, listening = true, remain = remain)
                        if (remain == 0f) break
                        delay(50)
                    }
                }
            }
        } finally {
            countdown?.cancel()
            if (pendingAnswer === manual) pendingAnswer = null
            manual.cancel()
        }
    }

    private suspend fun runDrive() {
        val fed = mutableSetOf<String>()
        var stopHandled = false
        var conditionAsked = false
        while (true) {
            delay(100)
            val previousDistance = session.distanceMeters
            session.tick(vehicle, SystemClock.elapsedRealtimeNanos())
            motionAvailable = session.speedAvailable
            _progress.floatValue = session.distanceMeters
            val now = SystemClock.elapsedRealtime()
            navigationFixFresh = gpsPosition != null && locationLink == VehicleLink.Connected && now - gpsReceivedAt <= 10_000L
            if (driveUsesDemo) tracker.advanceDemo(session.distanceMeters - previousDistance)
            publishNavigation()
            requestNavigationIfNeeded()
            remainingSeconds = if (navigationReady) roadRoute?.remainingDuration(navigationProgress) else null
            if (now - lastEtaUpdate >= 1000L) {
                arrivalPredictionMillis = remainingSeconds?.let { System.currentTimeMillis() + (it * 1000).toLong() }
                lastEtaUpdate = now
            }
            if (tracker.arrived && navigationReady) break
            stopped = session.stopped
            if (!stopped) {
                stopHandled = false
                // 재출발 시 즉시 음성 중단. 매 틱이 아니라 재생 중일 때만 호출해요.
                if (screen == Screen.Drive && (voice != null || feedbackJob?.isActive == true)) cancelVoice()
            }
            if (questStarted) quests = evaluator.update(
                quests, vehicle, session,
                roadRoute?.let { QuestRouteState(questRouteVersion, navigationProgress, navigationReady) },
            )
            if (stopped && screen == Screen.Drive && questStarted && speechEngine != null &&
                !stopHandled && feedbackJob?.isActive != true
            ) {
                if (!conditionAsked && rainy && consents.cond == "ask" && quests.any { it.consent }) {
                    conditionAsked = true
                    stopHandled = true
                    feedbackJob = viewModelScope.launch {
                        try { askCondition() } finally {
                            if (quests.any { it.consent }) conditionAsked = false
                        }
                    }
                    continue
                }
                if (consents.feedback != "on") continue
                val q = quests.filter {
                    it.res == 'F' && it.fb && !it.consent && it.g !in fed && guide(it.g).type == GType.AUTO
                }.maxByOrNull { guide(it.g).risk }
                if (q != null) {
                    fed += q.g
                    stopHandled = true
                    feedbackJob = viewModelScope.launch { say(VoiceUi.Feedback(fbText(q))) }
                }
            }
        }
        arrive()
    }

    /** @return 동의해서 오늘 퀘스트에 포함된 상황 가이드 퀘스트 id */
    private suspend fun askCondition(): Set<String> {
        val reply = askVoice("와이퍼가 작동 중이에요. 비 오는 날 가이드도 오늘 퀘스트에 넣을까요?")
        coroutineContext.ensureActive()
        if (!stopped || screen != Screen.Drive || reply == VoiceAnswer.UNAVAILABLE) {
            voice = null
            if (reply == VoiceAnswer.UNAVAILABLE) showToast("음성 연결을 확인하지 못해 상황 가이드 동의를 보류했어요.")
            return emptySet()
        }
        val accepted = if (reply == VoiceAnswer.YES) quests.filter { it.consent }.map { it.id }.toSet() else emptySet()
        val channel = if (manualVoiceAnswer) "터치 · 정차 중 Hy! mate" else "음성 · 정차 중 Hy! mate"
        if (reply == VoiceAnswer.YES) {
            quests = quests.map { if (it.consent) it.copy(consent = false) else it }
            consentLog = consentLog + ConsentRecord("상황 가이드 (우천)", "포함", channel)
            say(VoiceUi.Confirm("네, 오늘 넣을게요."))
        } else {
            quests = quests.map { if (it.consent) it.copy(res = 'N', ev = "상황 가이드에 동의하지 않아 제외했어요", consent = false, completed = true, notApplicable = true) else it }
            consentLog = consentLog + ConsentRecord("상황 가이드 (우천)", "제외", if (reply == VoiceAnswer.NO_RESPONSE) "무응답 · 정차 중 Hy! mate" else channel)
            say(VoiceUi.Confirm(if (reply == VoiceAnswer.NO_RESPONSE) "명확한 대답이 없어서 이번엔 뺄게요." else "이번엔 뺄게요."))
        }
        return accepted
    }

    private fun arrive() {
        driveGeneration++
        routeJob?.cancel()
        routeLoading = false
        cancelVoice()
        if (!demoReport) quests = evaluator.finish(quests)
        feedbackJob?.cancel()
        stopped = false; voice = null
        arrivedSec = session.elapsedSeconds
        arrivedAtMillis = System.currentTimeMillis()
        reportDetail = null
        screen = Screen.Report
    }

    // ───────── 리포트 ─────────
    fun openDetail(k: String) { reportDetail = k }
    fun closeDetail() { reportDetail = null }
    fun toggleDispute(id: String) { quests = quests.map { if (it.id == id) it.copy(disputed = !it.disputed) else it } }

    /** 보류 퀘스트에 운전자가 직접 답해요 (자가 체크 · 경험 자기보고) */
    fun answerHold(id: String, yes: Boolean) {
        if (screen != Screen.Report || driving) return
        quests = quests.map { if (it.id == id) com.example.hymate.data.resolveHold(it, yes) else it }
    }

    fun skipHold(id: String) {
        if (screen != Screen.Report || driving) return
        quests = quests.map { if (it.id == id && it.res == 'H') it.copy(
            res = 'N', ev = "자가 확인 · 이번 주행에는 해당 없다고 응답", completed = true,
            selfCheck = true, notApplicable = true,
        ) else it }
    }

    /** 출발 전 학습 완료 보상: 주차 실습·내용 확인은 +5, Hyundai ONLY 경험은 +3 */
    fun practiceReward(k: String): Int =
        if (k != TODAY_KEY && guide(k).type == GType.EXPERIENCE) XP.HS else XP.PRE

    fun practiceXp(): Int = practice.entries.filter { it.value.done }.sumOf { practiceReward(it.key) }

    fun computeGrowth(): Growth = com.example.hymate.data.computeGrowth(user, quests, practiceXp())

    fun commit() {
        // 화면 전환 애니메이션 중 다시 눌려도 한 번만 반영해요
        if (screen != Screen.Report || ceremony != null) return
        if (!questStarted) { nextDrive(); return }
        val g = computeGrowth()
        val judged = quests.filter { guide(it.g).type == GType.AUTO }
        lastDrive = DriveLog(
            ok = judged.count { it.res == 'S' },
            miss = judged.count { it.res == 'F' && !it.disputed },
            gain = g.gain,
            grads = g.grads.size,
            demo = demoReport,
            total = quests.size,
            pending = quests.count { it.res == 'H' },
            selfChecked = quests.count { it.selfAnswer != null || (it.selfCheck && it.notApplicable) },
        )
        val before = tierOf(user.xp)?.first
        user = user.copy(st = g.st, xp = user.xp + g.gain, drives = user.drives + 1, routeMemory = true)
        val after = tierOf(user.xp)?.first
        if (after != null && after != before) ceremony = after
        else { showToast("기록을 저장했어요. 홈으로 돌아가요."); nextDrive() }
    }

    fun nextDrive() {
        driveGeneration++
        routeJob?.cancel()
        routeLoading = false
        roadRoute = null
        navigationPosition = null
        arrivalPredictionMillis = null
        cancelVoice()
        driveJob?.cancel()
        feedbackJob?.cancel()
        stopped = false
        voice = null
        remainingSeconds = null
        ceremony = null
        demoReport = false
        quests = emptyList()
        practice = emptyMap()
        selectedGuide = null
        reportDetail = null
        _progress.floatValue = 0f
        screen = Screen.Home
    }

    fun showToast(msg: String) {
        toast = msg
        toastJob?.cancel()
        toastJob = viewModelScope.launch { delay(2600); toast = null }
    }

    fun preGuides(): List<PreGuide> {
        val order = mapOf("fail" to 0, "ret" to 1, "cond" to 2, "new" to 3, "lv" to 4, "off" to 5, "grad" to 6, "exp" to 7)
        // 졸업 가이드는 기획서대로 출발 전 안내에서 제외하되, UI에서 접힌 섹션으로 보여줘요(판정은 계속).
        return GUIDES.values.filterNot { it.key in unsupportedGuides }.map { g ->
            val s = user.st.getValue(g.key)
            val tag = when {
                g.type == GType.EXPERIENCE -> "exp"
                s.status == "graduated" -> "grad"
                g.cond == "rain" && !rainy -> "off"
                s.lastFail -> "fail"
                s.status == "returned" -> "ret"
                g.cond != null -> "cond"
                !s.seen -> "new"
                else -> "lv"
            }
            PreGuide(g.key, tag, order.getValue(tag))
        }.sortedWith(compareBy<PreGuide> { guide(it.key).lv }.thenBy { it.ord })
    }
}

data class PreGuide(val key: String, val tag: String, val ord: Int)
