package com.example.hymate

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
import com.example.hymate.data.RAIN_START_D
import com.example.hymate.data.STOPS
import com.example.hymate.data.TODAY_KEY
import com.example.hymate.data.UserProfile
import com.example.hymate.data.Vehicle
import com.example.hymate.data.XP
import com.example.hymate.data.buildQuests
import com.example.hymate.data.fbText
import com.example.hymate.data.guide
import com.example.hymate.data.seedUser
import com.example.hymate.data.tierOf
import com.example.hymate.data.todayQuest
import com.example.hymate.vehicle.VehicleLink
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen { Home, Settings, Pre, Guide, Drive, Report }

/** 홈 화면에 보여줄 직전 주행 요약 */
data class DriveLog(val ok: Int, val miss: Int, val gain: Int, val grads: Int)

/** 동의 값: on / off / ask(Gleo가 물어보기) */
data class Consents(val feedback: String = "on", val cond: String = "ask", val chime: String = "on")

data class ConsentRecord(val item: String, val value: String, val channel: String)

data class PracticeState(val step: Int = 0, val done: Boolean = false)

sealed interface VoiceUi {
    data class Feedback(val text: String) : VoiceUi
    data class Question(val text: String, val listening: Boolean, val remain: Float) : VoiceUi
    data class Confirm(val text: String) : VoiceUi
}

/**
 * 화면 상태 + 주행 시뮬레이션.
 * 실제 차량에서는 시뮬레이션 루프 대신 CarPropertyManager(PERF_VEHICLE_SPEED), Vehicle SDK 리스너,
 * NaviHelper 이벤트가 같은 상태를 갱신하면 돼요.
 */
class HyViewModel : ViewModel() {
    var screen by mutableStateOf(Screen.Home); private set
    private var settingsFrom = Screen.Home
    private var guideFrom = Screen.Pre
    var lastDrive by mutableStateOf<DriveLog?>(null); private set
    var profile by mutableStateOf("L0"); private set
    private val users = mutableMapOf<String, UserProfile>()
    var user by mutableStateOf(seedUser("L0")); private set

    val rainy = true // 오늘 날씨: 주행 중 비 예보 (08:30부터)
    var consents by mutableStateOf(Consents()); private set
    var consentLog by mutableStateOf(
        listOf(
            ConsentRecord("서비스 성격 고지", "확인함", "터치 · 최초 실행"),
            ConsentRecord("실시간 피드백", "켜기", "터치 · 동승 설정"),
            ConsentRecord("상황 가이드", "Gleo가 물어보기", "터치 · 동승 설정"),
            ConsentRecord("접근 알림음", "켜기", "터치 · 동승 설정"),
        ),
    ); private set

    var vehicle by mutableStateOf(Vehicle()); private set
    /** 차량 데이터(Vehicle SDK) 연결 상태. 연결되지 않으면 버튼 시뮬레이션으로 연습해요. */
    var vehicleLink by mutableStateOf<VehicleLink>(VehicleLink.Simulated); private set
    var practice by mutableStateOf(mapOf<String, PracticeState>()); private set
    var selectedGuide by mutableStateOf<String?>(null); private set

    // 주행
    private val _progress = mutableFloatStateOf(0f)
    /** 경로 진행 거리(m). 쓰기는 ViewModel 내부에서만, 화면은 읽기만 해요. */
    val progress: Float get() = _progress.floatValue
    var questStarted by mutableStateOf(false); private set
    var stopped by mutableStateOf(false); private set
    var raining by mutableStateOf(false); private set
    var voice by mutableStateOf<VoiceUi?>(null); private set
    var quests by mutableStateOf(listOf<Quest>()); private set
    var arrivedSec by mutableFloatStateOf(0f); private set
    private var driveJob: Job? = null
    private var pendingAnswer: Boolean? = null

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

    /**
     * 실제 차량이 주행 중인지 (Vehicle SDK 기어가 P가 아님). 차량이 연결되지 않았거나 기어를 모르면 false.
     * 앱은 주행 중에도 떠 있지만(distractionOptimized), 읽을거리·조작이 많은 화면은 이 값으로 스스로 잠가요.
     */
    val driving: Boolean get() = vehicleLink == VehicleLink.Connected && !vehicle.parked

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
        val vt = when (v) { "on" -> if (key == "cond") "포함" else "켜기"; "off" -> if (key == "cond") "제외" else "끄기"; else -> "Gleo가 물어보기" }
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

    fun onVehicleLink(link: VehicleLink) { vehicleLink = link }

    /** Vehicle SDK에서 실제 레버·다이얼·벨트 값이 들어오면 연습 단계를 바로 확인해요. (메인 스레드) */
    fun onVehicleSignal(change: (Vehicle) -> Vehicle) {
        val next = change(vehicle)
        if (next == vehicle) return
        vehicle = next
        if (!driving) checkPractice() // 실습은 주차 중에만
    }

