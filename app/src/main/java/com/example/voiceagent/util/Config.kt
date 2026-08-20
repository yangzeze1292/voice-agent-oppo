package com.example.voiceagent.util

import com.example.voiceagent.BuildConfig

object Config {
    var llmApiKey: String = BuildConfig.LLM_API_KEY
    var llmBaseUrl: String = "https://api.deepseek.com/v1/chat/completions"
    var llmModel: String = "deepseek-v4-pro"
    var llmTemperature: Double = 0.2
    var llmExecutorModel: String = ""
    var llmMaxTokens: Int = 2048
    var llmConnectTimeoutSec: Long = 15
    var llmReadTimeoutSec: Long = 120
    var llmMaxRetries: Int = 2
    var llmLogUsage: Boolean = true

    var llmVisionEnabled: Boolean = true
    var llmVisionApiKey: String = BuildConfig.LLM_VISION_API_KEY
    var llmVisionBaseUrl: String = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
    var llmVisionModel: String = "glm-5v-turbo"

    var useOppoBreeno: Boolean = false

    const val MAX_STEPS = 15
    const val STEP_DELAY_MS = 600L
    const val LOOP_DETECT_REPEAT = 3
    const val MAX_REPLANS = 2
    const val MEMORY_STEPS = 6
    const val VERIFY_DELAY_MS = 700L

    const val TREE_MAX_DEPTH = 16
    const val TREE_MAX_NODES = 120
    const val TREE_TEXT_MAX = 40

    const val SHOT_MAX_WIDTH = 720
    const val SHOT_JPEG_QUALITY = 60
    var maskSensitiveNodes: Boolean = true

    var blockOpeningSensitiveApps: Boolean = false
}
