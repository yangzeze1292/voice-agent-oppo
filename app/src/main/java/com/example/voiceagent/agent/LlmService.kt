package com.example.voiceagent.agent

import com.example.voiceagent.util.Config
import com.example.voiceagent.util.ServiceBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class LlmService(
    private val apiKey: String = Config.llmApiKey,
    private val baseUrl: String = Config.llmBaseUrl,
    private val model: String = Config.llmModel,
    private val executorModel: String = Config.llmExecutorModel.ifBlank { Config.llmModel },
    private val visionApiKey: String = Config.llmVisionApiKey,
    private val visionBaseUrl: String = Config.llmVisionBaseUrl,
    private val visionModel: String = Config.llmVisionModel
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(Config.llmConnectTimeoutSec, TimeUnit.SECONDS)
        .readTimeout(Config.llmReadTimeoutSec, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = "application/json".toMediaType()

    val ready: Boolean get() = apiKey.isNotBlank() && !apiKey.startsWith("REPLACE")

    suspend fun chat(systemPrompt: String, userPrompt: String): String {
        if (!ready) {
            ServiceBridge.tryEmit("[LLM] API Key 未配置（util/Config.kt）")
            return ""
        }
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            put(JSONObject().put("role", "user").put("content", userPrompt))
        }
        return request(baseUrl, apiKey, model, messages)
    }

    suspend fun chatExecutor(systemPrompt: String, userPrompt: String): String {
        if (!ready) {
            ServiceBridge.tryEmit("[LLM] API Key 未配置（util/Config.kt）")
            return ""
        }
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            put(JSONObject().put("role", "user").put("content", userPrompt))
        }
        return request(baseUrl, apiKey, executorModel, messages)
    }

    suspend fun chatVision(systemPrompt: String, text: String, imageBase64: String): String {
        if (visionApiKey.isBlank() || visionBaseUrl.isBlank() || visionModel.isBlank()) {
            return chatExecutor(systemPrompt, text)
        }
        val content = JSONArray().apply {
            put(JSONObject().put("type", "text").put("text", text))
            put(
                JSONObject().put("type", "image_url")
                    .put("image_url", JSONObject().put("url", "data:image/jpeg;base64," + imageBase64))
            )
        }
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            put(JSONObject().put("role", "user").put("content", content))
        }
        return request(visionBaseUrl, visionApiKey, visionModel, messages)
    }

    suspend fun plan(command: String, screenSummary: String, hint: String = ""): String {
        if (!ready) return ""
        val user = StringBuilder()
            .append("用户指令：").append(command).append("\n")
            .apply { if (hint.isNotBlank()) append("附加约束：").append(hint).append("\n") }
            .append("当前屏幕概览：\n").append(screenSummary).append("\n")
            .append("请输出执行计划。")
            .toString()
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", Prompts.MANAGER_PROMPT))
            put(JSONObject().put("role", "user").put("content", user))
        }
        return request(baseUrl, apiKey, model, messages)
    }

    private suspend fun request(
        url: String,
        key: String,
        model: String,
        messages: JSONArray
    ): String = withContext(Dispatchers.IO) {
        val bodyJson = JSONObject().apply {
            put("model", model)
            put("temperature", Config.llmTemperature)
            put("max_tokens", Config.llmMaxTokens)
            put("messages", messages)
        }.toString()

        var lastError = "无响应"
        for (attempt in 1..(Config.llmMaxRetries + 1)) {
            if (attempt > 1) {
                ServiceBridge.tryEmit("[LLM] 请求失败（" + lastError + "），第 " + attempt + " 次重试…")
                delay(1200L * (attempt - 1))
            }
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + key)
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toRequestBody(json))
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val txt = resp.body?.string().orEmpty()
                        val root = try { JSONObject(txt) } catch (e: Exception) { null }
                        if (root == null) {
                            lastError = "响应非 JSON"
                        } else {
                            val msg = root.optJSONArray("choices")
                                ?.optJSONObject(0)
                                ?.optJSONObject("message")
                            val content = msg?.optString("content").orEmpty()
                            val reasoning = msg?.optString("reasoning_content").orEmpty()
                            val finish = root.optJSONArray("choices")
                                ?.optJSONObject(0)
                                ?.optString("finish_reason")
                            if (Config.llmLogUsage) {
                                val usage = root.optJSONObject("usage")
                                val p = usage?.optInt("prompt_tokens", -1) ?: -1
                                val c = usage?.optInt("completion_tokens", -1) ?: -1
                                ServiceBridge.tryEmit("[LLM] tokens p=" + p + "/c=" + c)
                            }
                            if (content.isNotBlank()) return@withContext content
                            if (finish == "length") {
                                ServiceBridge.tryEmit("[LLM] 输出被截断（max_tokens 不足）")
                            }
                            if (reasoning.isNotBlank()) {
                                lastError = "模型仅输出推理无正文"
                            } else {
                                lastError = "响应无内容"
                            }
                        }
                    } else {
                        lastError = "HTTP " + resp.code
                    }
                }
            } catch (e: IOException) {
                lastError = e.message ?: "网络错误"
            }
            if (lastError.startsWith("HTTP 4")) break
        }
        ServiceBridge.tryEmit("[LLM] 请求失败：" + lastError)
        ""
    }
}
