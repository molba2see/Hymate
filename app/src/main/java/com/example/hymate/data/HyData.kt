package com.example.hymate.data

import androidx.compose.ui.geometry.Offset

// ───────────────────────── 가이드 라이브러리 ─────────────────────────

/**
 * 파이프라인(프로토타입 구현 스펙 v2)의 판정 유형.
 * AUTO: 차량 데이터로 성공/놓침 판정 · RECORD: 평가 없이 기록 · EXPERIENCE: Hyundai ONLY 경험형(판정·졸업 없음).
 * self_check(운전자 응답)는 별도 유형이 아니라 보류('H') 결과를 리포트에서 직접 확정하는 방식으로 다뤄요.
 */
enum class GType { AUTO, RECORD, EXPERIENCE }

/** Hyundai ONLY 가이드의 내부 레벨 값 (정렬용). 화면에는 "HS"로 표시해요. */
const val LV_HS = 6

data class Guide(
    val key: String,
    val lv: Int,
    val type: GType,
    val title: String,
    val short: String,
    val src: String,
    val risk: Int,
    val cond: String? = null,
)

val GUIDES: Map<String, Guide> = listOf(
    Guide("SEATBELT", 1, GType.AUTO, "안전벨트를 매고 출발", "안전벨트", "도로교통법 제50조", 5),
    Guide("HEADLIGHT", 1, GType.AUTO, "출발 전에 전조등을 AUTO로", "오토 라이트", "도로교통법 제37조", 4),
    Guide("RAIN", 1, GType.AUTO, "비가 오면 와이퍼와 전조등 켜기", "우천 와이퍼·전조등", "도로교통법 제37조", 3, cond = "rain"),
    Guide("SCHOOL", 1, GType.AUTO, "어린이보호구역 30km/h 이하로 진입", "보호구역 감속", "도로교통법 제12조", 5),
    Guide("SMOOTH", 2, GType.AUTO, "급제동 없이 정지", "부드러운 정지", "관찰 사실 · 차량 데이터", 3),
    Guide("SMOOTH_START", 2, GType.AUTO, "급출발 없이 출발", "부드러운 출발", "관찰 사실 · 차량 데이터", 2),
    Guide("SIG_CANCEL", 2, GType.AUTO, "차선을 바꾼 뒤 방향지시등 끄기", "방향지시등 끄기", "도로교통법 제38조", 3),
    Guide("TUNNEL", 2, GType.AUTO, "터널에 들어갈 때 전조등 켜기", "터널 전조등", "도로교통법 제37조", 4),
    Guide("SPEED", 2, GType.AUTO, "구간 제한속도 지키기", "제한속도", "도로교통법 제17조", 5),
    Guide("TURN", 3, GType.AUTO, "좌·우회전 30m 전 방향지시등", "회전 방향지시등", "도로교통법 시행령 별표2", 3),
    Guide("TURN_SPEED", 3, GType.AUTO, "회전할 때 20km/h 이하로 서행", "회전 서행", "도로교통법 제25조", 4),
    Guide("RED_RIGHT_STOP", 3, GType.AUTO, "적색 신호 우회전 일시정지", "우회전 일시정지", "도로교통법 시행규칙 별표2", 5),
    Guide("UNPROT_SIG", 4, GType.AUTO, "비보호 좌회전도 30m 전 방향지시등", "비보호 방향지시등", "도로교통법 시행령 별표2", 3),
    Guide("UNPROT_TIME", 4, GType.RECORD, "비보호 좌회전 진행 시점", "비보호 진행 시점", "행동 기록 · 평가하지 않음", 0),
    Guide("RAIN_SLOW", 4, GType.AUTO, "비가 오면 제한속도보다 20% 줄이기", "우천 감속", "도로교통법 시행규칙 제19조", 4, cond = "rain"),
    Guide("MERGE", 5, GType.AUTO, "합류·분기·진출 30m 전 방향지시등", "합류·분기 방향지시등", "도로교통법 시행령 별표2", 4),
    Guide("WEAVE_EXIT", 5, GType.AUTO, "엇갈림 구간에서 미리 차로를 바꿔 진출", "엇갈림 진출", "도로교통법 제19조", 4),
    Guide("HS_NSCC", LV_HS, GType.EXPERIENCE, "NSCC 2로 구간 제한속도 지키기", "NSCC 2", "Hyundai ONLY · 경험", 0),
    Guide("HS_GLEO", LV_HS, GType.EXPERIENCE, "Gleo에게 말로 차량 조작하기", "Gleo 음성 조작", "Hyundai ONLY · 경험", 0),
    Guide("HS_RSPA", LV_HS, GType.EXPERIENCE, "RSPA로 본사 주차장에 주차하기", "RSPA 주차", "Hyundai ONLY · 경험", 0),
).associateBy { it.key }

fun guide(k: String): Guide = GUIDES.getValue(k)

val LV_NAME = mapOf(1 to "기본 습관", 2 to "주행 기본", 3 to "회전", 4 to "교차로·우천", 5 to "자동차전용도로", LV_HS to "Hyundai ONLY · 경험")

fun lvLabel(lv: Int) = if (lv == LV_HS) "HS" else "Lv$lv"
val Guide.lvLabel get() = lvLabel(lv)

/** 주행에서 이 가이드로 얻는 XP: 일반 퀘스트는 Lv만큼, Hyundai ONLY는 3 */
val Guide.pts get() = if (lv == LV_HS) XP.HS else lv

