# navigation

실제 도로 길찾기. OSRM Route API로 자동차 경로를 받아 지도·회전 안내·ETA·퀘스트 지점을 모두 같은 응답에서 만듭니다.

| 파일 | 내용 |
|---|---|
| `OsrmRoutingClient.kt` | OSRM `/route/v1/driving` 호출. HTTPS 강제, 요청 간격 1.1초 제한, 429 대응. 파서(`OsrmRouteParser`)는 Android 비의존이라 단위 테스트 가능 |
| `RoadRoute.kt` | 탐색된 폴리라인 모델. 경로거리→좌표(`pointAt`), GPS→경로 투영(`project`), 다음 회전·잔여시간 계산. `RouteTracker`가 이탈 판정(60m·3초·2회)과 재탐색 간격(15초)을 관리 |

테스트는 `app/src/test/.../NavigationTest.kt`.
