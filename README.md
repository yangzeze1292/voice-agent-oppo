# VoiceAgent · OPPO ColorOS 16 语音控制手机应用

免 Root 语音操控手机的 Android 应用，针对 OPPO ColorOS 16（用户设备版本 16.0.9.400）深度适配。
本版本（v3）基于方案文档 v2.1 完成了主链路重构，实现细节参考 Droidrun Portal / Open-AutoGLM 等开源项目。

## 能力闭环

```
麦克风(听) → AI大脑(理解+决策) → 无障碍服务(操作) → 界面反馈
```

## v3 相对旧骨架的改进

| 问题 | 修复 |
|---|---|
| AskUser 答案永远传不回 Agent（旧版恒返回 "n"） | suspend confirm + CompletableDeferred，UI 横幅确认后真正回传 |
| 无多轮记忆 / 无 Manager 层 | Manager 先行规划（LlmService.plan）+ Executor 每步携带「计划 + 最近 6 步历史」 |
| expectation 字段从不使用 | 动作校验：expectation 关键词 + 16×16 屏幕变化检测，结果回填历史 |
| 死循环"重规划"只是清栈 | 连续 3 次重复动作 → 真正调用 Manager 换路径 + 注入 REPLAN_HINT，最多重规划 2 次 |
| 打开微信/支付宝整个被禁 | 拦截点下沉：OpenApp 默认放行，资金关键词 BLOCK、破坏性操作 ASK、敏感 App 内操作 ASK |
| 滑动坐标硬编码 540×1000 | 按真实屏幕尺寸计算（screenSize） |
| dumpUiTree 无截断 | 深度 ≤16 / 节点 ≤120 / 只保留可见且有意义节点 |
| LLM 无超时静默失败 | 15s 连接 + 90s 读超时，网络错误/5xx 重试 2 次，错误经 ServiceBridge 上报 |
| deepseek-chat 收到 base64 截图（纯文本模型看不懂） | 纯文本模式不再携带截图；配置 VLM 后走 OpenAI vision 格式发送图片 |
| 语音轮询 8 秒、错误不可见 | SpeechService 改为挂起式 listenOnce（partial 即返回）+ 错误码中文提示；小布优先，自动降级 |
| 保活链路错误（拉 FGS ≠ 恢复无障碍） | 诚实化：KeepAliveService 30s 健康轮询 + JobScheduler 15min 周期任务 + 断开通知引导 |
| 无法停止任务 | 顶栏「停止」按钮 + AgentLoop.requestStop() |
| 截图无脱敏 | 密码框/安全节点区域打码后再上传 |
| MediaProjection 兜底缺失 | ScreenCaptureService + 顶栏「录屏兜底」按钮，takeScreenshot 失败时自动取帧 |
| 动作空间缺 scroll/grid_tap | 新增 Scroll / ScrollToElement / GridTap（3×5 网格），共 15 个动作 |
| 无 Gradle Wrapper | 已补 gradlew / gradlew.bat / gradle-wrapper.jar |

## 工程结构

```
VoiceAgent/
├── gradlew / gradlew.bat              构建脚本（Gradle 8.7 Wrapper）
├── app/src/main/
│   ├── AndroidManifest.xml            无障碍/保活/JobService/录屏服务声明
│   ├── res/xml/accessibility_config.xml
│   └── java/com/example/voiceagent/
│       ├── VoiceAgentApp.kt           Application + 通知渠道
│       ├── MainActivity.kt            首次启动走权限引导；录屏授权流
│       ├── service/
│       │   ├── AgentAccessibilityService.kt   手机控制"手"：动作/UI树/截图/脱敏/网格
│       │   ├── KeepAliveService.kt            前台保活 + 30s 健康轮询
│       │   ├── KeepAliveNLService.kt          NotificationListener 兜底触发点
│       │   ├── KeepAliveJobService.kt         JobScheduler 15min 周期检查
│       │   ├── SpeechService.kt               系统语音识别（降级方案，挂起式）
│       │   └── ScreenCaptureService.kt        MediaProjection 录屏截图兜底
│       ├── keepalive/
│       │   ├── OppoKeepAliveHelper.kt         保活四件套检测/引导 + 周期任务注册
│       │   └── BootReceiver.kt                开机注册周期任务
│       ├── agent/
│       │   ├── AgentAction.kt                 15 个动作 + reasoning/expectation
│       │   ├── ActionParser.kt                LLM 输出解析（容错两种字段写法）
│       │   ├── Prompts.kt                     Manager/Executor Prompt（版本化）
│       │   ├── LlmService.kt                  DeepSeek 适配 + 超时重试 + VLM 支持 + plan()
│       │   ├── AgentLoop.kt                   主循环：规划/记忆/校验/重规划/打断
│       │   └── OppoBreenoService.kt           小布 SDK 骨架（待接入）
│       ├── control/
│       │   └── SensitiveActionFilter.kt       三态拦截（AUTO/ASK/BLOCK）
│       ├── viewmodel/ConversationViewModel.kt 协调 + 确认流 + 停止
│       ├── ui/screen/                        MainScreen / PermissionGuideScreen
│       └── util/
│           ├── ServiceBridge.kt              Service-ViewModel 通信桥
│           ├── ScreenCapture.kt              MediaProjection 帧采集
│           └── Config.kt                     全部可调参数
└── gradle/libs.versions.toml
```

