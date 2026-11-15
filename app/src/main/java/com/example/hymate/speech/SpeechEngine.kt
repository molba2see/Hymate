package com.example.hymate.speech

import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select

/** Activity 수명주기에 연결된 음성 입출력. ViewModel은 Context와 SDK 객체를 소유하지 않아요. */
interface SpeechEngine {
    suspend fun speak(text: String): Boolean
    suspend fun listen(onStarted: () -> Unit): Recognition
    fun stop()
}

sealed interface Recognition {
    data class Text(val text: String) : Recognition
    data object NoSpeech : Recognition
    data object Unavailable : Recognition
}

enum class VoiceAnswer { YES, NO, NO_RESPONSE, UNAVAILABLE }

/** 부분 문자열이나 추측으로 동의를 만들지 않고 명확한 응답만 받아요. */
fun parseVoiceAnswer(text: String): VoiceAnswer {
    val normalized = text.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    return when (normalized) {
        "응", "네", "예", "좋아", "좋아요", "그래", "그래요", "동의해요", "동의합니다",
        "넣어줘", "넣어주세요", "포함해줘", "포함해주세요", "추가해줘", "추가해주세요",
        "네넣어주세요", "네추가해주세요", "yes", "ok", "okay" -> VoiceAnswer.YES
        "아니", "아니요", "아니오", "싫어", "싫어요", "안할래", "안할래요", "빼줘", "빼주세요",
        "제외해줘", "제외해주세요", "넣지마", "넣지마세요", "추가하지마", "추가하지마세요",
        "동의안해", "동의하지않아요", "no" -> VoiceAnswer.NO
        else -> VoiceAnswer.NO_RESPONSE
    }
}

/** TTS 완료 → 마이크 시작 → 최종 결과 또는 화면 버튼. 취소는 호출자에게 그대로 전파해요. */
class SpeechConversation(private val engine: SpeechEngine) {
    suspend fun ask(
        text: String,
        manualAnswer: CompletableDeferred<Boolean>,
        onListening: () -> Unit,
    ): VoiceAnswer {
        if (!engine.speak(text)) return VoiceAnswer.UNAVAILABLE
        return coroutineScope {
            val recognition = async { engine.listen(onListening) }
            try {
                select {
                    manualAnswer.onAwait { if (it) VoiceAnswer.YES else VoiceAnswer.NO }
                    recognition.onAwait {
                        when (it) {
                            is Recognition.Text -> parseVoiceAnswer(it.text)
                            Recognition.NoSpeech -> VoiceAnswer.NO_RESPONSE
                            Recognition.Unavailable -> VoiceAnswer.UNAVAILABLE
                        }
                    }
                }
            } finally {
                recognition.cancel()
                engine.stop()
            }
        }
    }
}
