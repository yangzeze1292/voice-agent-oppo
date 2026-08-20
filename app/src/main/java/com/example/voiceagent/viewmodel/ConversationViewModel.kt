package com.example.voiceagent.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.voiceagent.agent.AgentLoop
import com.example.voiceagent.agent.BreenoFactory
import com.example.voiceagent.agent.LlmService
import com.example.voiceagent.agent.OppoBreenoService
import com.example.voiceagent.service.AgentAccessibilityService
import com.example.voiceagent.service.SpeechService
import com.example.voiceagent.util.ServiceBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatMessage(val role: String, val text: String)

class ConversationViewModel(app: Application) : AndroidViewModel(app) {

    private val speech = SpeechService(app)
    private val llm = LlmService()
    private val breeno: OppoBreenoService? = BreenoFactory.get(app)
    private val agentLoop = AgentLoop(llm)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _needAsk = MutableStateFlow<String?>(null)
    val needAsk: StateFlow<String?> = _needAsk

    private var runJob: Job? = null
    private var pendingDeferred: CompletableDeferred<Boolean>? = null

    init {
        viewModelScope.launch {
            ServiceBridge.events.collect { msg -> addMessage("agent", msg) }
        }
    }

    fun startVoiceCommand() {
        if (_listening.value || _running.value) return
        _listening.value = true
        addMessage("system", "请说话…")
        viewModelScope.launch {
            var cmd: String? = null
            if (breeno != null) {
                cmd = try { breeno.recognizeSpeech() } catch (t: Throwable) { null }
                if (cmd.isNullOrBlank()) {
                    addMessage("system", "小布 SDK 未接入，使用系统语音识别")
                }
            }
            val text = cmd ?: speech.listenOnce(8_000)
            _listening.value = false
            if (text.isNullOrBlank()) {
                addMessage("system", "未识别到语音")
                return@launch
            }
            addMessage("user", text)
            executeCommand(text)
        }
    }

    fun sendTextCommand(text: String) {
        if (text.isBlank() || _running.value) return
        addMessage("user", text)
        executeCommand(text)
    }

    fun answerAsk(allow: Boolean) {
        val d = pendingDeferred
        pendingDeferred = null
        _needAsk.value = null
        d?.complete(allow)
    }

    fun stopAgent() {
        agentLoop.requestStop()
        runJob?.cancel()
        runJob = null
        _running.value = false
        _needAsk.value = null
        pendingDeferred = null
        addMessage("system", "已停止")
    }

    private fun executeCommand(command: String) {
        if (AgentAccessibilityService.instance == null) {
            addMessage("system", "请先在设置中开启无障碍服务")
            return
        }
        addMessage("agent", "开始执行：" + command)
        _running.value = true
        runJob = viewModelScope.launch {
            try {
                agentLoop.run(command) { question ->
                    val d = CompletableDeferred<Boolean>()
                    pendingDeferred = d
                    _needAsk.value = question
                    d.await()
                }
            } finally {
                _running.value = false
                runJob = null
            }
        }
    }

    private fun addMessage(role: String, text: String) {
        _messages.value = _messages.value + ChatMessage(role, text)
    }

    override fun onCleared() {
        agentLoop.requestStop()
        runJob?.cancel()
        speech.destroy()
        super.onCleared()
    }
}