// ───────────────────────── 차량 신호 (Vehicle SDK 대역) ─────────────────────────

data class Vehicle(
    val signal: String = "OFF", // L / R / OFF
    val headlight: String = "OFF", // OFF / ON / AUTO
    val wiper: String = "OFF", // OFF / AUTO / LOW
    val belt: Boolean = true,
    val brake: Boolean = false, // 브레이크 페달 (정차 판정용 · 차속 연동 전이라 아직 판정에는 쓰지 않아요)
    val parked: Boolean = true, // 기어 P 여부 (주행 중 화면 잠금용)
)

/** 주차 중 연습 한 단계. 실제 차에서는 Vehicle SDK 리스너로 [ok]를 판정해요. */
class PStep(
    val title: String,
    val hint: String,
    val ok: (Vehicle) -> Boolean,
    val simulate: (Vehicle) -> Vehicle,
    val live: (Vehicle) -> String,
)

private fun sig(d: String, l: String) = PStep(
    "$l 방향지시등을 켜 보세요", if (d == "L") "레버를 아래로 내려요" else "레버를 위로 올려요",
    { it.signal == d }, { it.copy(signal = d) }, { "방향지시등 = ${it.signal}" },
)
private val SIG_OFF = PStep("방향지시등을 꺼 보세요", "레버를 가운데로 돌려놔요", { it.signal == "OFF" }, { it.copy(signal = "OFF") }, { "방향지시등 = ${it.signal}" })
private val HL_AUTO = PStep("전조등을 AUTO로 맞춰 보세요", "왼쪽 레버 끝 다이얼을 AUTO로", { it.headlight == "AUTO" }, { it.copy(headlight = "AUTO") }, { "전조등 = ${it.headlight}" })
private val WP_AUTO = PStep("와이퍼를 AUTO로 맞춰 보세요", "오른쪽 레버를 AUTO 칸으로", { it.wiper == "AUTO" }, { it.copy(wiper = "AUTO") }, { "와이퍼 = ${it.wiper}" })
private val BELT_OFF = PStep("안전벨트를 한 번 풀어 보세요", "버클 위치를 손으로 익혀요", { !it.belt }, { it.copy(belt = false) }, { "안전벨트 = ${if (it.belt) "체결" else "해제"}" })
private val GLEO_HL = PStep("Gleo에게 “전조등 AUTO로 해줘”라고 말해 보세요", "스티어링 휠의 음성 버튼을 누르고 말해요", { it.headlight == "AUTO" }, { it.copy(headlight = "AUTO") }, { "전조등 = ${it.headlight}" })
private val GLEO_WP = PStep("이번엔 “와이퍼 AUTO로 해줘”라고 말해 보세요", "손은 핸들에 둔 채로 말로만 해요", { it.wiper == "AUTO" }, { it.copy(wiper = "AUTO") }, { "와이퍼 = ${it.wiper}" })
private val BELT_ON = PStep("다시 매 보세요", "딸깍 소리와 경고등이 꺼지는지 확인", { it.belt }, { it.copy(belt = true) }, { "안전벨트 = ${if (it.belt) "체결" else "해제"}" })

data class GuideDetail(
    val why: String,
    val pain: String,
    val how: List<String>,
    val dia: String? = null,
    val practice: List<PStep> = emptyList(),
    val check: List<String> = emptyList(),
)

