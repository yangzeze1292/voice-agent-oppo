package com.example.voiceagent.agent

import org.json.JSONObject

/**
 * 把 LLM 返回的文本解析为 AgentAction。
 * 容错策略：
 * 1. 提取首个 {...} 段（允许 LLM 输出前后夹带文字）；
 * 2. 兼容两种字段写法：{"text": ...} 与 {"target": {"text": ...}}；
 * 3. 解析失败返回 Fail(原因)，便于上层诊断而不是静默失败。
 */
object ActionParser {

    fun parse(raw: String): AgentAction {
        if (raw.isBlank()) return AgentAction.Fail("LLM 返回为空")
        return try {
            val j = JSONObject(extractJson(raw))
            val action = j.optString("action", "").lowercase().trim()
            val reasoning = j.optString("reasoning", "")
            val expectation = j.optString("expectation", "")
            when (action) {
                "click" -> {
                    val text = optTargetText(j)
                    if (text.isBlank()) AgentAction.Fail("click 缺少 text")
                    else AgentAction.Click(text, reasoning, expectation)
                }
                "click_at" -> {
                    val (x, y) = optCoords(j)
                    if (x == null || y == null) AgentAction.Fail("click_at 缺少坐标")
                    else AgentAction.ClickAt(x, y, reasoning, expectation)
                }
                "long_press" -> {
                    val (x, y) = optCoords(j)
                    if (x == null || y == null) AgentAction.Fail("long_press 缺少坐标")
                    else AgentAction.LongPress(x, y, reasoning, expectation)
                }
                "swipe" -> AgentAction.Swipe(parseSwipeDir(j) ?: AgentAction.Swipe.Dir.UP, reasoning, expectation)
                "scroll" -> AgentAction.Scroll(parseScrollDir(j) ?: AgentAction.Scroll.Dir.UP, reasoning, expectation)
                "scroll_to_element" -> AgentAction.ScrollToElement(optTargetText(j), reasoning, expectation)
                "grid_tap" -> {
                    val idx = j.optInt("index", j.optInt("grid_index", -1))
                    if (idx < 0) AgentAction.Fail("grid_tap 缺少 index")
                    else AgentAction.GridTap(idx, reasoning, expectation)
                }
                "type" -> {
                    val text = j.optString("text", "")
                    if (text.isBlank()) AgentAction.Fail("type 缺少 text")
                    else AgentAction.Type(text, reasoning, expectation)
                }
                "back" -> AgentAction.Back
                "home" -> AgentAction.Home
                "open_app" -> {
                    val pkg = j.optString("pkg", "").ifBlank {
                        j.optString("package", "").ifBlank { j.optString("package_name", "") }
                    }
                    if (pkg.isBlank()) AgentAction.Fail("open_app 缺少包名")
                    else AgentAction.OpenApp(pkg, reasoning, expectation)
                }
                "wait" -> AgentAction.Wait(j.optLong("ms", 500).coerceIn(0, 5000), reasoning, expectation)
                "wait_for_element" -> AgentAction.WaitForElement(optTargetText(j), reasoning, expectation)
                "done" -> AgentAction.Done
                "fail" -> AgentAction.Fail(j.optString("reason", "无法完成"), reasoning, expectation)
                "ask_user" -> AgentAction.AskUser(j.optString("question", "请确认"), reasoning, expectation)
                else -> AgentAction.Fail("未知动作: " + action)
            }
        } catch (e: Exception) {
            AgentAction.Fail("解析失败: " + e.message)
        }
    }

    /** 兼容 {"text":...} 与 {"target":{"text":...}} 两种写法 */
    private fun optTargetText(j: JSONObject): String {
        val direct = j.optString("text", "")
        if (direct.isNotBlank()) return direct
        val target = j.optJSONObject("target") ?: return ""
        return target.optString("text", "")
    }

    /** 坐标：优先 x/y 字段，其次 target.bounds 中心点 */
    private fun optCoords(j: JSONObject): Pair<Float?, Float?> {
        var x = optFloat(j, "x")
        var y = optFloat(j, "y")
        if (x != null && y != null) return x to y
        val target = j.optJSONObject("target") ?: return x to y
        val bounds = target.optJSONArray("bounds")
        if (bounds != null && bounds.length() >= 4) {
            x = bounds.optDouble(0).toFloat()
            y = bounds.optDouble(1).toFloat()
            val x2 = bounds.optDouble(2).toFloat()
            val y2 = bounds.optDouble(3).toFloat()
            return ((x + x2) / 2f) to ((y + y2) / 2f)
        }
        return null to null
    }

    private fun optFloat(j: JSONObject, key: String): Float? {
        if (!j.has(key)) return null
        return try { j.optDouble(key).toFloat() } catch (e: Exception) { null }
    }

    private fun parseDirRaw(j: JSONObject): String =
        j.optString("dir", j.optString("direction", "")).uppercase().trim()

    private fun parseSwipeDir(j: JSONObject): AgentAction.Swipe.Dir? =
        try { AgentAction.Swipe.Dir.valueOf(parseDirRaw(j)) } catch (e: Exception) { null }

    private fun parseScrollDir(j: JSONObject): AgentAction.Scroll.Dir? =
        try { AgentAction.Scroll.Dir.valueOf(parseDirRaw(j)) } catch (e: Exception) { null }

    private fun extractJson(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start >= 0 && end > start) raw.substring(start, end + 1) else "{}"
    }
}
