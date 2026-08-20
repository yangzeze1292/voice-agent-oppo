package com.example.voiceagent.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object ServiceBridge {
    val events: SharedFlow<String> get() = _events
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 64)

    suspend fun emit(msg: String) = _events.emit(msg)
    fun tryEmit(msg: String) = _events.tryEmit(msg)
}
