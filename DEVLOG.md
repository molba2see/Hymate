# 개발 내역 (DEVLOG)

Hy! mate · Pleos Connect 차량용 운전 코칭 앱. 코드 리뷰 · 디자인 통일 · Pleos 컴플라이언스 작업 기록.

## 2026-11-15

### 1. 코드 리뷰 품질 수정 (`717c702`)
- `ReportScreen.GrowBox`: `computeGrowth()`를 `remember(quests, user)`로 메모이즈 — XP 게이지 애니메이션 중 매 프레임 재계산 제거.
- `HyViewModel.progress`: `private _progress` + 읽기 전용 `val progress`로 캡슐화(나머지 상태와 일관).
- 숫자·시간 포맷을 `Locale.US`로 고정(로케일별 숫자/소수점 깨짐 방지).

### 2. CourseHero 테마 통일 (`5d11960`)
- 홈 "오늘 코스" 히어로 카드의 자체 색(`Night`/`Mint`) 제거 → `hy.*` 테마 토큰 + 브랜드 액센트로 통일하고 라이트/다크 테마를 따르게 변경. (`Mint`는 기존 `arcadeMint` 토큰과 중복이었음.)

### 3. Pleos 컴플라이언스 (이번 커밋)
Pleos 개발자 가이드(document.pleos.ai)와 제출 기획서를 대조해 **엄격 필수 규칙** 위반을 수정.

**Phase 0 — 설정**
- `applicationId`: `com.example.hymate` → `ai.hymate.app` (스토어 예약어 회피 · Kotlin 패키지/namespace는 유지).
- `targetSdk`: 37 → 34 (Connect 플랫폼이 API 34 · `compileSdk`는 설치된 37 유지, `compileSdk ≥ targetSdk`).

**Phase 1 — Gleo 참칭 제거**
- 가이드상 서드파티 앱은 Gleo를 호출·대행할 수 없음(Gleo가 App Schema로 앱을 호출하는 구조). 앱이 'Gleo'인 척하던 음성 UI 문구를 전부 **"Hy! mate"**로 변경.
- 차량의 실제 Gleo 음성 조작 교육(HS_GLEO)은 "차량 기능"으로 유지.

**Phase 2 — CarUxRestrictions**
- `distractionOptimized=true` 선언 앱은 시스템 `CarUxRestrictions`를 따라야 함. 자체 기어(P) 잠금을 `UxRestrictionsSource`(리플렉션으로 AAOS `CarUxRestrictionsManager` 연결)로 교체. 차량이 아니면 기어 폴백, 값을 못 읽으면 안전하게 '제한됨'.
- `AndroidManifest`에 `<uses-library android:name="android.car" android:required="false" />` 추가.

## 컴플라이언스 현황
| 규칙 | 상태 |
|---|---|
| CarUxRestrictions 준수 | ✅ |
| Gleo 비참칭 | ✅ |
| applicationId / targetSdk | ✅ |
| 차량 권한 선언 / initialize·release | ✅ (기존 준수) |
| 오디오 포커스 | ⏸ 실제 재생 소리 없음 → TTS 도입 시(Phase 3) |

## 남은 로드맵
- **Phase 3**: 실제 음성(TextToSpeech / SpeechToText SDK) + 오디오 포커스(`AudioFocusRequest`, 더킹 `GAIN_TRANSIENT_MAY_DUCK`).
- **Phase 4 (선택)**: Gleo App Schema 정식 연동(Gleo Action ↔ Callback).
- **Phase 5**: 제출 메타데이터(개인정보처리방침 URL · 고객지원 · IARC 등급 · 개인정보 처리 항목).
- **스펙 갭 A-3**: 차종(아반떼/그랜저/투싼)별 미지원 가이드 자동 제외.
- 프로토타입 시뮬레이션(차속 `PERF_VEHICLE_SPEED` · NaviHelper · FusedLocation · 서버)은 실버전에서 실 SDK 연동 필요. 현재는 고정 모의 경로(국민대 → 양재 본사)로 시연.

## 빌드 메모
- `JAVA_HOME`이 깨져 있어 Android Studio JBR(JDK 25)로 빌드: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'` 후 `.\gradlew.bat assembleDebug`.
- `local.properties`(gitignore)에 `sdk.dir` 필요.
