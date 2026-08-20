package com.example.voiceagent.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.voiceagent.util.ServiceBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

class SpeechService(context: Context) {

    private val recognizer: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null

    private var pending: CompletableDeferred<String?>? = null

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) pending?.complete(text)
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            pending?.complete(text ?: "")
        }

        override fun onError(error: Int) {
            val msg = errorMessage(error)
            if (msg.isNotBlank()) ServiceBridge.tryEmit("[语音] " + msg)
            pending?.complete("")
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    init {
        recognizer?.setRecognitionListener(listener)
    }

    suspend fun listenOnce(timeoutMs: Long = 8_000): String? {
        val rec = recognizer ?: run {
            ServiceBridge.tryEmit("[语音] 系统语音识别不可用（ColorOS 常见），请接入讯飞或小布 SDK")
            return null
        }
        val d = CompletableDeferred<String?>()
        pending = d
        return try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            rec.startListening(intent)
            withTimeoutOrNull(timeoutMs) { d.await() }
        } catch (t: Throwable) {
            ServiceBridge.tryEmit("[语音] 识别异常：" + t.message)
            null
        } finally {
            pending = null
            runCatching { rec.stopListening() }
        }
    }

    fun destroy() {
        runCatching { recognizer?.destroy() }
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "没有听清，请重试"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "说话超时"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙"
        SpeechRecognizer.ERROR_CLIENT -> "客户端错误（可能是 Google 语音服务缺失）"
        SpeechRecognizer.ERROR_NETWORK -> "网络错误"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少录音权限"
        else -> "识别错误 code=" + code
    }
}
