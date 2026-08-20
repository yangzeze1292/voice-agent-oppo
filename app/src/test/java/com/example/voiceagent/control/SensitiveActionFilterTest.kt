package com.example.voiceagent.control

import com.example.voiceagent.agent.AgentAction
import org.junit.Assert.assertEquals
import org.junit.Test

class SensitiveActionFilterTest {

    @Test
    fun openWechatAllowedByDefault() {
        val d = SensitiveActionFilter.evaluate(
            AgentAction.OpenApp("com.tencent.mm", "", ""), null
        )
        assertEquals(ActionLevel.AUTO, d.level)
    }

    @Test
    fun paymentClickBlocked() {
        val d = SensitiveActionFilter.evaluate(
            AgentAction.Click("确认支付", "", ""), "com.eg.android.AlipayGphone"
        )
        assertEquals(ActionLevel.BLOCK, d.level)
    }

    @Test
    fun destructiveClickAsk() {
        val d = SensitiveActionFilter.evaluate(
            AgentAction.Click("删除全部", "", ""), null
        )
        assertEquals(ActionLevel.ASK, d.level)
    }

    @Test
    fun clickInsideSensitiveAppAsk() {
        val d = SensitiveActionFilter.evaluate(
            AgentAction.Click("文件传输助手", "", ""), "com.tencent.mm"
        )
        assertEquals(ActionLevel.ASK, d.level)
    }

    @Test
    fun passwordLikeTypeAsk() {
        val d = SensitiveActionFilter.evaluate(
            AgentAction.Type("123456", "", ""), null
        )
        assertEquals(ActionLevel.ASK, d.level)
    }

    @Test
    fun normalClickAuto() {
        val d = SensitiveActionFilter.evaluate(
            AgentAction.Click("网络和互联网", "", ""), "com.android.settings"
        )
        assertEquals(ActionLevel.AUTO, d.level)
    }
}
