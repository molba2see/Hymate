# 기획·가이드 대비 구현 점검 (REVIEW_GAPS)

기준 자료
- **팀 계획**: 공모전 제출 기획서 `최종_작성본.docx` (Hy! Mate · 국대오빠차) + `Hy_mate_파이프라인.html` (프로토타입 구현 스펙 v2 · 개발 순서 도식)
- **플랫폼 가이드**: Pleos Connect 개발자 문서 (document.pleos.ai / pleos-only)
- **점검 시점**: 2026-11-15 · `main` 최신 (라이브 차속 엔진 반영 후)

표기: ✅ 일치 · 🔶 부분 구현/의도적 시뮬레이션 · ❌ 미구현(계획·가이드와 다름)

---

## 1. 팀 계획(기획서) 대비

### 1-1. 계획대로 구현된 것 ✅
| 기획서 항목 | 구현 |
|---|---|
| 출발 전 가이드 선별(경로·날씨·누적 기록, 놓친 항목 우선) | `preGuides()` 태그/정렬 |
| 내 차 익히기 — 주차 중 실습, 차량 데이터로 수행 확인 | `PStep` + Vehicle SDK 신호 → `checkPractice()` |
| 퀘스트: 판정 지점 비공개, 주행 중 화면·음성 없음 | DriveScreen은 상태 알약만, 판정 지점 미노출 |
| **차량 속도: CarPropertyManager `PERF_VEHICLE_SPEED`** | `CarSpeedSource` — 계획서 명시 그대로 구현 (권한 체크·폴백 포함) |
| 정차 피드백: 주행당 가이드 1회, 재출발 시 즉시 중단 | `runDrive()`의 `fed` 집합 + `silenceWhenMoving()` |
| 주차 후 리포트: 가이드별 결과·근거, 이의 제외 | ReportScreen + `disputed` |
| 자가 체크(신호색·RSPA 등 차가 모르는 항목은 운전자 응답) | `resolveHold()` / 'H' 보류 |
| 성장: 졸업·복귀·경험치·라이선스 | `computeGrowth()` + `GrowthTest` 검증 |
| 판정·피드백을 차 안에서 처리, 궤적 원본 미전송 | 전부 온디바이스 (서버 자체가 없음) |
| 고정 모의 경로(국민대→양재 본사)로 통합 검증 | 시나리오·테스트 동일 경로 |

### 1-2. 계획과 다른 것 ❌
| # | 기획서 | 현재 구현 | 비고 |
|---|---|---|---|
| G1 | 그랜저·투싼은 **차종별 지원 기능 확인 후 미지원 가이드 자동 제외** | 차종 감지 없음 — 20개 가이드 항상 노출 | 신규 작업 필요 (Vehicle SDK capability 조회 확인) |
| G2 | Vehicle SDK로 **조향각·크루즈 상태** 조회 | 둘 다 미연동 (크루즈는 경험형 자가응답으로만) | SDK 지원 여부 확인 후 반영 |
| G3 | 졸업 가이드는 **출발 전 안내에서 제외** | 출발 전 목록에 '졸업' 태그로 흐리게 계속 노출 | 정차 피드백 제외는 ✅. UI 정책 결정 필요(완전 숨김 vs 접기) |
| G4 | 날씨는 **서버 조회, 실패 시 와이퍼로 우천 추정** | `rainy = true` 하드코딩 | 폴백(와이퍼 추정)만이라도 구현 가능 |
| G5 | TextToSpeech SDK로 **실제 음성** 피드백(음악 볼륨 낮춤) | 음성은 화면 배너 목업, 소리 없음 | 아래 P2와 동일 항목 |

### 1-3. 의도적 시뮬레이션 🔶 (기획서의 "대체 구현" 조항에 부합)
- **NaviHelper SDK**(회전·주의지점·제한속도) 미연동 → POI/정지선 하드코딩. 기획서 스스로 "에뮬레이터에서 수신이 어려우면 모의 이벤트로 시연" 허용.
- **FusedLocation** 미연동 → `posAt()` 경로 보간 + `routeMemory` 플래그.
- **서버**(가이드 배포·상태 저장·동기화) 없음 → in-memory. 재시작 시 초기화.
- 속도 외 판정값 일부는 아직 시나리오 스크립트 기반(`buildQuests`) — 단, 라이브 엔진(`LiveDriveSession`/`LiveQuestEvaluator`)이 실차속 기반 판정으로 대체 중.

