# FN助手狗（fn-assistant-dog）

个人自用的 Fortnite 工具 App 源码工程：**Android（Kotlin/Compose）+ iOS（SwiftUI，TrollStore 自签安装）**。
非官方客户端，与 Epic Games 无隶属、授权或合作关系。

## 目录

| 路径 | 内容 |
|---|---|
| `app/` `core/` | Android 工程。`:core` 是纯 JVM 模块（Epic OAuth / MCP / fortnite-api 全部网络与业务逻辑，零 `android.*` 依赖），`:app` 是 Compose UI 与平台实现（AndroidKeyStore、Room、DataStore、WorkManager） |
| `docs/` | 各阶段设计与验收报告（中文） |
| `ios/` | iOS 端。当前是 Phase 4 的**构建管线验证探针**（商城接口连通、Keychain 往返、Epic 端点可达性、TrollStore 运行验证） |
| `.github/workflows/ios-ipa.yml` | macOS runner 上出 IPA：xcodegen → xcodebuild（关签名）→ ldid/codesign 伪签名 + entitlements → 打 `Payload/*.app` 成 IPA → 挂到 Release |

## 本地构建（Android）

```bash
export JAVA_HOME="<jdk-17>"
./gradlew :app:assembleDebug :core:test :app:testDebugUnitTest :app:lintDebug
# 产物 app/build/outputs/apk/debug/app-debug.apk （包名 com.fnassistantdog.debug，可与正式版共存）
```
测试含 8 个 Robolectric 用例：它们在 JVM 里真启动 `MainActivity` 与 Compose 树，只有 API 基址（进程内 MockWebServer）与加密实现（AndroidKeyStore 在 JVM 沙箱不可用）被替换。

## iOS IPA

1. 推 `ios/**` 或手动 dispatch `iOS IPA (TrollStore)` 工作流
2. 从 Release 页下载 `FnDog-ios.ipa`（公开直链，无需登录）
3. iPhone 上用 **TrollStore** 安装（iOS 15.0–16.6.1 支持，无需开发者证书/无需 Apple ID）
4. 打开 App → 右上「跑探针」，四项应为 OK

## 凭据与安全

- 用户侧凭据（access token、deviceAuth 的 deviceId/deviceSecret）一律经 **AndroidKeyStore AES/GCM/NoPadding**（iOS 侧为 Keychain `AfterFirstUnlockThisDeviceOnly`）加密后才落库；授权码仅内存内使用，不落盘、不进日志。
- 日志出口统一过脱敏正则（`core/.../error/Redactor.kt`）。
- 仓库里唯一的"凭据"是 Epic 的**公开 OAuth 客户端对**（各家 Fortnite 工具与原版 APK 都内置同一条），不含任何用户账号信息。
- 应用不提供账号中转服务器，也不接收他人凭据。Epic 可能按其服务条款限制非官方客户端。
