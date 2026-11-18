# 汐 · 后续能力与内核接口笔记

本文记录后续评估项，不代表当前版本已经实现。所有能力优先使用 LineageOS 24 服务或 Android 17 公共 API；只有系统没有提供时才考虑 Root sysfs。

## 参考仓库

- `LineageOS/android_kernel_oneplus_sm8750-modules`（lineage-24.0）
  - `oplus/` 主要是 `hardware/radio`、`kernel`、`secure`、`sensor`，没有现成的调频/温控策略模块。
- `LineageOS/android_kernel_oneplus_sm8750`（lineage-24.0）
  - 标准 Android 通用内核结构；用户空间稳定依赖仍是标准 sysfs + LineageOS 服务。

## 优先接口

1. `lineagehealth`：充电上限、模式、快充（已接入）
2. `lineagehardware`：显示、触控、防闪烁等（已接入）
3. `livedisplay`：色彩、阅读模式（只读探测）
4. Android 17：`DisplayManager`、`BatteryManager`、`PowerManager`、`StorageStatsManager`、`NetworkStatsManager`、`AppOpsManager`、`VpnService`、`NotificationListenerService`、`RoleManager`（按系统支持）

## 候选增强

### 充电与电池
- 旁路充电 / 充电分离（若 `oplus_chg` 暴露对应节点）
- 充电电流上限、充电温度保护
- 电池循环/健康趋势的长期图表
- 充满提醒、低电量提醒（已实现）

### 显示与触控
- 刷新率切换（DisplayManager）
- HBM / 阳光屏、DC 调光、防闪烁（LineageHardware）
- 触控采样率/灵敏度（LineageHardware）
- 分辨率切换（若内核/显示 HAL 暴露）

### 性能与散热
- 按 CPU 簇限制最大频率
- GPU 最小/最大频率与 governor
- 温控档位/thermal profile
- UFS 健康、`fstrim`、I/O 调度
- 网络 TCP congestion control、Wi-Fi 省电

### 自动化触发器
- 温度过高/回落
- 游戏启动/退出
- 充电旁路状态
- 刷新率变化
- 通知渠道/前台应用

### AI 增强
- 图片理解（已支持，需视觉模型）
- 自定义人设（已支持）
- 每周设备报告（耗电/存储/隐私/自动化执行记录）
- 根据使用习惯建议规则，而不是自动执行破坏性动作
- 工具调用结果的结构化回放与撤销

### 安全
- SELinux / Verified Boot / 内核模块审计（已只读展示）
- 应用行为时间线（需要 AppOps 历史权限，普通应用不可用）
- 域名过滤（DNS 级已实现；DoH/IP 直连会绕过）
