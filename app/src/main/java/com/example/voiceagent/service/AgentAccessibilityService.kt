package com.example.voiceagent.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import java.util.concurrent.Executor
import android.view.accessibility.AccessibilityNodeInfo
import com.example.voiceagent.agent.AgentAction
import com.example.voiceagent.agent.Prompts
import com.example.voiceagent.util.Config
import com.example.voiceagent.util.ScreenCapture
import com.example.voiceagent.util.ServiceBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

class AgentAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AgentAccessibilityService? = null
            private set
        private const val TAP_DURATION_MS = 50L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ServiceBridge.tryEmit("[无障碍] 服务已连接")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        ServiceBridge.tryEmit("[无障碍] 服务已断开")
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun screenSize(): Pair<Int, Int> {
        val wm = getSystemService(WINDOW_SERVICE) as? WindowManager
        return try {
            val b = wm?.maximumWindowMetrics?.bounds
            if (b != null && b.width() > 0) b.width() to b.height()
            else resources.displayMetrics.widthPixels to resources.displayMetrics.heightPixels
        } catch (t: Throwable) {
            resources.displayMetrics.widthPixels to resources.displayMetrics.heightPixels
        }
    }

    fun getActivePackage(): String? = try {
        rootInActiveWindow?.packageName?.toString()
    } catch (t: Throwable) { null }

    fun clickByText(text: String): Boolean {
        val query = text.trim()
        if (query.isBlank()) return false
        val root = rootInActiveWindow ?: return false
        val node = findTextOrDescMatch(root, query) ?: return false
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable && !target.isCheckable) target = target.parent
        return target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    fun clickByCoordinate(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    fun longPress(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 800)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    fun swipe(sx: Float, sy: Float, ex: Float, ey: Float, durationMs: Long = 300): Boolean {
        val path = Path().apply { moveTo(sx, sy); lineTo(ex, ey) }
        val dur = durationMs.coerceIn(10L, 5000L)
        val stroke = GestureDescription.StrokeDescription(path, 0, dur)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    fun swipeByDirection(dir: AgentAction.Swipe.Dir, durationMs: Long = 300): Boolean {
        val (w, h) = screenSize()
        val cx = w / 2f
        val cy = h / 2f
        val dx = w * 0.3f
        val dy = h * 0.3f
        return when (dir) {
            AgentAction.Swipe.Dir.UP -> swipe(cx, cy + dy, cx, cy - dy, durationMs)
            AgentAction.Swipe.Dir.DOWN -> swipe(cx, cy - dy, cx, cy + dy, durationMs)
            AgentAction.Swipe.Dir.LEFT -> swipe(cx + dx, cy, cx - dx, cy, durationMs)
            AgentAction.Swipe.Dir.RIGHT -> swipe(cx - dx, cy, cx + dx, cy, durationMs)
        }
    }

    fun scroll(dir: AgentAction.Scroll.Dir): Boolean {
        val node = findBestScrollable() ?: return swipeByDirection(toSwipeDir(dir))
        val action = if (dir == AgentAction.Scroll.Dir.UP || dir == AgentAction.Scroll.Dir.LEFT) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        return node.performAction(action)
    }

    suspend fun scrollToElement(text: String, maxScrolls: Int = 8): Boolean {
        if (dumpUiTree().contains(text)) return true
        repeat(maxScrolls) {
            if (!scroll(AgentAction.Scroll.Dir.UP)) return false
            delay(500)
            if (dumpUiTree().contains(text)) return true
        }
        return false
    }

    fun gridTap(index: Int): Boolean {
        val cols = Prompts.GRID_COLS
        val rows = Prompts.GRID_ROWS
        if (index !in 0 until cols * rows) return false
        val (w, h) = screenSize()
        val row = index / cols
        val col = index % cols
        return clickByCoordinate((col + 0.5f) * w / cols, (row + 0.5f) * h / rows)
    }

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val edit = findFocusedEditText(root) ?: findAnyEditText(root) ?: return false
        if (!edit.isFocused) edit.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun openApp(packageName: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(intent)
            true
        } catch (t: Throwable) { false }
    }

    fun pressBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun pressRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)

    suspend fun waitForElement(text: String, timeoutMs: Long = 3000): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (dumpUiTree().contains(text)) return true
            delay(300)
        }
        return false
    }

    private var nodeCount = 0

    fun dumpUiTree(): String {
        val root = rootInActiveWindow ?: return "[]"
        nodeCount = 0
        val out = JSONArray()
        dumpNode(root, out, 0)
        return out.toString()
    }

    private fun dumpNode(node: AccessibilityNodeInfo, out: JSONArray, depth: Int): Boolean {
        if (nodeCount >= Config.TREE_MAX_NODES || depth > Config.TREE_MAX_DEPTH) return false
        val keep = shouldKeep(node)
        val children = JSONArray()
        var anyChild = false
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (dumpNode(child, children, depth + 1)) anyChild = true
        }
        if (!keep && !anyChild) return false
        if (keep && nodeCount < Config.TREE_MAX_NODES) {
            val j = JSONObject()
            j.put("type", node.className?.toString()?.substringAfterLast('.') ?: "")
            j.put("text", (node.text?.toString() ?: "").take(Config.TREE_TEXT_MAX))
            j.put("desc", (node.contentDescription?.toString() ?: "").take(Config.TREE_TEXT_MAX))
            j.put("id", node.viewIdResourceName?.substringAfterLast('/') ?: "")
            val b = Rect().also { node.getBoundsInScreen(it) }
            j.put("bounds", JSONArray().put(b.left).put(b.top).put(b.right).put(b.bottom))
            j.put("clickable", node.isClickable)
            j.put("scrollable", node.isScrollable)
            j.put("editable", node.isEditable)
            j.put("checked", node.isChecked)
            j.put("focused", node.isFocused)
            j.put("secure", isSensitiveNode(node))
            if (anyChild) j.put("children", children)
            out.put(j)
            nodeCount++
        } else {
            for (i in 0 until children.length()) out.put(children.get(i))
        }
        return true
    }

    private fun findTextOrDescMatch(root: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
        var fallback: AccessibilityNodeInfo? = null
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeFirst()
            if (node.isVisibleToUser) {
                val nodeText = node.text?.toString().orEmpty()
                val desc = node.contentDescription?.toString().orEmpty()
                if (nodeText == query || desc == query) return node
                if (fallback == null && (nodeText.contains(query) || desc.contains(query))) {
                    fallback = node
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.addLast(it) }
            }
        }
        return fallback
    }

    private fun shouldKeep(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false
        return node.isClickable || node.isScrollable || node.isEditable || node.isCheckable ||
            !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank() || isSensitiveNode(node)
    }

    fun screenSummary(textLimit: Int = 40): String {
        val pkg = getActivePackage() ?: "未知"
        val texts = visibleTexts(textLimit)
        return "当前应用: " + pkg + "\n可见元素文字: " +
            (if (texts.isEmpty()) "（无）" else texts.joinToString("、"))
    }

    private fun visibleTexts(limit: Int): List<String> {
        val out = mutableListOf<String>()
        val root = rootInActiveWindow ?: return out
        collectTexts(root, out, limit)
        return out
    }

    private fun collectTexts(node: AccessibilityNodeInfo, out: MutableList<String>, limit: Int) {
        if (out.size >= limit) return
        val t = node.text?.toString()
        if (!t.isNullOrBlank() && t.length <= 20) out.add(t)
        val d = node.contentDescription?.toString()
        if (out.size < limit && !d.isNullOrBlank() && d.length <= 20) out.add(d)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectTexts(it, out, limit) }
        }
    }

    suspend fun takeScreenshotSafe(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return ScreenCapture.instance?.lastFrame()
        }
        val deferred = CompletableDeferred<Bitmap?>()
        val callback = object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                try {
                    val bmp = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                    val copy = bmp?.copy(Bitmap.Config.ARGB_8888, false)
                    bmp?.recycle()
                    screenshot.hardwareBuffer.close()
                    if (copy != null && isAllBlack(copy)) {
                        ServiceBridge.tryEmit("[截图] 检测到黑屏（安全窗口），降级为纯 UI 树模式")
                        deferred.complete(null)
                    } else {
                        deferred.complete(copy)
                    }
                } catch (t: Throwable) {
                    ServiceBridge.tryEmit("[截图] 处理失败：" + t.message)
                    deferred.complete(ScreenCapture.instance?.lastFrame())
                }
            }

            override fun onFailure(errorCode: Int) {
                if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW) {
                    ServiceBridge.tryEmit("[截图] 安全窗口（flagSecure），降级为纯 UI 树模式")
                    deferred.complete(null)
                } else {
                    val fallback = ScreenCapture.instance?.lastFrame()
                    if (fallback != null) {
                        ServiceBridge.tryEmit("[截图] 无障碍截图失败(code=" + errorCode + ")，使用录屏兜底")
                        deferred.complete(fallback.copy(Bitmap.Config.ARGB_8888, false))
                    } else {
                        ServiceBridge.tryEmit("[截图] 失败 code=" + errorCode + "，且无录屏兜底，使用纯 UI 树模式")
                        deferred.complete(null)
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, callback)
        } else {
            try {
                val method = AccessibilityService::class.java.getMethod(
                    "takeScreenshot",
                    Executor::class.java,
                    AccessibilityService.TakeScreenshotCallback::class.java
                )
                method.invoke(this, mainExecutor, callback)
            } catch (t: Throwable) {
                ServiceBridge.tryEmit("[截图] 旧系统截图调用失败：" + t.message)
                deferred.complete(ScreenCapture.instance?.lastFrame())
            }
        }
        return deferred.await()
    }

    private fun isAllBlack(bmp: Bitmap, sampleCount: Int = 50): Boolean {
        var dark = 0
        val w = bmp.width
        val h = bmp.height
        if (w <= 0 || h <= 0) return true
        for (i in 0 until sampleCount) {
            val x = (i * 7) % w
            val y = (i * 13) % h
            val p = bmp.getPixel(x, y)
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (r + g + b < 30) dark++
        }
        return dark > sampleCount * 9 / 10
    }

    fun maskSensitive(bmp: Bitmap): Bitmap {
        if (!Config.maskSensitiveNodes) return bmp
        val rects = sensitiveBounds()
        if (rects.isEmpty()) return bmp
        val canvas = Canvas(bmp)
        val paint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        for (r in rects) {
            val left = r.left.coerceIn(0, bmp.width)
            val top = r.top.coerceIn(0, bmp.height)
            val right = r.right.coerceIn(0, bmp.width)
            val bottom = r.bottom.coerceIn(0, bmp.height)
            if (right > left && bottom > top) {
                canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint)
            }
        }
        return bmp
    }

    private fun isSensitiveNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isPassword) return true
        val cls = node.className?.toString() ?: ""
        return cls.contains("Password", true) || cls.contains("Secure", true)
    }

    private fun sensitiveBounds(): List<Rect> {
        val out = mutableListOf<Rect>()
        val root = rootInActiveWindow ?: return out
        collectSensitive(root, out)
        return out
    }

    private fun collectSensitive(node: AccessibilityNodeInfo, out: MutableList<Rect>) {
        if (isSensitiveNode(node)) {
            val r = Rect().also { node.getBoundsInScreen(it) }
            if (!r.isEmpty) out.add(r)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectSensitive(it, out) }
        }
    }

    private fun findFocusedEditText(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        node ?: return null
        if (node.isFocused && node.isEditable) return node
        for (i in 0 until node.childCount) {
            findFocusedEditText(node.getChild(i))?.let { return it }
        }
        return null
    }

    private fun findAnyEditText(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        node ?: return null
        if (node.isEditable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            findAnyEditText(node.getChild(i))?.let { return it }
        }
        return null
    }

    private fun findBestScrollable(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0L
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            if (n.isScrollable && n.isVisibleToUser) {
                val r = Rect().also { n.getBoundsInScreen(it) }
                val area = r.width().toLong() * r.height()
                if (area > bestArea) {
                    bestArea = area
                    best = n
                }
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { stack.addLast(it) }
            }
        }
        return best
    }

    private fun toSwipeDir(dir: AgentAction.Scroll.Dir): AgentAction.Swipe.Dir = when (dir) {
        AgentAction.Scroll.Dir.UP -> AgentAction.Swipe.Dir.UP
        AgentAction.Scroll.Dir.DOWN -> AgentAction.Swipe.Dir.DOWN
        AgentAction.Scroll.Dir.LEFT -> AgentAction.Swipe.Dir.LEFT
        AgentAction.Scroll.Dir.RIGHT -> AgentAction.Swipe.Dir.RIGHT
    }
}