## 部署步骤

### 1. 编译工程

通用方式（已配好 Gradle Wrapper，需 JDK 17 + Android SDK）：

```bash
./gradlew assembleDebug
```

> 构建注意事项（踩坑记录）：
> - 工作目录含中文时需 `android.overridePathCheck=true`（已写入 gradle.properties）；
> - Gradle 官方分发源在本网络环境无法解析，wrapper 已改用腾讯镜像；
> - **Gradle 单元测试 worker 在中文路径下无法加载类**（GBK 编码破坏 classpath），
>   跑 `testDebugUnitTest` 请把工程复制到纯 ASCII 路径（如 D:\vagent\VoiceAgent），并设置
>   `$env:GRADLE_USER_HOME = "D:\gradle-home"`；assembleDebug 本身不受影响。

### 2. 配置 LLM（本地私有配置）

API Key 不再写入源码。请在工程根目录的 `local.properties` 中加入本地私有配置
（该文件已被 `.gitignore` 忽略），或用同名环境变量注入：

```properties
VOICEAGENT_LLM_API_KEY=你的 DeepSeek Key
VOICEAGENT_LLM_VISION_API_KEY=你的 GLM 视觉模型 Key
```

代码会在构建时把这些值注入 `BuildConfig`，`util/Config.kt` 只读取构建产物中的值。

**模型实测结论（本工程已据此调优）**：
- deepseek-v4-pro 是**推理模型**：每步 5-10 秒（推理 + 输出），Executor 单步输出约 100-400 token；
  LlmService 已设 max_tokens=2048、读超时 120s、截断/空正文告警。
- **API 不支持图片输入**（已实测返回 unknown variant image_url），默认纯 UI 树文本模式，
  实测可正确处理点击/输入/滚动/拒绝危险任务等场景。
- 追求速度：把 `llmExecutorModel` 设为 `"deepseek-v4-flash"`（Executor 每步换快模型，
  Manager 规划仍用 v4-pro），实测可明显降延迟、质量略降。

**视觉模型（GLM-5V-Turbo）**：deepseek-v4-pro 的 API 不支持图片输入，
因此双模态 Executor 使用智谱 GLM-5V-Turbo：

```kotlin
var llmVisionEnabled: Boolean = true
var llmVisionApiKey: String = BuildConfig.LLM_VISION_API_KEY
var llmVisionBaseUrl: String = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
var llmVisionModel: String = "glm-5v-turbo"
```

**双模型架构（最终形态）**：
| 角色 | 模型 | 输入 | 实测 |
|---|---|---|---|
| Manager 规划 | deepseek-v4-pro | 指令 + 屏幕概览（文本） | ✓ 4-5s |
| Executor 每步 | GLM-5V-Turbo | 截图 + UI 树（双模态） | ✓ 实测能识别截图文字/状态，11s/步 |
| Executor 降级 | deepseek-v4-pro | 仅 UI 树（文本） | ✓ 视觉失败/黑屏时自动切换 |

### 3. 安装到 OPPO 手机

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

或直接把 APK 传到手机点击安装。

