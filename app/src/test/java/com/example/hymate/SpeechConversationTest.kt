package com.example.hymate

import com.example.hymate.speech.Recognition
import com.example.hymate.speech.SpeechConversation
import com.example.hymate.speech.SpeechEngine
import com.example.hymate.speech.VoiceAnswer
import com.example.hymate.speech.parseVoiceAnswer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class SpeechConversationTest {
    private class FakeEngine : SpeechEngine {
        var playback = CompletableDeferred(true)
        var recognition = CompletableDeferred<Recognition>(Recognition.Text("네"))
        val started = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        override suspend fun speak(text: String): Boolean {
            calls += "speak"
            return playback.await()
        }
        override suspend fun listen(onStarted: () -> Unit): Recognition {
            calls += "listen"
            onStarted()
            started.complete(Unit)
            return recognition.await()
        }
        override fun stop() { calls += "stop" }
    }

    @Test fun acceptsOnlyExplicitYesOrNo() {
        listOf("응", "네!", " 예 ", "포함해 주세요.", "YES", "네, 넣어주세요.").forEach {
            assertEquals(it, VoiceAnswer.YES, parseVoiceAnswer(it))
        }
        listOf("아니", "아니요.", "넣지 마세요", "동의하지 않아요", "NO").forEach {
            assertEquals(it, VoiceAnswer.NO, parseVoiceAnswer(it))
        }
    }

    @Test fun ambiguousOrNegatedSentencesNeverBecomeConsent() {
        listOf("", "음...", "응 아니", "네가 알아서 해", "추가할지 모르겠어", "네라고 하지 않았어요").forEach {
            assertEquals(it, VoiceAnswer.NO_RESPONSE, parseVoiceAnswer(it))
        }
    }

    @Test fun doesNotOpenMicrophoneUntilPlaybackCompletes() = runBlocking {
        val engine = FakeEngine().apply { playback = CompletableDeferred() }
        val answer = async(start = CoroutineStart.UNDISPATCHED) {
            SpeechConversation(engine).ask("질문", CompletableDeferred()) {}
        }
        assertEquals(listOf("speak"), engine.calls)
        engine.playback.complete(true)
        assertEquals(VoiceAnswer.YES, withTimeout(1000) { answer.await() })
        assertEquals(listOf("speak", "listen", "stop"), engine.calls)
    }

    @Test fun failedPlaybackDoesNotStartRecognition() = runBlocking {
        val engine = FakeEngine().apply { playback = CompletableDeferred(false) }
        val result = SpeechConversation(engine).ask("질문", CompletableDeferred()) {}
        assertEquals(VoiceAnswer.UNAVAILABLE, result)
        assertEquals(listOf("speak"), engine.calls)
    }

    @Test fun recognizedNegativeAnswerIsNotConsent() = runBlocking {
        val engine = FakeEngine().apply { recognition = CompletableDeferred(Recognition.Text("아니요")) }
        assertEquals(VoiceAnswer.NO, SpeechConversation(engine).ask("질문", CompletableDeferred()) {})
    }

    @Test fun unrecognizedFinalResultDoesNotDefaultToYes() = runBlocking {
        val engine = FakeEngine().apply { recognition = CompletableDeferred(Recognition.Text("잘 모르겠어요")) }
        assertEquals(VoiceAnswer.NO_RESPONSE, SpeechConversation(engine).ask("질문", CompletableDeferred()) {})
    }

    @Test fun silenceDoesNotDefaultToYes() = runBlocking {
        val engine = FakeEngine().apply { recognition = CompletableDeferred(Recognition.NoSpeech) }
        assertEquals(VoiceAnswer.NO_RESPONSE, SpeechConversation(engine).ask("질문", CompletableDeferred()) {})
    }

    @Test fun recognitionServiceFailureIsReportedSeparatelyFromNo() = runBlocking {
        val engine = FakeEngine().apply { recognition = CompletableDeferred(Recognition.Unavailable) }
        assertEquals(VoiceAnswer.UNAVAILABLE, SpeechConversation(engine).ask("질문", CompletableDeferred()) {})
    }

    @Test fun manualAnswerStopsTheMicrophoneWithoutWaitingForRecognition() = runBlocking {
        val engine = FakeEngine().apply { recognition = CompletableDeferred() }
        val manual = CompletableDeferred<Boolean>()
        val result = async {
            SpeechConversation(engine).ask("질문", manual) {}
        }
        withTimeout(1000) { engine.started.await() }
        manual.complete(false)
        assertEquals(VoiceAnswer.NO, withTimeout(1000) { result.await() })
        assertEquals("stop", engine.calls.last())
        assertFalse(engine.recognition.isCompleted)
    }

    @Test fun cancellationDuringListeningReleasesRecognition() = runBlocking {
        val engine = FakeEngine().apply { recognition = CompletableDeferred() }
        val job = async { SpeechConversation(engine).ask("질문", CompletableDeferred()) {} }
        withTimeout(1000) { engine.started.await() }
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals("stop", engine.calls.last())
    }

    @Test fun cancellationDuringPlaybackNeverOpensTheMicrophone() = runBlocking {
        val engine = FakeEngine().apply { playback = CompletableDeferred() }
        val job = async(start = CoroutineStart.UNDISPATCHED) {
            SpeechConversation(engine).ask("질문", CompletableDeferred()) {}
        }
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertFalse("listen" in engine.calls)
    }
}
