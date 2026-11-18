# 汐 · Solid Butler

> 运行在 **LineageOS 24 / Android 17** 上的本地设备管家。
> UI 采用 [Solid Glass](https://github.com/deeplethe/solid-glass) 的材质语言：深色中性底、三级玻璃厚度、边缘高光、克制的彩色反馈。
>
> 应用名与图标来自《鸣潮》的「汐」（官方立绘，版权归库洛游戏），仅作个人非商业使用。

## 能做什么

| 模块 | 能力 |
| --- | --- |
| **存储** | 深度扫描、分类（缓存 / 残留 APK / 日志 / 缩略图 / 空目录 / 旧下载）、重复文件（大小 → 64KB 局部哈希 → 全量 SHA-256）、目录与类型占用、应用缓存专项 |
| **应用** | 冻结 / 解冻、为当前用户卸载 / 恢复、后台限制、唤醒锁拦截、电池白名单、自启动组件级开关、危险权限批量收回、应用锁（本地 PIN + 无障碍遮罩） |
| **性能** | 2 秒实时面板（CPU 各核频率/负载、GPU、RAM、温度、网络速率、电池）、24 小时耗电排行、充电上限 / 定时充电 / 快充模式、电池健康、governor 预设、温和回收后台 |
| **安全** | 敏感权限聚合、相机/麦克风实时占用、离线追踪 SDK 扫描、侧载与权限组合风险规则、Root 防火墙（iptables 独立链） |
| **自启** | 开机/网络接收器逐项开关、后台限制、`am set-standby-bucket` 待机分组 |
| **AI** | OpenAI 兼容接口的云端管家，支持系统语音识别输入与 TTS 播报；所有动作先展示命令与影响，再二次确认 |
| **LineageOS** | `lineagehealth` 充电控制、`lineagehardware` 硬件特性开关；`livedisplay`/`trust`/`profile` 服务探测；可作为系统数字助理（ACTION_ASSIST） |
| **规则** | 25% 低电量语音、95% 充满语音、到上限自动限充、锁屏限制后台、连接 Wi-Fi 时限制 |
| **隐私工具** | 特殊权限总览、通知渠道屏蔽、唤醒/任务审计、崩溃与 ANR、域名 DNS 过滤、回收站 |

## 为什么是“调用官方接口”

已核对 `LineageOS/android_lineage-sdk` 的 `lineage-24.0` 分支：

- **充电控制**：直接调用 `lineagehealth` 系统服务（`IHealthInterface`）：`setChargingControlEnabled/Mode/Limit/StartTime/TargetTime`、`setFastChargeMode`。源码中这些事务没有权限检查，因此**不需要 Root**，也不需要 system 签名。
- **应用控制**：`pm disable-user`、`pm uninstall --user 0`、`appops set`、`cmd deviceidle whitelist`、`am set-standby-bucket`、`am kill-all`、`dumpsys`。
- **存储**：`StorageStatsManager.queryStatsForUid`、`StatFs`、`MANAGE_EXTERNAL_STORAGE`、流式 SHA-256。
- **性能**：`/proc/stat`、`cpufreq`、`thermal_zone`、`ActivityManager.MemoryInfo`、`BatteryManager`、`TrafficStats`、`UsageStatsManager`、`NetworkStatsManager`。
- **网络防火墙**：内核 netfilter 的独立链 `BUTLER_FW` / `BUTLER_FW6`，只匹配 `--uid-owner`，可整体清空。

特权分级：**无特权** → **Shizuku（shell）** → **Root**。不支持的能力会明确置灰并给出原因，不做假开关。

## 构建

APK 由 GitHub Actions 云端编译（固定 keystore 签名）：

```
Actions → Build APK → 任意一次 run → Artifacts / Releases
```

本地需要 JDK 17 与 Android SDK：

```bash
./gradlew assembleDebug
# 可选：-PbutlerCompileSdk=37 -PbutlerTargetSdk=37
```

签名参数通过环境变量注入：`BUTLER_STORE_FILE`、`BUTLER_STORE_PASSWORD`、`BUTLER_KEY_ALIAS`、`BUTLER_KEY_PASSWORD`。

## 安装后要给的权限

1. **所有文件访问**：深度扫描。
2. **使用情况访问**：应用缓存、耗电排行。
3. **无障碍服务**：应用锁（只读前台包名，不读屏幕内容）。
4. **Shizuku 或 Root**：冻结、后台限制、自启动组件、权限收回、防火墙。

## 目录

```
app/src/main/java/io/github/dryearth/solidbutler/
├── ai/            OpenAI 兼容客户端
├── core/          Shell、Shizuku、Capability、LineageHealth、Prefs
├── data/          存储 / 应用 / 性能 / 安全仓库
├── service/       应用锁无障碍服务、开机恢复防火墙
├── ui/            Solid Glass 主题、组件、5 个页面 + AI 对话 + 设置
└── ButlerViewModel.kt
```

## 已知边界

- 应用锁依赖无障碍服务；部分 ROM 会限制后台拉起 Activity，本项目用 `TYPE_ACCESSIBILITY_OVERLAY` 规避。
- 相机/麦克风实时占用来自 `dumpsys`；历史调用记录属于系统隐私面板的数据，普通应用无法读取，本项目不伪造。
- 防火墙为 UID 级；域名级过滤（`VpnService`）在后续版本。
- `pm clear --cache-only` 在部分 ROM 上不可用，会自动回落到 Root 删除 cache 目录。
- 清理默认不进入废纸篓；删除前有二次确认。

## 许可

代码以 MIT 发布。Solid Glass 的设计语言来自 [deeplethe/solid-glass](https://github.com/deeplethe/solid-glass)（MIT）。「汐」角色版权归库洛游戏所有，本项目仅为个人非商业使用。
