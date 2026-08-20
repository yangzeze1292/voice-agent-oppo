package com.example.voiceagent.agent

/**
 * Agent 动作空间（方案文档 v2 扩展：14+ 动作 + reasoning/expectation 校验字段）。
 */
sealed class AgentAction {
    abstract val reasoning: String
    abstract val expectation: String

    data class Click(val text: String, override val reasoning: String, override val expectation: String) : AgentAction()
    data class ClickAt(val x: Float, val y: Float, override val reasoning: String, override val expectation: String) : AgentAction()
    data class LongPress(val x: Float, val y: Float, override val reasoning: String, override val expectation: String) : AgentAction()
    data class Swipe(val dir: Dir, override val reasoning: String, override val expectation: String) : AgentAction() {
        enum class Dir { UP, DOWN, LEFT, RIGHT }
    }
    data class Scroll(val dir: Dir, override val reasoning: String, override val expectation: String) : AgentAction() {
        enum class Dir { UP, DOWN, LEFT, RIGHT }
    }
    data class ScrollToElement(val text: String, override val reasoning: String, override val expectation: String) : AgentAction()
    data class GridTap(val index: Int, override val reasoning: String, override val expectation: String) : AgentAction()
    data class Type(val text: String, override val reasoning: String, override val expectation: String) : AgentAction()
    object Back : AgentAction() {
        override val reasoning = "返回上一级"
        override val expectation = "界面回退"
    }
    object Home : AgentAction() {
        override val reasoning = "回到桌面"
        override val expectation = "显示桌面"
    }
    data class OpenApp(val packageName: String, override val reasoning: String, override val expectation: String) : AgentAction()
    data class Wait(val ms: Long, override val reasoning: String, override val expectation: String) : AgentAction()
    data class WaitForElement(val text: String, override val reasoning: String, override val expectation: String) : AgentAction()
    object Done : AgentAction() {
        override val reasoning = "任务已完成"
        override val expectation = "—"
    }
    data class Fail(val reason: String = "无法完成", override val reasoning: String = "", override val expectation: String = "") : AgentAction()
    data class AskUser(val question: String, override val reasoning: String, override val expectation: String) : AgentAction()

    override fun toString(): String = when (this) {
        is Click -> "点击「" + text + "」"
        is ClickAt -> "点击坐标(" + x + "," + y + ")"
        is LongPress -> "长按(" + x + "," + y + ")"
        is Swipe -> "滑动" + dir
        is Scroll -> "滚动" + dir
        is ScrollToElement -> "滚动到「" + text + "」"
        is GridTap -> "网格点击#" + index
        is Type -> "输入「" + text + "」"
        Back -> "返回"
        Home -> "回桌面"
        is OpenApp -> "打开App(" + packageName + ")"
        is Wait -> "等待" + ms + "ms"
        is WaitForElement -> "等待元素「" + text + "」"
        Done -> "完成"
        is Fail -> "失败(" + reason + ")"
        is AskUser -> "询问用户：" + question
    }
}