val GD: Map<String, GuideDetail> = mapOf(
    "SEATBELT" to GuideDetail(
        "운전석뿐 아니라 모든 좌석이 안전벨트 의무예요. 차가 출발하는 순간에 판정해요.",
        "처음엔 시동, 기어, 내비 설정을 챙기다 보면 벨트를 깜빡하기 쉬워요.",
        listOf("시동을 걸면 내비보다 벨트를 먼저 매요", "동승자가 있으면 뒷좌석도 확인해요", "계기판 벨트 경고등이 꺼졌는지 봐요"),
        practice = listOf(BELT_OFF, BELT_ON),
    ),
    "HEADLIGHT" to GuideDetail(
        "전조등을 AUTO로 두면 터널과 어두운 길에서 알아서 켜져요. 출발 10초 안에 AUTO나 ON이면 성공이에요.",
        "이 길에는 출발 4km 만에 정릉터널이 나와요. 터널 직전에 다이얼을 찾다 보면 시선이 흔들려요.",
        listOf("주차 중에 전조등 다이얼 위치를 익혀요", "AUTO로 맞춰 두고 출발해요", "계기판에 전조등 표시가 뜨는지 봐요"),
        practice = listOf(HL_AUTO),
    ),
    "RAIN" to GuideDetail(
        "비가 오면 와이퍼와 전조등을 함께 켜요. 비를 감지하고 10초 안에 둘 다 켜져 있으면 성공이에요.",
        "비가 갑자기 오면 와이퍼 레버를 반대로 움직이거나 전조등을 잊기 쉬워요.",
        listOf("와이퍼를 AUTO로 두면 빗방울을 감지해요", "전조등도 함께 켜요", "유리에 김이 서리면 에어컨을 켜요"),
        practice = listOf(WP_AUTO, HL_AUTO),
    ),
    "SMOOTH" to GuideDetail(
        "정지할 때 속도가 1초에 11km/h 넘게 줄면 급제동으로 기록해요. 평가 없이 사실만 알려줘요.",
        "정릉삼거리·청담사거리·도곡동처럼 신호가 자주 바뀌는 곳에서 늦게 보고 세게 밟기 쉬워요.",
        listOf("멀리 있는 신호를 미리 봐요", "속도를 나눠서 천천히 줄여요", "정지선 앞에서 완전히 서요"),
        check = listOf("기준: 감속 초당 11km/h 이상이면 급제동", "이 길의 신호 대기: 3곳 이상", "결과는 정차할 때와 리포트에서만 알려줘요"),
    ),
    "SMOOTH_START" to GuideDetail(
        "출발할 때 속도가 1초에 10km/h 넘게 붙으면 급출발로 기록해요. 평가 없이 사실만 알려줘요.",
        "신호가 바뀌고 뒤차가 기다리면 마음이 급해져서 가속 페달을 세게 밟기 쉬워요.",
        listOf("초록불이 켜지면 앞뒤를 한 번 봐요", "가속 페달은 천천히 눌러요", "앞차와 거리를 두고 따라가요"),
        check = listOf("기준: 가속 초당 10km/h 이상이면 급출발", "신호 대기 뒤 출발할 때마다 판정해요", "결과는 정차할 때와 리포트에서만 알려줘요"),
    ),
    "SIG_CANCEL" to GuideDetail(
        "진로 변경을 끝냈으면 방향지시등을 꺼요. 계속 켜 두면 다른 차가 내가 또 바꾸는 줄 알아요.",
        "합류처럼 핸들을 조금만 돌리면 방향지시등이 저절로 꺼지지 않아요. 이 길에는 이런 곳이 3번 있어요.",
        listOf("합류·분기를 마치면 계기판 깜빡이 표시를 봐요", "켜져 있으면 레버를 가운데로 돌려놔요", "통과하고 10초 안에 꺼져 있으면 성공이에요"),
        dia = "merge", practice = listOf(sig("R", "오른쪽"), SIG_OFF),
    ),
    "TURN" to GuideDetail(
        "회전하려는 지점 30m 전부터 방향지시등을 켜서 뒤차와 옆차가 미리 알 수 있게 해요.",
        "국민대 정문 앞처럼 출발하자마자 나오는 회전은 준비할 시간이 짧아서 늦게 켜기 쉬워요.",
        listOf("내비가 “300m 앞 회전”을 알려주면 손을 레버 위에 올려 둬요", "회전 지점 30m 전, 승용차 6대 남짓 거리에서 켜요", "회전을 마친 뒤 저절로 꺼졌는지 확인해요"),
        dia = "turn", practice = listOf(sig("L", "왼쪽"), sig("R", "오른쪽"), SIG_OFF),
    ),
    "TURN_SPEED" to GuideDetail(
        "교차로에서 회전할 때는 서행해야 해요. 회전하는 순간 20km/h 이하면 성공이에요.",
        "학여울역처럼 넓은 교차로는 속도감이 덜해서 빠르게 돌다가 바깥 차로로 밀리기 쉬워요.",
        listOf("회전 전에 미리 속도를 줄여요", "핸들을 돌리는 동안에는 가속하지 않아요", "다 돌고 바퀴가 펴지면 천천히 가속해요"),
        check = listOf("기준: 회전 지점 통과 속도 20km/h 이하", "이 길의 회전: 좌회전 1곳 · 우회전 2곳", "판정은 차량 속도로만 해요"),
    ),
    "RED_RIGHT_STOP" to GuideDetail(
        "빨간불에 우회전하려면 정지선 앞에서 반드시 완전히 멈춘 뒤에 가야 해요. 차가 멈췄는지는 차량 데이터로 보고, 신호 색은 차가 알 수 없어서 멈추지 않았을 때만 주차 후에 여쭤봐요.",
        "앞차를 따라 그대로 돌거나, 보행자가 없어 보이면 속도만 줄이고 지나가기 쉬워요.",
        listOf("빨간불이면 정지선 앞에서 바퀴가 완전히 멈출 때까지 서요", "횡단보도에 보행자가 있는지 좌우를 확인해요", "보행자가 없을 때 천천히 우회전해요"),
        check = listOf("기준: 우회전 직전에 완전히 정지했는지 관측", "멈추지 않았으면 리포트에서 빨간불이었는지 여쭤봐요", "답하지 않으면 성공·놓침으로 확정하지 않아요"),
    ),
    "SCHOOL" to GuideDetail(
        "어린이보호구역에서는 30km/h 이하로 달려야 해요. 들어가는 순간의 속도로 판정해요.",
        "정릉로는 출발 직후라 속도가 붙기 시작하는 구간이고, 표지를 늦게 보면 그대로 들어가기 쉬워요.",
        listOf("노란 표지판과 빨간 노면이 보이면 보호구역이에요", "진입하는 순간 30km/h 이하로 맞춰요", "구역 안에서는 계속 30 이하로 가요"),
        check = listOf("보호구역 제한속도는 30km/h예요", "이 길에는 출발 직후 보호구역이 1곳 있어요", "진입 순간 속도로 판정해요"),
    ),
    "UNPROT_SIG" to GuideDetail(
        "비보호 좌회전도 회전이에요. 기다리는 동안에도 30m 전부터 왼쪽 방향지시등을 켜 둬요.",
        "맞은편 차를 보느라 방향지시등까지 신경 쓰기 어려워요.",
        listOf("비보호 표지가 보이면 미리 왼쪽 방향지시등", "멈춰서 기다리는 동안에도 계속 켜 둬요", "돌고 나서 꺼졌는지 확인해요"),
        dia = "turn", practice = listOf(sig("L", "왼쪽"), SIG_OFF),
    ),
    "UNPROT_TIME" to GuideDetail(
        "비보호 좌회전은 정답이 상황마다 달라서 평가하지 않아요. 몇 번 멈췄는지, 얼마나 기다렸는지만 기록해요.",
        "언제 들어가야 할지 몰라 오래 기다리거나, 뒤차 때문에 급하게 들어가기 쉬워요.",
        listOf("초록불일 때만 진행할 수 있어요", "맞은편 차가 충분히 멀 때 들어가요", "애매하면 다음 신호를 기다려도 괜찮아요"),
        check = listOf("평가 없이 기록만 해요", "기록: 멈춘 횟수 · 기다린 시간 · 통과 속도", "리포트에서 지난 기록과 비교할 수 있어요"),
    ),
    "MERGE" to GuideDetail(
        "합류·분기·진출도 진로 변경이에요. 바꾸기 30m 전부터 켜요. 고속도로는 100m 전이에요.",
        "내부순환로 진입처럼 자동차전용도로 합류는 속도 차이가 커서, 본선 차를 보느라 방향지시등을 늦게 켜기 쉬워요.",
        listOf("내비가 합류·분기를 알려주면 들어갈 쪽을 확인해요", "진로를 바꾸기 30m 전에 그쪽 방향지시등을 켜요", "다 들어간 뒤에 꺼요"),
        dia = "merge", practice = listOf(sig("R", "오른쪽"), SIG_OFF),
    ),
    "TUNNEL" to GuideDetail(
        "터널 안에서는 전조등을 켜야 해요. AUTO로 두면 터널에서 알아서 켜져요.",
        "정릉터널은 내부순환로에 합류하자마자 나와요. 합류에 집중하다가 전조등을 잊기 쉬워요.",
        listOf("전조등을 AUTO로 두면 따로 할 일이 없어요", "수동이라면 터널 입구 전에 켜요", "터널을 나오면 다시 확인해요"),
        practice = listOf(HL_AUTO),
    ),
    "SPEED" to GuideDetail(
        "제한속도를 넘은 상태가 5초 넘게 이어지면 기록돼요. 구간마다 제한속도가 달라요.",
        "내부순환로 70, 동부간선 60, 영동대로 50으로 구간마다 바뀌어서 속도를 그대로 유지하기 쉬워요.",
        listOf("표지판 숫자와 계기판 제한속도 표시를 같이 봐요", "도로가 바뀌는 곳에서 다시 확인해요", "내리막에서는 속도가 저절로 붙어요"),
        check = listOf("내부순환로 70km/h", "동부간선도로 60km/h", "영동대로 50km/h · 초과 5초 이상이면 기록"),
    ),
    "RAIN_SLOW" to GuideDetail(
        "비가 와서 노면이 젖으면 제한속도보다 20% 줄여서 달려야 해요. 제한속도의 80%를 넘긴 상태가 10초 넘게 이어지면 놓친 것으로 봐요.",
        "내부순환로처럼 흐름이 빠른 길에서는 주변 차 속도에 맞추다 보면 평소 속도 그대로 달리기 쉬워요.",
        listOf("비가 오기 시작하면 계기판 속도부터 확인해요", "제한속도의 80%를 넘지 않게 맞춰요 (70km/h 구간이면 56km/h)", "앞차와의 거리도 평소보다 넉넉히 둬요"),
        check = listOf("기준: 제한속도의 80% 초과가 10초 이상이면 놓침", "내부순환로 56 · 동부간선 48 · 영동대로 40km/h", "상황 가이드 동의를 따라요"),
    ),
    "WEAVE_EXIT" to GuideDetail(
        "들어오는 차와 나가는 차가 엇갈리는 구간에서는 미리 차로를 바꿔 두고 나가요. 진출 50m 전까지 차로 변경을 마치고, 마지막 50m에서 급하게 꺾거나 급하게 줄이지 않으면 성공이에요.",
        "영동대교 북단처럼 진출로가 짧은 곳은 끝까지 버티다가 마지막에 급하게 끼어들기 쉬워요.",
        listOf("내비가 진출을 알려주면 바로 바깥 차로로 옮길 준비를 해요", "진출 50m 전까지 차로 변경을 끝내요", "놓쳤으면 무리하지 말고 다음 출구로 가요"),
        check = listOf("기준: 진출 50m 전 차로 변경 완료", "마지막 50m 급조향·급감속 없음", "이 길의 엇갈림 진출: 1곳"),
    ),
    "HS_NSCC" to GuideDetail(
        "내비게이션 기반 스마트 크루즈 컨트롤(NSCC 2)을 직접 켜고 끄면서 구간 제한속도를 지켜 보는 경험이에요. 성공·놓침을 판정하지 않아요.",
        "보조 기능을 켜면 차가 다 해줄 것 같지만, 핸들을 잡고 앞을 보는 건 그대로 운전자 몫이에요.",
        listOf("자동차전용도로에서 핸들의 크루즈 버튼으로 켜요", "차간거리 단계를 넉넉하게 맞춰요", "핸들 경고가 뜨면 바로 핸들을 잡고, 나가기 전에 직접 꺼요"),
        check = listOf("경험형이라 판정하지 않아요", "직접 켜고 끄기 · 차간거리 · 핸들 경고 · 속도를 살펴봐요", "경험하면 +3 XP"),
    ),
    "HS_GLEO" to GuideDetail(
        "Gleo에게 말로 전조등이나 와이퍼를 맞춰 보는 경험이에요. 주차 중 실습에서 설정이 바뀌는지만 확인하고, 주행 중에는 판정하지 않아요.",
        "운전 중에 레버나 다이얼을 찾으면 시선이 흔들려요. 말로 할 수 있다는 걸 미리 알아 두면 손이 덜 가요.",
        listOf("스티어링 휠의 음성 버튼을 눌러요", "“전조등 AUTO로 해줘”처럼 짧게 말해요", "계기판 표시가 바뀌었는지 확인해요"),
        practice = listOf(GLEO_HL, GLEO_WP),
    ),
    "HS_RSPA" to GuideDetail(
        "원격 스마트 주차 보조(RSPA)로 본사 주차장에 주차해 보는 경험이에요. 차가 확인할 수 없어서 주차한 뒤에 직접 알려주시면 기록해요.",
        "처음엔 차가 스스로 움직이는 게 낯설어서 중간에 멈추거나 직접 하게 돼요.",
        listOf("주차 칸 앞에 서서 주차 버튼을 눌러요", "화면에서 주차할 칸을 골라요", "주변에 사람이나 장애물이 없는지 계속 살펴봐요"),
        check = listOf("경험형이라 판정하지 않아요", "주차 후 리포트에서 해봤는지 알려주세요", "경험하면 +3 XP"),
    ),
)

