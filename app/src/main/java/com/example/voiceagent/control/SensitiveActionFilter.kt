package com.example.voiceagent.control

import com.example.voiceagent.agent.AgentAction
import com.example.voiceagent.util.Config

enum class ActionLevel { AUTO, ASK, BLOCK }

data class SensitiveDecision(
    val level: ActionLevel,
    val reason: String,
    val question: String
)

object SensitiveActionFilter {

    private val paymentKeywords = listOf(
        "支付", "付款", "转账", "汇款", "借款", "贷款", "充值", "提现",
        "确认支付", "立即支付", "指纹支付", "免密支付", "刷脸支付"
    )

    private val destructiveKeywords = listOf(
        "删除", "清空", "卸载", "注销", "退出登录", "恢复出厂", "格式化"
    )

    private val sensitivePackages = mapOf(
        "com.eg.android.AlipayGphone" to "支付宝",
        "com.tencent.mm" to "微信",
        "com.cmbchina.cmbproduction" to "招商银行",
        "com.icbc.im" to "工商银行"
    )

    fun evaluate(action: AgentAction, activePackage: String?): SensitiveDecision {
        val appName = sensitivePackages[activePackage] ?: ""
        return when (action) {
            is AgentAction.OpenApp -> {
                if (Config.blockOpeningSensitiveApps && sensitivePackages.containsKey(action.packageName)) {
                    SensitiveDecision(
                        ActionLevel.BLOCK,
                        "禁止打开敏感App " + action.packageName,
                        "即将打开敏感应用，确定继续吗？"
                    )
                } else {
                    SensitiveDecision(ActionLevel.AUTO, "", "")
                }
            }
            is AgentAction.Click -> {
                val text = action.text
                when {
                    paymentKeywords.any { text.contains(it) } ->
                        SensitiveDecision(
                            ActionLevel.BLOCK,
                            "涉及资金操作：" + text,
                            "检测到资金相关操作「" + text + "」，风险极高，确定强制执行吗？"
                        )
                    destructiveKeywords.any { text.contains(it) } ->
                        SensitiveDecision(
                            ActionLevel.ASK,
                            "破坏性操作：" + text,
                            "即将执行「" + text + "」，是否允许？"
                        )
                    appName.isNotEmpty() ->
                        SensitiveDecision(
                            ActionLevel.ASK,
                            "在" + appName + "内点击：" + text,
                            "即将在" + appName + "中点击「" + text + "」，是否允许？"
                        )
                    else -> SensitiveDecision(ActionLevel.AUTO, "", "")
                }
            }
            is AgentAction.ClickAt, is AgentAction.LongPress, is AgentAction.GridTap -> {
                if (appName.isNotEmpty()) {
                    SensitiveDecision(
                        ActionLevel.ASK,
                        "在" + appName + "内执行手势",
                        "即将在" + appName + "中执行手势操作，是否允许？"
                    )
                } else {
                    SensitiveDecision(ActionLevel.AUTO, "", "")
                }
            }
            is AgentAction.Type -> {
                val digitsOnly = action.text.length >= 6 && action.text.all { it.isDigit() }
                when {
                    digitsOnly ->
                        SensitiveDecision(
                            ActionLevel.ASK,
                            "疑似输入密码/金额",
                            "检测到疑似密码或金额输入，是否允许？"
                        )
                    appName.isNotEmpty() ->
                        SensitiveDecision(
                            ActionLevel.ASK,
                            "在" + appName + "内输入",
                            "即将在" + appName + "中输入文字，是否允许？"
                        )
                    else -> SensitiveDecision(ActionLevel.AUTO, "", "")
                }
            }
            else -> SensitiveDecision(ActionLevel.AUTO, "", "")
        }
    }
}
