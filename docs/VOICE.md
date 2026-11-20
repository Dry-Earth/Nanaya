# 语音输入实现

本文固定 Nanaya 的语音输入架构、调用方式和排错入口。后续改语音相关代码时先读这一页，避免又退回“启动系统语音 Activity”的旧方案。

## 结论

Nanaya 的语音输入分两条入口，但共用同一套底层识别方式：

- 完整聊天页：`AssistantScreen`
- 跨应用悬浮助手：`AssistOverlayService` + `OverlaySpeech`

两者都直接绑定系统的 `RecognitionService`，不通过 `ACTION_RECOGNIZE_SPEECH` 打开第二个系统语音窗口。

原因：

- LineageOS 和类原生系统经常只提供离线识别服务（例如 Marmalade），没有可用的语音 Activity。
- 系统语音 Activity 会离开当前界面，悬浮助手无法保持。
- 直接绑定服务可以自己接收 `onPartialResults` / `onResults`，统一处理预览、结束和错误。

## 文件

| 文件 | 作用 |
| --- | --- |
| `service/SystemSpeechRecognizer.kt` | 从系统设置和已安装服务里选择 `RecognitionService`，创建 `SpeechRecognizer` |
| `service/SystemRecognitionSession.kt` | 聊天页的识别会话：开始、结束、取消、预览、最终结果、错误映射 |
| `service/OverlayVoice.kt` | 悬浮助手的识别会话，通过 `OverlayVoiceBus` 把状态和结果交给悬浮窗 |
| `ui/screens/AssistantScreen.kt` | 聊天页麦克风按钮、权限请求、实时预览，结果直接 `sendChat` |
| `service/AssistOverlayService.kt` | 悬浮语音胶囊、前台服务麦克风类型、结果发送 |
| `ui/components/VoiceWave.kt` | 聆听时的流动声波、呼吸光晕和边光 |

## 聊天页流程

```text
点麦克风
  → 已授权 RECORD_AUDIO
      → SystemRecognitionSession.start()
      → SpeechRecognizer.startListening()
      → onReadyForSpeech          显示“正在聆听”
      → onPartialResults          更新预览文字
      → onResults                 sendChat(最终文字)，清空聆听状态
  → 未授权
      → 请求 RECORD_AUDIO
      → 授权成功后 start()
```

再次点麦克风或点停止图标：

```text
SystemRecognitionSession.finish()
  → SpeechRecognizer.stopListening()
  → 等待 onResults
```

识别结果直接发送，不填入输入框等待确认。

## 悬浮助手流程

```text
点语音胶囊
  → AssistOverlayService.startVoice()
  → 检查 RECORD_AUDIO
  → 提升前台服务类型为 microphone
  → OverlaySpeech.start()
  → OverlayVoiceBus 更新 listening / level / status
  → onResults
  → OverlayBus.result
  → vm.sendChat(text)
```

悬浮窗必须在前台服务里声明麦克风类型，否则 Android 11+ 会拒绝后台录音。Nanaya 的做法：

- Manifest：`foregroundServiceType="specialUse|microphone"`
- 服务创建时如果已经有麦克风权限，直接带上 microphone 类型
- 用户后来才授权时，通过 `AssistOverlayService.refreshMic()` 重新应用类型

## 权限

需要 `android.permission.RECORD_AUDIO`。

设置页的「权限与授权」可以查看并跳转：

- 麦克风
- 通知
- 悬浮窗
- 使用情况访问
- 所有文件访问

如果系统里显示已授权、App 仍认为未授权，优先检查：

1. 是否授予的是“仅本次”。
2. 系统隐私面板或 ROM 是否单独拦截了麦克风。
3. 悬浮助手的前台服务是否带上了 microphone 类型。
4. 识别引擎自己的麦克风权限是否被关闭。

## 聆听动效

`VoiceWave` 是轻量 Compose 实现，不依赖 AGSL：

- 蓝、紫、粉、奶油色渐变
- 三条错位正弦波
- `onRmsChanged` 有电平时抬起振幅和亮度
- 没有电平时按固定节奏呼吸
- 底部有柔和光晕和边光

聊天页显示在输入框上方，悬浮助手显示在语音胶囊后面。

## 常见错误

| 提示 | 原因 |
| --- | --- |
| 没有可用的系统语音识别服务 | 系统没有配置 `RecognitionService`，也没装离线引擎 |
| 麦克风权限不足 | App 没有 `RECORD_AUDIO`，或系统隐私开关拦截 |
| 识别服务缺少麦克风权限 | 识别引擎自身没有麦克风权限 |
| 没有听清，再试一次 | `ERROR_NO_MATCH` / `ERROR_SPEECH_TIMEOUT` |
| 识别器忙，稍后再试 | `ERROR_RECOGNIZER_BUSY` |
| 识别服务网络错误 | 在线识别引擎网络失败 |

## 不要再做的事

- 不要改回 `ACTION_RECOGNIZE_SPEECH` 作为主路径。
- 不要在聊天页为了识别再打开系统语音 Activity。
- 不要在悬浮服务里省略 microphone 前台服务类型。
- 不要把识别结果只填进输入框；产品行为是识别完成直接发送。
- 不要用纯 `checkSelfPermission` 判断后就假定录音一定可用，仍要处理识别引擎返回的权限错误。

## 参考

实现思路参考了 `Dry-Earth/AI_Eta` 的系统识别入口：

- `SystemSpeechRecognizer`
- `SystemRecognitionSession`

Nanaya 只移植了“直接绑定系统 `RecognitionService`”这一条路径；云端 ASR、录音、TTS 仍使用本项目原有实现。
