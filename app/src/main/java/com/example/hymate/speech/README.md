# speech

Pleos 차량 Speech Service를 통한 음성 안내(TTS)와 응답 인식(STT).

| 파일 | 내용 |
|---|---|
| `SpeechEngine.kt` | 엔진 인터페이스 + `SpeechConversation`(질문 재생이 끝난 뒤에만 마이크를 열고, 명확한 응·아니만 동의로 해석) |
| `PleosSpeechEngine.kt` | TextToSpeech/SpeechToText SDK(ON_DEVICE) 구현. 발화별 initialize→release, 늦게 도착한 콜백 격리, 침묵 6초 타임아웃 |

동의 안전 규칙(모호한 답·침묵·오류는 절대 YES가 되지 않음)은 `SpeechConversationTest.kt`로 검증합니다.
