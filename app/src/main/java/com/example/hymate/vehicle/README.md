# vehicle

차량 데이터 연결과 주행 판정 엔진. 각 소스는 start/stop 수명주기를 갖고, 값을 받으면 메인 스레드로 ViewModel에 전달합니다. 차량이 아니면 조용히 `Unavailable`로 내려가 앱은 계속 동작합니다.

| 파일 | 내용 |
|---|---|
| `PleosVehicleSource.kt` | Vehicle SDK — 방향지시등·전조등/와이퍼 스위치·브레이크·벨트·기어·크루즈 수신, 차종별 capability 조회(미지원 가이드 제외) |
| `CarSpeedSource.kt` | AAOS `PERF_VEHICLE_SPEED` 차속 (CAR_SPEED 런타임 권한) |
| `LocationSource.kt` | Pleos FusedLocation 위경도 (경로 투영·재탐색용) |
| `UxRestrictionsSource.kt` | AAOS CarUxRestrictions — 주행 중 화면 잠금을 시스템 규제에 맞춤 |
| `LiveDriveSession.kt` | 차속×시간 적분 진행도·정차 판정(0.5km/h·3초) |
| `LiveQuestPlan.kt` | 탐색된 경로의 회전/합류 지점마다 판정 퀘스트 생성 |
| `LiveQuestEvaluator.kt` | 지점 통과 시 실제 신호로 성공/놓침 판정. 미수신 신호는 판정하지 않고 자가 체크로 넘김 |

테스트: `LiveDriveTest` · `LiveQuestFlowTest` · `RouteGeoTest`.
