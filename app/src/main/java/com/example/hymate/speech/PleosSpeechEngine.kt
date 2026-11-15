package com.example.hymate.speech

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitializeListener
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitializeListener
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 차량 Speech Service의 ON_DEVICE TTS/STT.
 * SDK 콜백에 요청 ID가 없으므로 작업별 연결로 늦게 도착한 콜백을 다음 발화와 분리해요.
 * 앱은 직접 녹음하거나 오디오 파일을 저장하지 않아요.
 */
class PleosSpeechEngine(context: Context) : SpeechEngine {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var owner: Any? = null
    private var cancelCurrent: (() -> Unit)? = null
    private val operationMutex = Mutex()

    private suspend fun <T> operation(block: suspend () -> T): T = withContext(Dispatchers.Main.immediate) {
        stop()
        operationMutex.withLock { block() }
    }

    override suspend fun speak(text: String): Boolean = operation {
        if (text.isBlank() || !hasPermission(TTS_PERMISSION)) return@operation false
        val token = Any()
        var client: TextToSpeech? = null
        var listener: EventListener? = null
        val playbackEnded = CompletableDeferred<Unit>()
        try {
            withTimeoutOrNull(30_000L) {
                suspendCancellableCoroutine { continuation ->
                    owner = token
                    cancelCurrent = { continuation.cancel() }
                    fun finish(success: Boolean) = deliver(token) {
                        if (continuation.isActive) continuation.resume(success)
                    }
                    try {
                        val tts = TextToSpeech(appContext, TtsMode.ON_DEVICE)
                        client = tts
                        val events = object : EventListener {
                            override fun onReady() = Unit
                            override fun onStart() { Log.d(TAG, "TTS 재생 시작") }
                            override fun onUpdatedRms(rms: Double) = Unit
                            override fun onDone() {
                                Log.d(TAG, "TTS 재생 완료")
                                playbackEnded.complete(Unit)
                                finish(true)
                            }
                            override fun onStop() {
                                playbackEnded.complete(Unit)
                                finish(false)
                            }
                            override fun onError(errorType: TtsError) {
                                Log.w(TAG, "TTS 재생 실패: $errorType")
                                finish(false)
                            }
                        }
                        listener = events
                        tts.initialize(object : TtsInitializeListener {
                            override fun onSuccess() = deliver(token) {
                                try {
                                    tts.addEventListener(events)
                                    tts.setLocale(Locale.KOREA)
                                    tts.speak(text)
                                } catch (e: Exception) {
                                    Log.w(TAG, "TTS 요청 실패", e)
                                    finish(false)
                                }
                            }
                            override fun onFailure(errorType: TtsError) {
                                Log.w(TAG, "TTS 초기화 실패: $errorType")
                                finish(false)
                            }
                        })
                    } catch (e: Exception) {
                        Log.w(TAG, "TTS 연결 실패", e)
                        finish(false)
                    }
                }
            } ?: false
        } finally {
            clearOwner(token)
            withContext(NonCancellable) {
                if (!playbackEnded.isCompleted && client != null) {
                    safely { client?.stop() }
                    withTimeoutOrNull(1000L) { playbackEnded.await() }
                }
                listener?.let { events -> safely { client?.removeEventListener(events) } }
                safely { client?.release() }
            }
        }
    }

