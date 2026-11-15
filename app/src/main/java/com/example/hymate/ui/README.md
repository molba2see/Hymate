# ui

Jetpack Compose 화면. 디자인 기준 해상도(1700×1184, 17" Pleos Connect-S)를 `u`/`fs` 단위로 비례 변환해 어떤 창 크기에서도 같은 비율로 그립니다.

| 위치 | 내용 |
|---|---|
| `HyApp.kt` | 화면 전환 + 주행 중 잠금(DrivingLock). CarUxRestrictions가 걸리면 설정·연습·리포트를 가림 |
| `screens/` | 홈 → 출발 전(가이드 목록·동의) → 가이드 상세(주차 중 실습) → 주행(실지도 내비) → 리포트(판정 결과·자가 체크) → 설정 |
| `components/RoadMap.kt` | MapLibre + OpenStreetMap 타일 지도. 경로 선·차량 위치 표시, 카메라 2Hz 제한, 식별 User-Agent·디스크 캐시·출처 표기 |
| `components/HyComponents.kt` | 공용 컴포넌트(버튼·칩·게이지·픽셀 HUD 등) |
| `license/` | 라이선스 카드·발급 연출 |
| `theme/` | 색 토큰(`hy.*`)·단위 변환 |
