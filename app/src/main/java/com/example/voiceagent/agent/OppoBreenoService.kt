package com.example.voiceagent.agent

import android.content.Context
import com.example.voiceagent.util.Config

interface OppoBreenoService {
    suspend fun recognizeSpeech(): String?
    suspend fun understand(text: String): String
    suspend fun speak(text: String)
}

class OppoBreenoServiceImpl(private val ctx: Context) : OppoBreenoService {
    override suspend fun recognizeSpeech(): String? = null
    override suspend fun understand(text: String): String = "{}"
    override suspend fun speak(text: String) {}
}

object BreenoFactory {
    fun get(ctx: Context): OppoBreenoService? =
        if (Config.useOppoBreeno) OppoBreenoServiceImpl(ctx) else null
}