data class TodayQuest(val title: String, val why: String, val practice: List<PStep>)

fun todayQuest(rainy: Boolean): TodayQuest = if (rainy) {
    TodayQuest("와이퍼·전조등 AUTO 맞추기", "오늘 비 예보가 있어요. 출발 전에 둘 다 AUTO로 맞춰 두면 비가 와도 손이 덜 가요.", listOf(WP_AUTO, HL_AUTO))
} else {
    TodayQuest("출발 전 기본 세팅", "오늘 경로에는 정릉터널과 합류·분기 3곳이 있어요. 오토 라이트를 켜 두고 방향지시등 레버를 손에 익혀요.", listOf(HL_AUTO, sig("R", "오른쪽"), SIG_OFF))
}

const val TODAY_KEY = "today"

// ───────────────────────── 성장 · 라이선스 ─────────────────────────

/** 라이선스 임계값 (누적 XP). 퀘스트 Lv와는 별개예요. F 단계는 Future Work. */
val TIERS = listOf("L0" to 30, "L1" to 500, "L2" to 1500, "P0" to 3000, "P1" to 9000, "P2" to 18000, "F1" to 32000, "F2" to 96000)

/**
 * 보상 정의: 퀘스트 성공은 가이드 Lv만큼([Guide.pts]), 도착·리포트 5, Hyundai ONLY 3,
 * 출발 전 주차 실습·내용 확인 완료 5.
 */
