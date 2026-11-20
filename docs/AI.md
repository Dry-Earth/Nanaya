# AI 管家实现说明

本文说明 Nanaya（汐）的 AI 管家、悬浮助手、语音链路和无线 ADB 的代码结构，方便继续开发和构建。

## 入口

```
ACTION_ASSIST
  → AssistEntryActivity / MainActivity（两者都是无界面兼容入口）
  → 有悬浮窗权限时启动 AssistOverlayService
  → 无悬浮窗权限时回退到 AppActivity 的 AssistantScreen
```

数字助理不依赖独立的 `VoiceInteractionService`。`AppActivity` 是完整的主界面；`MainActivity` 只保留为无界面兼容入口。悬浮窗展开“完整对话”时通过 `open_chat` 进入 `AppActivity` 的 AI 对话页。

## 主要文件

| 文件 | 作用 |
| --- | --- |
| `ai/AiClient.kt` | OpenAI 兼容接口、模型列表、图片消息、响应解析 |
| `ButlerViewModel.kt` | 对话状态、模型配置、动作确认、设备上下文、自动化 |
| `ui/screens/AssistantScreen.kt` | 全屏 AI 对话、图片选择、TTS、系统语音会话与聆听声波 |
| `ui/screens/AiSettingsScreen.kt` | 接口、模型、思考强度、人设、语音、悬浮窗、开场白 |
| `service/AssistOverlayService.kt` | 全屏悬浮窗、唤醒动画、液态玻璃面板、拖拽与自动生长 |
| `service/OverlayVoice.kt` | 悬浮窗语音会话，结果回传悬浮窗 |
| `service/SystemRecognitionSession.kt` | 聊天页直接绑定 `RecognitionService`，支持预览与结束识别 |
| `service/SystemSpeechRecognizer.kt` | 读取系统语音识别服务并选择外部引擎 |
| `ui/components/VoiceWave.kt` | 聆听时的流动声波与呼吸光晕 |
| `core/AdbManager.java` | 无线 ADB 配对、连接与 shell 执行 |
| `core/Shell.kt` | Root / 无线 ADB / 普通 Shell 后端选择 |

## AI 对话

1. `AiSettingsScreen` 保存接口地址、API Key、模型名、思考模式、人设和语音配置。
2. `AiClient` 读取配置，拼装 OpenAI 兼容请求。
3. 模型返回普通文本或动作 JSON；动作由 `parseResponse` 解析。
4. 有动作时先写入 `ChatMessage.action`，状态为 `PENDING`。
5. 用户确认后由 `ButlerViewModel` 执行对应设备操作；取消则不执行。
6. 回复可交给系统 TTS 播报。

设备上下文只包含回答所需的摘要：机型、系统版本、权限状态、电池、内存、存储、应用数量和敏感权限统计，不上传文件。

## 悬浮助手

- 窗口类型：`TYPE_APPLICATION_OVERLAY`，全屏、贴边、无系统窗口动画。
- 唤醒：暗场、右侧极光、边框收缩、顶部到胶囊的流光、沿胶囊边缘流动的彩色光环、粒子爆发。
- 语音胶囊：未交互时只显示底部胶囊；点击后展开面板。
- 面板：上拉展开、下拉吸附、内容自动生长，超过屏幕高度后内部滚动。
- 语音：绑定系统 `RecognitionService`，不打开第二个语音窗口；聆听时显示流动声波和实时预览，识别结果直接发送；错误、取消、销毁时清理“正在识别”状态。
- 玻璃：半透明底色、斜向高光、磨砂颗粒、边缘亮线和内阴影；不启用不稳定的跨窗口实时模糊。

## 语音链路

完整的调用方式、权限与排错说明见 [`VOICE.md`](VOICE.md)。

```
悬浮窗 / 胶囊
  → OverlayVoiceBus
  → OverlaySpeech.start()
  → SystemRecognitionSession / OverlaySpeech
  → SystemSpeechRecognizer.create()
  → 系统配置的 RecognitionService
  → onPartialResults / onResults
  → OverlayVoiceBus.result
  → ButlerViewModel.sendChat()
```

- 识别引擎来自系统“语音输入”设置。
- 不启动系统语音输入 Activity，不弹识别面板。
- 唤醒开场白使用系统 TTS，播完后再开始识别。
- 识别结果为空、网络错误、权限不足、识别器忙都会给出明确提示。

## 无线 ADB

- 通过系统无线调试的配对协议在机内完成配对。
- 配对需要地址、配对端口和 6 位配对码。
- 配对端口与无线调试首页显示的连接端口不同，按配对弹窗里的端口填写。
- 配对后通过 mDNS 发现 TLS 连接端口。
- 配对成功后关闭系统配对弹窗，再点「只连接」。
- 连接成功后，`Shell` 优先使用 ADB；否则使用 Root 或普通 shell。
- ADB shell 是 `shell` 用户，不是 root；需要写内核或系统分区的操作仍要求 Root。

## 构建

```bash
./gradlew assembleDebug
```

- JDK 17
- Android SDK 36
- 发布签名使用环境变量：`BUTLER_STORE_FILE`、`BUTLER_STORE_PASSWORD`、`BUTLER_KEY_ALIAS`、`BUTLER_KEY_PASSWORD`

## 隐私

- 不上传文件、截图或应用列表。
- AI 请求只发送对话内容和必要的设备摘要。
- 无线 ADB 密钥和证书保存在应用私有目录。
- 所有破坏性操作先展示影响，再二次确认。