### 4. 手机端权限配置（ColorOS 16）

首次启动 App 会自动弹出引导页，按顺序完成：

1. **开启无障碍服务**：设置 → 系统设置 → 无障碍 → 已安装的服务 → VoiceAgent → 开启
2. **开启通知访问权限**：设置 → 通知与状态栏 → 通知访问 → VoiceAgent → 开启
3. **自启动管理**：设置 → 应用 → 自启动管理 → VoiceAgent → 允许
4. **关闭后台冻结**：设置 → 电池 → 耗电保护 → VoiceAgent → 关闭「后台冻结」+「异常耗电自动优化」
5. **移出冷冻室**：设置 → 应用管理 → 冷冻室 → 移除 VoiceAgent
6. **麦克风权限**：首次使用时弹窗授予
7. **（可选）录屏截图兜底**：主界面顶栏「屏幕共享」按钮，授权一次 MediaProjection

### 5. 使用

- 点麦克风按钮说话，或用文字输入指令
- 示例指令：
  - "打开设置"
  - "打开 WLAN"
  - "返回"
  - "回桌面"
  - "打开微信，给文件传输助手发一条消息"

## 接入 OPPO 小布 SDK（可选，推荐）

本工程已预留 `OppoBreenoService` 接口骨架（语音/理解/TTS 三接口 + BreenoFactory 接线，
ViewModel 已优先调用小布、失败自动降级）。接入后复用 ColorOS 原生能力，免 Google 服务依赖：

1. 访问 https://open.oppomobile.com 注册开发者
2. 在「小布 AI 能力」页面创建技能，定义意图与槽位
3. 引入官方 SDK 依赖到 `app/build.gradle.kts`
4. 替换 `OppoBreenoServiceImpl` 中的 TODO 为真实 SDK 调用
5. `Config.useOppoBreeno = true`（默认已开启，未接入时自动降级）

## 安全机制

- **截图脱敏**：密码框/安全节点打码（Config.maskSensitiveNodes）
- **敏感动作三态拦截**：资金关键词 BLOCK、破坏性操作 ASK、敏感 App 内操作 ASK、
  疑似密码输入 ASK——全部经用户确认后才执行
- **安全窗口降级**：flagSecure 黑屏（银行/支付）自动切换纯 UI 树模式
- **LLM 错误不静默**：所有失败经 ServiceBridge 显示在对话流中

## 单元测试

```powershell
# 17 个测试：ActionParser（11）+ SensitiveActionFilter（6）——已全部通过
# 含真实 deepseek-v4-pro API 输出的解析回归用例
gradlew.bat testDebugUnitTest
```

## 验收测试（OPPO 专项）

- [ ] 锁屏 8 小时后，无障碍服务仍存活（或 30 秒内收到断开提醒）
- [ ] 从最近任务列表划掉 App 后，NotificationListener 触发恢复检查
- [ ] 打开银行 App（招行/工行）截图黑屏时，自动降级为 UI 树模式
- [ ] 支付宝/微信内点击「转账」被拦截并询问用户
- [ ] 执行中点击顶栏「停止」立即中断
- [ ] 授权录屏后，关闭无障碍截图（测试模式）仍能通过兜底取帧

## 已知限制（诚实说明）

- **无障碍服务被杀无法程序化自启**：这是 Android/ColorOS 系统限制，任何 App 都无法绕过。
  本工程做到的是：检测 + 通知引导 + JobScheduler 兜底提醒。
- **安全窗口（flagSecure）在 MediaProjection 录屏中同样黑屏**：录屏兜底只覆盖
  takeScreenshot API 失败场景，不覆盖银行 App 安全窗口。
- **MediaProjection 每次启动 App 需重新授权一次**（Android 14+ 限制）。
- `SpeechRecognizer` 在部分 ColorOS 上因 Google 服务被裁剪而不可用，需接入讯飞或小布 SDK。
- 纯文本模型（deepseek-chat）决策能力有限，建议按上文配置 VLM。
- `PermissionGuideScreen` 的「去设置」按钮跳转到通用页，部分 ColorOS 版本路径略有差异。

## 相关文档

完整方案设计见：`../语音控制手机应用_方案报告_v2_优化版.md`（含 v2.1 OPPO 专项适配章节）