object XP {
    const val ARRIVE = 5
    const val HS = 3
    const val PRE = 5
    const val PRAC = PRE
}

/** 아직 첫 라이선스(L0) 임계값에 못 미치면 null */
fun tierOf(xp: Int): Pair<String, Int>? = TIERS.lastOrNull { xp >= it.second }
fun nextTier(xp: Int): Pair<String, Int>? = TIERS.firstOrNull { it.second > xp }

data class GuideState(
    val status: String = "learning", // learning / graduated / returned
    val count: Int = 0,
    val places: List<String> = emptyList(),
    val recent: List<Char> = emptyList(),
    val lastFail: Boolean = false,
    val seen: Boolean = false,
)

data class UserProfile(
    val st: Map<String, GuideState>,
    val xp: Int,
    val drives: Int,
    val routeMemory: Boolean,
    val streak: Int,
)

fun remaining(st: Map<String, GuideState>) = st.count { (k, s) -> guide(k).type == GType.AUTO && s.status != "graduated" }
fun needGrad(lv: Int, s: GuideState) = if (lv <= 2) s.count >= 1 else s.count >= 3

fun seedUser(profile: String): UserProfile {
    val st = GUIDES.keys.associateWith { GuideState() }.toMutableMap()
    if (profile == "L0") return UserProfile(st, xp = 0, drives = 0, routeMemory = false, streak = 3)
    fun g(k: String, s: GuideState) { st[k] = s }
    g("SEATBELT", GuideState("graduated", 1, listOf("a"), "SSS".toList(), seen = true))
    g("HEADLIGHT", GuideState("graduated", 1, listOf("a"), "SS".toList(), seen = true))
    g("SMOOTH", GuideState("graduated", 1, listOf("a"), "SSSSS".toList(), seen = true))
    g("SMOOTH_START", GuideState("graduated", 1, listOf("a"), "SSS".toList(), seen = true))
    g("SIG_CANCEL", GuideState(recent = "FF".toList(), seen = true, lastFail = true))
    g("TURN", GuideState("graduated", 3, listOf("a", "b", "c"), "SSFSS".toList(), seen = true))
    g("TURN_SPEED", GuideState(count = 2, places = listOf("a", "b"), recent = "SFS".toList(), seen = true))
    g("RED_RIGHT_STOP", GuideState(count = 1, places = listOf("a"), recent = "S".toList(), seen = true))
    g("RAIN_SLOW", GuideState(recent = "F".toList(), seen = true))
    g("WEAVE_EXIT", GuideState(count = 2, places = listOf("a", "b"), recent = "SS".toList(), seen = true))
    g("HS_NSCC", GuideState(seen = true))
    g("SCHOOL", GuideState(count = 2, places = listOf("a", "b"), recent = "SFS".toList(), seen = true))
    g("UNPROT_SIG", GuideState(count = 2, places = listOf("a", "b"), recent = "SS".toList(), seen = true))
    g("UNPROT_TIME", GuideState(seen = true))
    g("MERGE", GuideState(count = 2, places = listOf("a", "b"), recent = "SSFS".toList(), seen = true, lastFail = true))
    g("TUNNEL", GuideState(count = 1, places = listOf("a"), recent = "FS".toList(), seen = true))
    g("SPEED", GuideState(count = 1, places = listOf("a"), recent = "SSS".toList(), seen = true))
    return UserProfile(st, xp = 460, drives = 9, routeMemory = true, streak = 12)
}