    /** 차량이 연결되지 않았을 때(개발 기기)는 버튼으로 조작을 흉내 내요. */
    fun simulateStep() {
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
        quests = if (withQuest) buildQuests(profile, user, vehicle, consents.cond) else emptyList()
        _progress.floatValue = 0f
        stopped = false; raining = false; voice = null
        screen = Screen.Drive
        driveJob?.cancel()
        driveJob = viewModelScope.launch { runDrive() }
    }

    fun answer(yes: Boolean) { pendingAnswer = yes }

    /** 개발용: 남은 주행을 건너뛰고 도착 처리 */
    fun skipToArrive() {
        driveJob?.cancel()
        _progress.floatValue = DEST.toFloat()
        arrive()
    }

    private suspend fun runDrive() {
        val fed = mutableSetOf<String>()
        val carry = mutableSetOf<String>() // 정차 중 동의로 뒤늦게 포함된 퀘스트 (다음 정차 피드백 후보)
        var prevStopD = -1
        var pendingCond = false
        val stops = STOPS.toMutableList()
        val metersPerSecond = 340f // 약 1분 남짓에 21.4km를 재생하는 시간 압축
        var last = System.nanoTime()
        while (_progress.floatValue < DEST) {
            delay(16)
            // delay는 16ms보다 길어질 수 있어서 실제 경과 시간으로 전진해요 (기기와 무관하게 같은 속도)
            val now = System.nanoTime()
            val dt = ((now - last) / 1e9f).coerceIn(0f, 0.05f)
            last = now
            val d = _progress.floatValue
            var nd = d + metersPerSecond * dt
            val stop = stops.firstOrNull()
            if (!raining && rainy && nd >= RAIN_START_D) {
                raining = true
                pendingCond = questStarted && consents.cond == "ask" && quests.any { it.consent }
            }
            if (stop != null && nd >= stop.d) {
                nd = stop.d.toFloat()
                _progress.floatValue = nd
                stops.removeAt(0)
                // ── 정차 판정: 속도 ≤0.5km/h + 브레이크 3초 (압축해서 1.2초) ──
                stopped = true
                delay(1200)
                if (questStarted) {
                    val cand = quests.filter {
                        it.d <= nd && (it.d > prevStopD || it.id in carry) && it.res == 'F' && it.fb && !it.consent &&
                            it.g !in fed && guide(it.g).type == GType.AUTO
                    }.sortedWith(compareByDescending<Quest> { guide(it.g).risk }.thenByDescending { it.d })
                    if (consents.feedback == "on" && cand.isNotEmpty()) {
                        val q = cand.first()
                        fed += q.g
                        voice = VoiceUi.Feedback(fbText(q))
                        delay(4200)
                    }
                    if (pendingCond) {
                        pendingCond = false
                        carry += askCondition()
                    }
                }
                voice = null
                delay(stop.holdMs)
                stopped = false
                prevStopD = stop.d
                last = System.nanoTime()
                continue
            }
            _progress.floatValue = nd.coerceAtMost(DEST.toFloat())
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
            consentLog = consentLog + ConsentRecord("상황 가이드 (우천)", "포함", "음성 · 정차 중 Gleo")
            voice = VoiceUi.Confirm("네, 오늘 넣을게요.")
        } else {
            quests = quests.filterNot { it.consent } // 동의 전 판정값은 차 안에서 지워요
            consentLog = consentLog + ConsentRecord("상황 가이드 (우천)", "제외", if (ans == null) "무응답 · 정차 중 Gleo" else "음성 · 정차 중 Gleo")
            voice = VoiceUi.Confirm(if (ans == null) "대답이 없어서 이번엔 뺄게요." else "이번엔 뺄게요.")
        }
        delay(2000)
        return accepted
    }

    private fun arrive() {
        quests = quests.filterNot { it.consent }
        stopped = false; voice = null
        arrivedSec = DEST / (28f / 3.6f)
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
        ceremony = null
        quests = emptyList()
        practice = emptyMap()
        selectedGuide = null
        reportDetail = null
        _progress.floatValue = 0f
        raining = false
        // 실제 차량 값을 받는 중이면 그대로 두고, 시뮬레이션일 때만 초기화해요
        if (vehicleLink != VehicleLink.Connected) vehicle = Vehicle(belt = vehicle.belt)
        screen = Screen.Home
    }

    fun showToast(msg: String) {
        toast = msg
        toastJob?.cancel()
        toastJob = viewModelScope.launch { delay(2600); toast = null }
    }

    fun preGuides(): List<PreGuide> {
        val order = mapOf("fail" to 0, "ret" to 1, "cond" to 2, "new" to 3, "lv" to 4, "off" to 5, "grad" to 6, "exp" to 7)
        return GUIDES.values.map { g ->
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
