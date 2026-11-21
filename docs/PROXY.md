# Nanaya Shizuku 兼容代理

目标：不安装 Shizuku/Stellar，由 Nanaya 自己启动 shell 服务，兼容现有 Shizuku 客户端应用。

## 架构

```text
Nanaya App
  ├─ 管理器 UI
  ├─ 权限数据库
  ├─ 调用日志
  └─ 启动器
        │ 无线 ADB / Root
        ▼
Shell 进程 (uid=2000)
  ├─ NanayaProxyServer
  ├─ IShizukuService 兼容层
  ├─ 权限校验
  └─ 客户端 Provider 发现 / Binder 分发
```

## 阶段

### 阶段 1：协议与权限模型
- [x] 代理 AIDL 骨架
- [x] 按 UID 的权限存储
- [ ] shell 启动器
- [ ] Binder 服务

### 阶段 2：Shizuku 核心兼容
- [ ] `pingBinder`
- [ ] `getVersion`
- [ ] `getUid`
- [ ] `checkSelfPermission`
- [ ] `requestPermission`
- [ ] `attachApplication`
- [ ] `transactRemote`

### 阶段 3：完整兼容
- [ ] `newProcess`
- [ ] 用户服务
- [ ] 一次性授权
- [ ] 客户端 Provider 发现
- [ ] 调用日志与风险提示

## 关键约束

- 必须通过无线 ADB 或 Root 启动 shell 进程
- 不安装 Shizuku/Stellar 时，Nanaya 必须自己实现 Shizuku 兼容 Binder
- 部分应用会额外检查 Shizuku 包或签名，需要逐项兼容
- 代理服务是高权限攻击面，必须校验调用 UID/PID/权限
- 授权码、命令参数和调用记录不能泄露敏感内容

## 首批测试

- Shizuku 官方示例
- 开源 Shizuku 工具应用
- 只使用 `checkSelfPermission` / `requestPermission` 的应用
- 使用 `transactRemote` 的应用
- 使用 `newProcess` 的应用

## 共存策略

本方案默认不安装 Shizuku/Stellar，避免 Binder 抢占和权限库分裂。  
如果检测到已安装，提示用户停用其中一个代理。