// ───────────────────────── 경로 (국민대 → 현대자동차 양재 본사) ─────────────────────────

data class Poi(val id: String, val d: Int, val kind: String, val name: String, val road: String = "", val dir: String? = null, val lab: String? = null)

val POIS = listOf(
    Poi("p1", 650, "turn", "국민대 정문 앞 교차로", "정릉로 방면", "L"),
    Poi("z1", 1500, "school", "정릉 어린이보호구역"),
    Poi("m1", 3100, "merge", "내부순환로 정릉 진입로", "내부순환로 성수 방면", "R", "합류"),
    Poi("t1", 3800, "tunnel", "정릉터널"),
    Poi("m2", 9200, "merge", "성수JC 동부간선", "동부간선도로 방면", "R", "분기"),
    Poi("m3", 11000, "merge", "영동대교 북단", "영동대교 방면", "R", "진출"),
    Poi("p2", 16800, "turn", "학여울역 사거리", "양재대로 방면", "R"),
    Poi("u1", 20400, "unprot", "양재 시민의숲 앞", "비보호 좌회전", "L"),
    Poi("p3", 21250, "turn", "현대자동차 본사 앞", "본사 진입", "R"),
    Poi("dest", 21420, "dest", "현대자동차 양재 본사", "목적지"),
)

data class Stop(val id: String, val d: Int, val name: String, val holdMs: Long)

val STOPS = listOf(
    Stop("s1", 2300, "정릉삼거리 신호", 2200),
    Stop("s2", 12600, "청담사거리 신호", 2200),
    Stop("s3", 18500, "도곡동 신호", 2200),
    Stop("uw", 20340, "비보호 좌회전 대기", 1400),
)

const val DEST = 21420
const val RAIN_START_D = 5200

private val ANCH = listOf(
    floatArrayOf(0f, 150f, 38f), floatArrayOf(650f, 166f, 50f), floatArrayOf(2300f, 190f, 78f),
    floatArrayOf(3100f, 205f, 94f), floatArrayOf(4600f, 234f, 118f), floatArrayOf(9200f, 276f, 206f),
    floatArrayOf(11000f, 262f, 236f), floatArrayOf(12600f, 250f, 272f), floatArrayOf(16800f, 240f, 350f),
    floatArrayOf(18500f, 206f, 372f), floatArrayOf(20400f, 170f, 400f), floatArrayOf(21250f, 152f, 418f),
    floatArrayOf(21420f, 146f, 425f),
)

/** 경로 거리 d(m) → 지도 좌표 (360×440 엔진 좌표계). */
fun posAt(d: Float): Offset {
    for (i in 1 until ANCH.size) {
        val a = ANCH[i - 1]
        val b = ANCH[i]
        if (d <= b[0]) {
            val k = ((d - a[0]) / (b[0] - a[0])).coerceIn(0f, 1f)
            return Offset(a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k)
        }
    }
    val l = ANCH.last()
    return Offset(l[1], l[2])
}

val ROUTE_POINTS: List<Offset> = (0..DEST step 60).map { posAt(it.toFloat()) } + posAt(DEST.toFloat())

fun nextManeuver(d: Float): Poi? = POIS.firstOrNull { it.d > d && it.kind in setOf("turn", "merge", "unprot", "dest") }

fun routeCount(k: String, rainy: Boolean): String = when (k) {
    "TURN" -> "오늘 경로 좌회전 1회 · 우회전 2회"
    "MERGE" -> "오늘 경로 합류·분기 3회"
    "SCHOOL" -> "오늘 경로 어린이보호구역 1곳"
    "TUNNEL" -> "오늘 경로 터널 1곳"
    "UNPROT_SIG" -> "오늘 경로 비보호 좌회전 1곳"
    "SPEED" -> "내부순환로 70 · 동부간선 60 · 영동대로 50"
    "SMOOTH_START" -> "오늘 경로 신호 출발 3곳 이상"
    "SIG_CANCEL" -> "오늘 경로 합류·분기 뒤 3번"
    "TURN_SPEED" -> "오늘 경로 회전 3곳"
    "HEADLIGHT" -> "출발 순간 판정 · 정릉터널 1곳"
    "UNPROT_TIME" -> "오늘 경로 비보호 좌회전 1곳 · 기록만 해요"
    "RAIN" -> if (rainy) "비 감지 후 10초 안에 판정" else "오늘은 맑음 · 비가 오면 판정해요"
    "SMOOTH" -> "오늘 경로 신호 정차 3곳 이상"
    "RED_RIGHT_STOP" -> "오늘 경로 우회전 2곳"
    "RAIN_SLOW" -> if (rainy) "비가 오는 동안 구간마다 판정" else "오늘은 맑음 · 비가 오면 판정해요"
    "WEAVE_EXIT" -> "오늘 경로 엇갈림 진출 1곳"
    "HS_NSCC" -> "자동차전용도로에서 경험 · 판정 없음"
    "HS_GLEO" -> "주차 중 실습으로 경험 · 주행 판정 없음"
    "HS_RSPA" -> "도착 후 주차장에서 경험 · 직접 알려주기"
    "SEATBELT" -> "출발 순간 판정"
    else -> ""
}

