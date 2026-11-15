package com.example.hymate

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hymate.data.DEST
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
import com.example.hymate.vehicle.hasFreshSpeed
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen { Home, Settings, Pre, Guide, Drive, Report }

/** 홈 화면에 보여줄 직전 주행 요약 */
data class DriveLog(val ok: Int, val miss: Int, val gain: Int, val grads: Int)

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
 * 지도는 예시 경로이며, 이동은 AAOS 차속으로만 진행해요.
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
    var stopped by mutableStateOf(false); private set
    /** 주행 중 비 오버레이: 와이퍼 작동으로 추정 */
    val raining: Boolean get() = screen == Screen.Drive && vehicle.wiping
    var voice by mutableStateOf<VoiceUi?>(null); private set
    var quests by mutableStateOf(listOf<Quest>()); private set
    var arrivedSec by mutableFloatStateOf(0f); private set
    private var driveJob: Job? = null
    private var feedbackJob: Job? = null
    private var pendingAnswer: Boolean? = null // askCondition(음성 질문) 재도입용 스텁
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
        if (screen != Screen.Settings) settingsFrom = screen
        screen = Screen.Settings
    }
    fun openPre() { screen = Screen.Pre }
    fun back() {
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
        if (link != VehicleLink.Connected) vehicle = vehicle.copy(knownSignals = emptySet())
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
            voice = null
            feedbackJob?.cancel()
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
        questStarted = withQuest
        // 기존 시나리오에서는 지점 목록만 재사용하고, 미리 정해진 결과는 버려요.
        quests = if (withQuest) buildQuests(profile, user, vehicle, consents.cond)
            .filterNot { it.g in unsupportedGuides } // 차종 미지원 가이드는 퀘스트도 만들지 않아요
            .map { it.copy(res = 'N', ev = "아직 판정 지점에 도달하지 않았어요") }
        else emptyList()
        session = LiveDriveSession()
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

    fun answer(yes: Boolean) {
        // 강우·음성 API 미연동 상태에서는 자동 질문을 만들지 않아요.
    }

    private suspend fun runDrive() {
        val fed = mutableSetOf<String>()
        while (_progress.floatValue < DEST) {
            delay(100)
            session.tick(vehicle, SystemClock.elapsedRealtimeNanos())
            motionAvailable = session.speedAvailable
            _progress.floatValue = session.distanceMeters.coerceAtMost(DEST.toFloat())
            remainingSeconds = session.remainingSeconds(DEST.toFloat(), vehicle)
            stopped = session.stopped
            if (!stopped) {
                voice = null
                feedbackJob?.cancel()
            }
            if (questStarted) quests = evaluator.update(quests, vehicle, session)
            if (stopped && questStarted && consents.feedback == "on" && feedbackJob?.isActive != true) {
                val q = quests.filter {
                    it.res == 'F' && it.fb && !it.consent && it.g !in fed && guide(it.g).type == GType.AUTO
                }.maxByOrNull { guide(it.g).risk }
                if (q != null) {
                    fed += q.g
                    feedbackJob = viewModelScope.launch {
                        voice = VoiceUi.Feedback(fbText(q))
                        delay(4200)
                        voice = null
                    }
                }
            }
        }
        arrive()
    }

    /** @return 동의해서 오늘 퀘스트에 포함된 상황 가이드 퀘스트 id */
    private suspend fun askCondition(): Set<String> {
        val q = "비 오는 날 가이드도 넣을까요?"
        voice = VoiceUi.Question(q, listening = false, remain = 1f)
        delay(1400)
        pendingAnswer = null
        val total = 6000L
        var el = 0L
        while (el < total && pendingAnswer == null) {
            voice = VoiceUi.Question(q, listening = true, remain = 1f - el / total.toFloat())
            delay(50); el += 50
        }
        val ans = pendingAnswer
        val accepted = if (ans == true) quests.filter { it.consent }.map { it.id }.toSet() else emptySet()
        if (ans == true) {
            quests = quests.map { if (it.consent) it.copy(consent = false) else it }
            consentLog = consentLog + ConsentRecord("상황 가이드 (우천)", "포함", "음성 · 정차 중 Hy! mate")
            voice = VoiceUi.Confirm("네, 오늘 넣을게요.")
        } else {
            quests = quests.filterNot { it.consent } // 동의 전 판정값은 차 안에서 지워요
            consentLog = consentLog + ConsentRecord("상황 가이드 (우천)", "제외", if (ans == null) "무응답 · 정차 중 Hy! mate" else "음성 · 정차 중 Hy! mate")
            voice = VoiceUi.Confirm(if (ans == null) "대답이 없어서 이번엔 뺄게요." else "이번엔 뺄게요.")
        }
        delay(2000)
        return accepted
    }

    private fun arrive() {
        quests = evaluator.finish(quests).filterNot { it.consent }
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
        quests = quests.map { if (it.id == id) com.example.hymate.data.resolveHold(it, yes) else it }
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
        )
        val before = tierOf(user.xp)?.first
        user = user.copy(st = g.st, xp = user.xp + g.gain, drives = user.drives + 1, routeMemory = true)
        val after = tierOf(user.xp)?.first
        if (after != null && after != before) ceremony = after
        else { showToast("기록을 저장했어요. 홈으로 돌아가요."); nextDrive() }
    }

    fun nextDrive() {
        driveJob?.cancel()
        feedbackJob?.cancel()
        stopped = false
        voice = null
        remainingSeconds = null
        ceremony = null
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