    override suspend fun listen(onStarted: () -> Unit): Recognition = operation {
        if (!hasPermission(STT_PERMISSION)) return@operation Recognition.Unavailable
        val token = Any()
        var client: SpeechToText? = null
        var listener: ResultListener? = null
        val recognitionEnded = CompletableDeferred<Unit>()
        var silenceTimeout: Runnable? = null
        try {
            // 초기화가 지연돼도 영원히 마이크 연결 대기에 머무르지 않아요.
            withTimeoutOrNull<Recognition>(30_000L) {
                suspendCancellableCoroutine<Recognition> { continuation ->
                    owner = token
                    cancelCurrent = { continuation.cancel() }
                    fun finish(result: Recognition) = deliver(token) {
                        if (continuation.isActive) continuation.resume(result)
                    }
                    silenceTimeout = Runnable { finish(Recognition.Unavailable) }.also {
                        main.postDelayed(it, 12_000L)
                    }
                    try {
                        val stt = SpeechToText(appContext, SttMode.ON_DEVICE)
                        client = stt
                        var listening = false
                        fun beginListening() = deliver(token) {
                            if (!listening && continuation.isActive) {
                                listening = true
                                Log.d(TAG, "STT 응답 대기 시작")
                                onStarted()
                                silenceTimeout?.let { main.removeCallbacks(it) }
                                silenceTimeout = Runnable { finish(Recognition.NoSpeech) }.also {
                                    main.postDelayed(it, 6_000L)
                                }
                            }
                        }
                        val results = object : ResultListener {
                            // 준비 완료 후 사용자가 말할 수 있음을 표시해요. 발화 시작으로 타이머를 재설정하지 않아요.
                            override fun onReady() = beginListening()
                            override fun onStartedRecognition() = beginListening()
                            override fun onUpdated(stt: String, completed: Boolean) {
                                // 중간 인식으로 동의를 확정하지 않아요.
                                if (completed) deliver(token) {
                                    Log.d(TAG, "STT 최종 응답 수신")
                                    finish(Recognition.Text(stt))
                                }
                            }
                            override fun onUpdatedEpdData(on: Long, off: Long) = Unit
                            override fun onUpdatedRms(rms: Double) = Unit
                            // 종료 알림이 최종 텍스트보다 먼저 올 수 있어 최종 결과/타임아웃까지 기다려요.
                            override fun onEndedRecognition() {
                                Log.d(TAG, "STT 인식 종료")
                                recognitionEnded.complete(Unit)
                            }
                            override fun onError(errorType: SttError) {
                                Log.w(TAG, "STT 인식 실패: $errorType")
                                finish(Recognition.Unavailable)
                            }
                        }
                        listener = results
                        stt.initialize(object : SttInitializeListener {
                            override fun onSuccess() = deliver(token) {
                                try {
                                    stt.addListener(results)
                                    stt.setLocale(Locale.KOREA)
                                    stt.request()
                                } catch (e: Exception) {
                                    Log.w(TAG, "STT 요청 실패", e)
                                    finish(Recognition.Unavailable)
                                }
                            }
                            override fun onFailure(errorType: SttError) {
                                Log.w(TAG, "STT 초기화 실패: $errorType")
                                finish(Recognition.Unavailable)
                            }
                        })
                    } catch (e: Exception) {
                        Log.w(TAG, "STT 연결 실패", e)
                        finish(Recognition.Unavailable)
                    }
                }
            } ?: Recognition.Unavailable
        } finally {
            clearOwner(token)
            silenceTimeout?.let { main.removeCallbacks(it) }
            withContext(NonCancellable) {
                if (!recognitionEnded.isCompleted && client != null) {
                    safely { client?.stop() }
                    withTimeoutOrNull(1000L) { recognitionEnded.await() }
                }
                listener?.let { results -> safely { client?.removeListener(results) } }
                safely { client?.release() }
            }
        }
    }

    override fun stop() = onMain {
        val cancel = cancelCurrent
        if (cancel != null) Log.d(TAG, "음성 작업 중단")
        owner = null
        cancelCurrent = null
        cancel?.invoke()
    }

    private fun clearOwner(token: Any) {
        if (owner === token) {
            owner = null
            cancelCurrent = null
        }
    }

    private fun deliver(token: Any, block: () -> Unit) = onMain {
        if (owner === token) block()
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() }
    }

    private fun hasPermission(permission: String): Boolean {
        val granted = ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
        if (!granted) Log.w(TAG, "음성 서비스 권한 없음: $permission")
        return granted
    }

    private inline fun safely(block: () -> Unit) {
        try { block() } catch (e: Exception) { Log.w(TAG, "음성 연결 해제 실패", e) }
    }

    private companion object {
        const val TAG = "HySpeech"
        const val TTS_PERMISSION = "pleos.car.permission.TTS_SERVICE"
        const val STT_PERMISSION = "pleos.car.permission.STT_SERVICE"
    }
}