// ───────────────────────── 퀘스트 (주행 중 비공개 판정) ─────────────────────────

data class Quest(
    val id: String,
    val g: String,
    val place: String, // 운전자에게 보여줄 지점명
    val short: String,
    val d: Int, // 판정 지점 (경로 거리)
    val res: Char, // S 성공 · F 놓침 · R 기록 · H 보류(운전자 확인 필요) · E 경험 · N 해당 없음
    val ev: String, // 판정 근거
    val dir: String? = null,
    val lab: String? = null,
    val limit: Int = 0,
    val fb: Boolean = true, // 졸업한 가이드는 정차 피드백 없음
    val consent: Boolean = false, // 상황 가이드 동의 대기 중 (차 안 임시 보관)
    val disputed: Boolean = false,
)

/**
 * 프로토타입의 시나리오 스크립트를 판정 결과로 옮긴 것.
 * 실제 앱에서는 NaviHelper 이벤트 + Vehicle SDK 신호로 생성·판정돼요.
 */
fun buildQuests(profile: String, user: UserProfile, veh: Vehicle, condConsent: String): List<Quest> {
    val l0 = profile == "L0"
    val lightOn = veh.headlight != "OFF" || !l0
    val wiperAuto = veh.wiper == "AUTO" || !l0
    val out = mutableListOf<Quest>()
    fun q(g: String, place: String, short: String, d: Int, ok: Boolean?, ev: String, dir: String? = null, lab: String? = null, limit: Int = 0, consent: Boolean = false, res: Char? = null) {
        out += Quest(
            id = "Q-%02d".format(out.size + 1), g = g, place = place, short = short, d = d,
            res = res ?: when (ok) { true -> 'S'; false -> 'F'; null -> 'R' }, ev = ev, dir = dir, lab = lab, limit = limit,
            fb = user.st[g]?.status != "graduated", consent = consent,
        )
    }
    q("SEATBELT", "국민대학교 출발", "출발", 0, veh.belt, if (veh.belt) "출발 시 안전벨트 체결" else "출발 시 안전벨트 해제 상태")
    q("HEADLIGHT", "국민대학교 출발", "출발", 60, lightOn, if (lightOn) "출발 시 전조등 ${if (veh.headlight == "OFF") "AUTO" else veh.headlight}" else "출발 10초 뒤 전조등 OFF")
    q("TURN", "국민대 정문 앞 교차로", "국민대 정문", 650, false, if (l0) "9m 전 점등 (기준 30m)" else "10m 전 점등 (기준 30m)", dir = "L")
    q("TURN_SPEED", "국민대 정문 앞 교차로", "국민대 정문", 655, true, "회전 속도 16km/h", dir = "L")
    q("SCHOOL", "정릉 어린이보호구역", "보호구역", 1500, !l0, if (l0) "진입 속도 34km/h (기준 30)" else "진입 속도 27km/h")
    q("SMOOTH", "정릉삼거리 신호", "정릉삼거리", 2300, true, "감속 초당 6.2km/h")
    q("SMOOTH_START", "정릉삼거리 신호", "정릉삼거리", 2310, !l0, if (l0) "가속 초당 12.4km/h (기준 10)" else "가속 초당 6.1km/h")
    q("MERGE", "내부순환로 정릉 진입로", "내부순환 합류", 3100, true, if (l0) "45m 전 점등" else "50m 전 점등", dir = "R", lab = "합류")
    q("SIG_CANCEL", "내부순환로 정릉 진입로", "내부순환 합류", 3110, !l0, if (l0) "통과 뒤 33초 동안 켜져 있었어요" else "통과 4초 뒤 꺼짐", lab = "합류")
    q("TUNNEL", "정릉터널", "정릉터널", 3800, lightOn, if (lightOn) "터널 진입 시 전조등 켜짐" else "터널 진입 시 전조등 OFF")
    if (condConsent != "off") {
        q("RAIN", "비가 시작된 곳", "비 시작", 5600, wiperAuto, if (wiperAuto) "비 감지 3초 뒤 와이퍼·전조등 켜짐" else "비 감지 14초 뒤 와이퍼 작동 (기준 10초)", consent = condConsent == "ask")
    }
    if (!l0) q("HS_NSCC", "내부순환로", "NSCC 2", 6400, null, "NSCC 2 직접 켜고 끔 · 3분 40초 사용", res = 'E')
    if (condConsent != "off") {
        q("RAIN_SLOW", "내부순환로 빗길", "빗길 감속", 7400, !l0, if (l0) "56km/h(제한의 80%) 초과 18초 (기준 10초)" else "최고 54km/h · 초과 0초", limit = 70, consent = condConsent == "ask")
    }
    q("SPEED", "내부순환로", "내부순환로", 9190, true, "최고 66km/h · 초과 0초", limit = 70)
    q("MERGE", "성수JC 동부간선", "성수JC", 9200, true, if (l0) "38m 전 점등" else "48m 전 점등", dir = "R", lab = "분기")
    q("SIG_CANCEL", "성수JC 동부간선", "성수JC", 9210, true, "통과 3초 뒤 꺼짐", lab = "분기")
    q("SPEED", "동부간선도로", "동부간선", 10990, true, "최고 56km/h · 초과 0초", limit = 60)
    q("WEAVE_EXIT", "영동대교 북단", "영동대교 북단", 10980, true, if (l0) "진출 58m 전 차로 변경 완료" else "진출 72m 전 차로 변경 완료", dir = "R", lab = "진출")
    q("MERGE", "영동대교 북단", "영동대교 북단", 11000, false, if (l0) "12m 전 점등 (기준 30m)" else "20m 전 점등 (기준 30m)", dir = "R", lab = "진출")
    q("SIG_CANCEL", "영동대교 북단", "영동대교 북단", 11010, true, "통과 5초 뒤 꺼짐", lab = "진출")
    q("SMOOTH", "청담사거리 신호", "청담사거리", 12600, !l0, if (l0) "감속 초당 13.8km/h (기준 11)" else "감속 초당 7.0km/h")
    q("SMOOTH_START", "청담사거리 신호", "청담사거리", 12610, true, "가속 초당 7.4km/h")
    q("SPEED", "영동대로", "영동대로", 16790, true, "최고 47km/h · 초과 0초", limit = 50)
    if (l0) q("RED_RIGHT_STOP", "학여울역 사거리", "학여울역", 16795, null, "일시정지가 관측되지 않았어요 · 신호 색은 차가 알 수 없어요", dir = "R", res = 'H')
    else q("RED_RIGHT_STOP", "학여울역 사거리", "학여울역", 16795, true, "정지 1.8초 뒤 우회전", dir = "R")
    q("TURN", "학여울역 사거리", "학여울역", 16800, true, if (l0) "35m 전 점등" else "40m 전 점등", dir = "R")
    q("TURN_SPEED", "학여울역 사거리", "학여울역", 16805, !l0, if (l0) "회전 속도 27km/h (기준 20)" else "회전 속도 17km/h", dir = "R")
    q("SMOOTH", "도곡동 신호", "도곡동", 18500, true, "감속 초당 5.9km/h")
    q("SMOOTH_START", "도곡동 신호", "도곡동", 18510, true, "가속 초당 6.8km/h")
    q("UNPROT_SIG", "양재 시민의숲 앞", "비보호 좌회전", 20400, true, if (l0) "40m 전 점등" else "45m 전 점등", dir = "L")
    q("UNPROT_TIME", "양재 시민의숲 앞", "비보호 좌회전", 20405, null, "정지 2회 · 대기 13초 · 통과 18km/h")
    q("RED_RIGHT_STOP", "현대자동차 본사 앞", "본사 앞", 21245, true, "정지 2.1초 뒤 우회전", dir = "R")
    q("TURN", "현대자동차 본사 앞", "본사 앞", 21250, true, if (l0) "32m 전 점등" else "40m 전 점등", dir = "R")
    q("TURN_SPEED", "현대자동차 본사 앞", "본사 앞", 21255, true, "회전 속도 14km/h", dir = "R")
    q("HS_RSPA", "본사 주차장", "주차장", 21400, null, "차가 확인할 수 없어요 · 직접 알려주세요", res = 'H')
    return out
}

