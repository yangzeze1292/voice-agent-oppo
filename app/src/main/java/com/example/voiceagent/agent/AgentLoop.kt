package com.example.voiceagent.agent

import android.graphics.Bitmap
import android.util.Base64
import com.example.voiceagent.control.ActionLevel
import com.example.voiceagent.control.SensitiveActionFilter
import com.example.voiceagent.service.AgentAccessibilityService
import com.example.voiceagent.util.Config
import com.example.voiceagent.util.ServiceBridge
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import kotlin.math.abs

class AgentLoop(
    private val llm: LlmService
) {
    @Volatile
    private var cancelled = false

    private val history = mutableListOf<StepRecord>()
    private val recentSignatures = mutableListOf<String>()
    private var replans = 0

    data class StepRecord(val step: Int, val action: AgentAction, val verdict: String)

    fun requestStop() {
        cancelled = true
    }

    suspend fun run(command: String, confirm: suspend (String) -> Boolean) {
        cancelled = false
        history.clear()
        recentSignatures.clear()
        replans = 0

        val svc = AgentAccessibilityService.instance ?: run {
            ServiceBridge.tryEmit("[Agent] 无障碍服务未连接")
            return
        }

        var plan = llm.plan(command, svc.screenSummary())
        if (plan.isNotBlank()) {
            ServiceBridge.tryEmit("[Manager] 计划：\n" + plan)
        }

        var step = 0
        var replanHint = ""
        while (step < Config.MAX_STEPS && !cancelled) {
            step++
            delay(Config.STEP_DELAY_MS)
            if (cancelled) break

            ServiceBridge.tryEmit("[Agent] 第 " + step + " 步：观察屏幕…")
            val uiTree = svc.dumpUiTree()
            val shotRaw = svc.takeScreenshotSafe()
            val shot = shotRaw?.let { svc.maskSensitive(it) }

            val userPrompt = buildUserPrompt(command, plan, uiTree, shot != null, replanHint)
            replanHint = ""

            var raw = if (Config.llmVisionEnabled && shot != null) {
                llm.chatVision(Prompts.EXECUTOR_PROMPT, userPrompt, encodeImage(shot))
            } else {
                llm.chatExecutor(Prompts.EXECUTOR_PROMPT, userPrompt)
            }
            if (raw.isBlank() && Config.llmVisionEnabled && shot != null) {
                ServiceBridge.tryEmit("[Agent] 视觉模型无响应，降级纯 UI 树模式")
                raw = llm.chatExecutor(Prompts.EXECUTOR_PROMPT, userPrompt)
            }
            if (raw.isBlank()) {
                ServiceBridge.tryEmit("[Agent] LLM 无响应，稍后重试")
                continue
            }
            val action = ActionParser.parse(raw)

            val sig = signature(action)
            recentSignatures.add(sig)
            if (countRecent(sig) >= Config.LOOP_DETECT_REPEAT) {
                if (replans >= Config.MAX_REPLANS) {
                    ServiceBridge.tryEmit("[Agent] 多次重规划仍无进展，任务中止")
                    return
                }
                replans++
                ServiceBridge.tryEmit("[Agent] 检测到重复动作，请求 Manager 重新规划（第 " + replans + " 次）")
                val newPlan = llm.plan(
                    command,
                    svc.screenSummary(),
                    "刚才的动作连续重复且没有进展，请制定一条与之前不同的路径"
                )
                if (newPlan.isNotBlank()) {
                    plan = newPlan
                    ServiceBridge.tryEmit("[Manager] 新计划：\n" + newPlan)
                }
                replanHint = Prompts.REPLAN_HINT
                continue
            }

            if (action is AgentAction.AskUser) {
                val answer = confirm(action.question)
                ServiceBridge.tryEmit("[Agent] 用户回答：" + (if (answer) "是" else "否"))
                history.add(StepRecord(step, action, if (answer) "用户确认" else "用户否认"))
                continue
            }

            val decision = SensitiveActionFilter.evaluate(action, svc.getActivePackage())
            if (decision.level != ActionLevel.AUTO) {
                ServiceBridge.tryEmit("[安全] " + decision.reason)
                val allowed = confirm(decision.question)
                if (!allowed) {
                    ServiceBridge.tryEmit("[Agent] 用户拒绝，跳过该动作")
                    history.add(StepRecord(step, action, "用户拒绝"))
                    continue
                }
                ServiceBridge.tryEmit("[安全] 用户已确认放行")
            }

            ServiceBridge.tryEmit("[Agent] 第 " + step + " 步：" + action)
            val ok = execute(svc, action)
            if (!ok) ServiceBridge.tryEmit("[Agent] 动作执行返回失败")

            val verdict = verify(svc, action, shot)
            history.add(StepRecord(step, action, verdict))
            if (verdict.startsWith("✗")) ServiceBridge.tryEmit("[校验] " + verdict)

            when (action) {
                is AgentAction.Done -> {
                    ServiceBridge.tryEmit("[Agent] 任务完成")
                    return
                }
                is AgentAction.Fail -> {
                    ServiceBridge.tryEmit("[Agent] 任务失败：" + action.reason)
                    return
                }
                else -> {}
            }
        }
        ServiceBridge.tryEmit(if (cancelled) "[Agent] 已停止" else "[Agent] 超过最大步数 " + Config.MAX_STEPS)
    }

    private fun buildUserPrompt(
        command: String,
        plan: String,
        uiTree: String,
        hasScreenshot: Boolean,
        replanHint: String
    ): String {
        val sb = StringBuilder()
        sb.append("用户指令：").append(command).append("\n\n")
        if (plan.isNotBlank()) sb.append("执行计划：\n").append(plan).append("\n\n")
        if (history.isNotEmpty()) {
            sb.append("历史动作（旧→新）：\n")
            history.takeLast(Config.MEMORY_STEPS).forEach { r ->
                sb.append("- 步骤").append(r.step).append(" ").append(r.action)
                    .append(" [").append(r.verdict).append("]\n")
            }
            sb.append("\n")
        }
        if (replanHint.isNotBlank()) sb.append("注意：").append(replanHint).append("\n\n")
        sb.append("当前屏幕 UI 树（JSON）：\n").append(uiTree).append("\n\n")
        if (hasScreenshot) {
            sb.append("（屏幕截图已作为图片随本条消息发送；网格：3 列 × 5 行，编号 0-14，行优先）\n")
        } else {
            sb.append("（截图不可用：安全窗口或未授权录屏，请仅依据 UI 树决策）\n")
        }
        sb.append("\n请输出下一步动作 JSON。")
        return sb.toString()
    }

    private suspend fun verify(
        svc: AgentAccessibilityService,
        action: AgentAction,
        shotBefore: Bitmap?
    ): String {
        if (action is AgentAction.Done || action is AgentAction.Fail ||
            action is AgentAction.AskUser || action is AgentAction.Wait
        ) return "—"
        delay(Config.VERIFY_DELAY_MS)
        val treeAfter = svc.dumpUiTree()
        if (matchExpectation(action.expectation, treeAfter)) return "✓ 预期达成"
        val shotAfterRaw = svc.takeScreenshotSafe()
        val shotAfter = shotAfterRaw?.let { svc.maskSensitive(it) }
        if (shotBefore != null && shotAfter != null && !screenChanged(shotBefore, shotAfter)) {
            return "✗ 屏幕无变化"
        }
        return "? 待观察"
    }

    private fun matchExpectation(expectation: String, tree: String): Boolean {
        if (expectation.isBlank() || expectation == "—" || expectation == "-") return false
        val tokens = expectation.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }
        val bigrams = expectation.windowed(2).filter { w -> w.all { it.code > 0x2E00 } }
        return (tokens + bigrams).any { tree.contains(it) }
    }

    private fun screenChanged(a: Bitmap, b: Bitmap): Boolean {
        val n = 16
        val sa = Bitmap.createScaledBitmap(a, n, n, true)
        val sb = Bitmap.createScaledBitmap(b, n, n, true)
        var diff = 0L
        for (y in 0 until n) {
            for (x in 0 until n) {
                val p1 = sa.getPixel(x, y)
                val p2 = sb.getPixel(x, y)
                diff += abs(((p1 shr 16) and 0xFF) - ((p2 shr 16) and 0xFF))
                diff += abs(((p1 shr 8) and 0xFF) - ((p2 shr 8) and 0xFF))
                diff += abs((p1 and 0xFF) - (p2 and 0xFF))
            }
        }
        sa.recycle()
        sb.recycle()
        return diff > (n * n * 3 * 4)
    }

    private fun encodeImage(src: Bitmap): String {
        val scaled = if (src.width > Config.SHOT_MAX_WIDTH) {
            val h = src.height * Config.SHOT_MAX_WIDTH / src.width
            Bitmap.createScaledBitmap(src, Config.SHOT_MAX_WIDTH, h, true)
        } else src
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, Config.SHOT_JPEG_QUALITY, baos)
        if (scaled !== src) scaled.recycle()
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    private suspend fun execute(svc: AgentAccessibilityService, action: AgentAction): Boolean = try {
        when (action) {
            is AgentAction.Click -> svc.clickByText(action.text)
            is AgentAction.ClickAt -> svc.clickByCoordinate(action.x, action.y)
            is AgentAction.LongPress -> svc.longPress(action.x, action.y)
            is AgentAction.Swipe -> svc.swipeByDirection(action.dir)
            is AgentAction.Scroll -> svc.scroll(action.dir)
            is AgentAction.ScrollToElement -> svc.scrollToElement(action.text)
            is AgentAction.GridTap -> svc.gridTap(action.index)
            is AgentAction.Type -> svc.typeText(action.text)
            AgentAction.Back -> svc.pressBack()
            AgentAction.Home -> svc.pressHome()
            is AgentAction.OpenApp -> svc.openApp(action.packageName)
            is AgentAction.Wait -> {
                delay(action.ms.coerceIn(0, 5000))
                true
            }
            is AgentAction.WaitForElement -> svc.waitForElement(action.text)
            is AgentAction.AskUser -> true
            is AgentAction.Done, is AgentAction.Fail -> true
        }
    } catch (t: Throwable) {
        ServiceBridge.tryEmit("[Agent] 执行异常：" + t.message)
        false
    }

    private fun signature(a: AgentAction): String = when (a) {
        is AgentAction.Click -> "click:" + a.text
        is AgentAction.ClickAt -> "click:" + a.x.toInt() + "," + a.y.toInt()
        is AgentAction.GridTap -> "grid:" + a.index
        is AgentAction.OpenApp -> "open:" + a.packageName
        is AgentAction.ScrollToElement -> "scroll_to:" + a.text
        AgentAction.Back -> "back"
        AgentAction.Home -> "home"
        else -> a.toString()
    }

    private fun countRecent(sig: String): Int =
        recentSignatures.takeLast(Config.LOOP_DETECT_REPEAT).count { it == sig }
}