---

## 2. Pleos 가이드 대비

### 2-1. 준수 ✅
| 가이드 요구 | 구현 |
|---|---|
| Kotlin · minSdk 28+ | ✅ (minSdk 28) |
| Vehicle SDK 2.3.2 · 공식 Maven 저장소 | ✅ |
| 도메인별 차량 권한 선언 (`CAR_LIGHTS`/`CAR_WINDOWS`/`CAR_DRIVE`/`CAR_SAFETY`) | ✅ + AAOS `CAR_SPEED` 런타임 요청 |
| `initialize()`/`release()` 수명주기 · 리스너 등록/해제 | ✅ `PleosVehicleSource` onStart/onStop |
| **DO 선언 앱은 CarUxRestrictions 준수** | ✅ `UxRestrictionsSource`(리플렉션) → `driving` 게이트 · 차량 아니면 기어(P) 폴백 |
| **Gleo 비참칭** (앱은 Gleo를 호출 못 함 — Gleo가 App Schema로 앱을 호출) | ✅ 앱 음성 주체를 "Hy! mate"로 리브랜드 · 차량의 실제 Gleo 교육(HS_GLEO)만 유지 |
| applicationId 예약어 금지 | ✅ `ai.hymate.app` |
| targetSdk = 플랫폼(API 34) 정렬 | ✅ 34 (compileSdk 37 유지) |
| 주행 중 주의분산 최소화(동영상·과도한 조작 금지) | ✅ 주행 화면은 표시 전용, 조작 화면은 잠금 |

### 2-2. 가이드와 다른 것 / 남은 의무 ❌
| # | 가이드 요구 | 현재 | 시점 |
|---|---|---|---|
| P1 | 모든 오디오 재생 전 `AudioFocusRequest` 요청 + 상실/획득 이벤트 처리 (짧은 안내는 `GAIN_TRANSIENT_MAY_DUCK`) | 아직 소리를 재생하지 않아 당장 위반은 아님 — **TTS/알림음 도입 즉시 필수** | TTS 도입 시 |
| P2 | 음성 출력은 **TextToSpeech SDK**(Speech Service 연동), 음성 인식은 **SpeechToText SDK** | 미연동 (화면 배너 목업) | 실음성 단계 |
| P3 | 앱 마켓 제출물: 개인정보처리방침 URL · 고객지원 연락처 · IARC 연령등급 · 개인정보 처리 항목 선언 · 스크린샷/아이콘 규격 | 미준비 | 제출 직전 |
| P4 | Gleo 연동을 원하면 **App Schema 등록**(Gleo Action ↔ Callback) 방식 | 미적용 (선택사항) | 선택 |

---

## 3. 우선순위 제안

1. **G1 차종별 가이드 제외** — 기획서 명시 + 심사 시 눈에 띄는 차별점. Vehicle SDK capability/차종 조회 API 확인부터.
2. **G4 우천 추정 폴백** — 작은 작업으로 "하드코딩 날씨" 제거 가능 (와이퍼 AUTO 작동 감지 → rainy).
3. **P1+P2+G5 실음성(TTS) + 오디오 포커스** — 한 묶음으로 진행해야 가이드 위반 없이 음성 데모 가능.
4. **G3 졸업 가이드 노출 정책** — 팀 결정 후 반영 (완전 숨김이면 한 줄 수정).
5. **G2 조향각·크루즈** — SDK 지원 확인 후. 미지원이면 기획서의 "운전자 응답 대체" 조항으로 현행 유지 가능.
6. **P3 제출 메타데이터** — 코드 아님. 제출 전 체크리스트로 관리.

---

## 4. 참고 — 이번 점검에서 해소된 과거 지적
- ~~자체 기어 잠금이 CarUxRestrictions 미준수~~ → `UxRestrictionsSource`로 해소
- ~~앱이 'Gleo'를 참칭~~ → "Hy! mate" 리브랜드로 해소
- ~~`com.example` applicationId / targetSdk 37~~ → `ai.hymate.app` / 34로 해소
- ~~차속 판정이 전부 사전 스크립트~~ → `CarSpeedSource` + 라이브 엔진으로 계획서 방식(PERF_VEHICLE_SPEED) 구현