fun fbText(q: Quest): String {
    val dirKo = if (q.dir == "L") "좌" else "우"
    return when (q.g) {
        "TURN" -> "${q.place}에서 ${dirKo}회전 방향지시등을 30m 전에 켜셔야 해요."
        "UNPROT_SIG" -> "${q.place}에서 좌회전 방향지시등을 30m 전에 켜셔야 해요."
        "MERGE" -> "${q.place}에서 ${q.lab} 방향지시등을 30m 전에 켜셔야 해요."
        "SCHOOL" -> "${q.place}에는 30km/h 이하로 들어가셔야 해요."
        "TUNNEL" -> "${q.place}에서는 전조등을 켜셔야 해요."
        "SMOOTH" -> "${q.place}에서 브레이크를 세게 밟으셨어요."
        "RAIN" -> "비가 오면 와이퍼와 전조등을 켜셔야 해요."
        "SMOOTH_START" -> "${q.place}에서 급하게 출발하셨어요."
        "SIG_CANCEL" -> "${q.place} ${q.lab} 뒤에는 방향지시등을 끄셔야 해요."
        "TURN_SPEED" -> "${q.place}에서는 20km/h 이하로 천천히 도셔야 해요."
        "SPEED" -> "${q.place}에서는 제한속도 ${q.limit}km/h를 지키셔야 해요."
        "RED_RIGHT_STOP" -> "${q.place}에서 빨간불 우회전은 완전히 멈춘 뒤에 하셔야 해요."
        "RAIN_SLOW" -> "비가 올 때는 제한속도보다 20% 줄여서 달리셔야 해요."
        "WEAVE_EXIT" -> "${q.place}에서는 진출 50m 전에 차로를 바꿔 두셔야 해요."
        "SEATBELT" -> "출발 전에 안전벨트를 매셔야 해요."
        "HEADLIGHT" -> "출발할 때 전조등을 AUTO로 두셔야 해요."
        else -> q.place
    }
}

/** 시뮬레이션 시각: 08:12:00 기준 */
fun clockText(sec: Float, withSeconds: Boolean = false): String {
    val s = (8 * 3600 + 12 * 60 + sec).toInt()
    val h = (s / 3600) % 24
    val m = (s / 60) % 60
    return if (withSeconds) "%02d:%02d:%02d".format(h, m, s % 60) else "%02d:%02d".format(h, m)
}
