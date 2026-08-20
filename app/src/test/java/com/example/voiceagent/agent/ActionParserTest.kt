package com.example.voiceagent.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionParserTest {

    @Test
    fun parseClick() {
        val a = ActionParser.parse("""{"action":"click","text":"网络和互联网","reasoning":"r","expectation":"e"}""")
        assertTrue(a is AgentAction.Click)
        assertEquals("网络和互联网", (a as AgentAction.Click).text)
    }

    @Test
    fun parseClickWithTargetField() {
        val a = ActionParser.parse("""{"action":"click","target":{"text":"设置"},"reasoning":"r","expectation":"e"}""")
        assertTrue(a is AgentAction.Click)
        assertEquals("设置", (a as AgentAction.Click).text)
    }

    @Test
    fun parseClickAtWithBoundsCenter() {
        val a = ActionParser.parse("""{"action":"click_at","target":{"bounds":[0,100,200,300]},"reasoning":"r","expectation":"e"}""")
        assertTrue(a is AgentAction.ClickAt)
        a as AgentAction.ClickAt
        assertEquals(100f, a.x, 0.01f)
        assertEquals(200f, a.y, 0.01f)
    }

    @Test
    fun parseSwipeDirCaseInsensitive() {
        val a = ActionParser.parse("""{"action":"swipe","dir":"down","reasoning":"r","expectation":"e"}""")
        assertTrue(a is AgentAction.Swipe)
        assertEquals(AgentAction.Swipe.Dir.DOWN, (a as AgentAction.Swipe).dir)
    }

    @Test
    fun parseScrollAndGridTap() {
        val s = ActionParser.parse("""{"action":"scroll","dir":"up"}""")
        assertTrue(s is AgentAction.Scroll)
        assertEquals(AgentAction.Scroll.Dir.UP, (s as AgentAction.Scroll).dir)
        val g = ActionParser.parse("""{"action":"grid_tap","index":7}""")
        assertTrue(g is AgentAction.GridTap)
        assertEquals(7, (g as AgentAction.GridTap).index)
    }

    @Test
    fun parseTypeAndOpenApp() {
        val t = ActionParser.parse("""{"action":"type","text":"我到家了","reasoning":"r","expectation":"e"}""")
        assertTrue(t is AgentAction.Type)
        assertEquals("我到家了", (t as AgentAction.Type).text)
        val o = ActionParser.parse("""{"action":"open_app","package":"com.android.settings"}""")
        assertTrue(o is AgentAction.OpenApp)
        assertEquals("com.android.settings", (o as AgentAction.OpenApp).packageName)
    }

    @Test
    fun parseDoneFailAskUser() {
        assertTrue(ActionParser.parse("""{"action":"done"}""") is AgentAction.Done)
        val f = ActionParser.parse("""{"action":"fail","reason":"找不到"}""")
        assertTrue(f is AgentAction.Fail)
        assertEquals("找不到", (f as AgentAction.Fail).reason)
        val q = ActionParser.parse("""{"action":"ask_user","question":"要发给谁？"}""")
        assertTrue(q is AgentAction.AskUser)
    }

    @Test
    fun parseWithSurroundingText() {
        val raw = "好的，下一步动作是：\n{"action":"back"}\n完成"
        assertTrue(ActionParser.parse(raw) is AgentAction.Back)
    }

    @Test
    fun parseGarbageReturnsFail() {
        assertTrue(ActionParser.parse("你好，我无法执行") is AgentAction.Fail)
    }

    @Test
    fun parseEmptyReturnsFail() {
        assertTrue(ActionParser.parse("") is AgentAction.Fail)
    }

    @Test
    fun parseRealModelOutputs() {
        val real = listOf(
            """{"action":"click","text":"网络和互联网","reasoning":"根据执行计划，需要先点击「网络和互联网」进入该设置页面，然后才能操作WLAN开关","expectation":"进入网络和互联网设置页面，看到WLAN开关选项"}""",
            """{"action":"click","text":"网络和互联网","reasoning":"当前在设置页面，需先进入网络和互联网","expectation":"进入网络和互联网页面，显示WLAN等选项"}""",
            """{"action":"done","reasoning":"当前界面显示 WLAN 开关已处于开启状态（checked=true，描述为已开启），用户目标已达成","expectation":"任务结束"}""",
            """{"action":"scroll","dir":"down","reasoning":"当前聊天列表未显示“妈妈”，向下滚动查找妈妈的会话","expectation":"列表中出现“妈妈”或其他相关联系人"}""",
            """{"action":"type","text":"我到家了","reasoning":"当前输入框已聚焦，需要输入要发送的消息","expectation":"输入框显示“我到家了"}"""
        )
        val expected = listOf(
            AgentAction.Click::class,
            AgentAction.Click::class,
            AgentAction.Done::class,
            AgentAction.Scroll::class,
            AgentAction.Type::class
        )
        real.forEachIndexed { i, raw ->
            val a = ActionParser.parse(raw)
            assertTrue("case " + i + ": " + a, expected[i].isInstance(a))
        }
    }
}
