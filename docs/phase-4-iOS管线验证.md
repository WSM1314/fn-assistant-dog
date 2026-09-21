# Phase 4-A：iOS IPA 云构建管线验证（macOS runner → TrollStore 可装）

决策（你已确认）：构建环境 = **GitHub Actions macOS runner**；仓库 = **公开**；顺序 = **先验证管线**。
关键前提：**TrollStore 只免掉"证书与签名要求"，不免掉"必须在 macOS 上用 Apple 工具链编译"**。本机实测无嵌套虚拟化（`HyperVPresent=False`）、无 USB 透传、仅 VPC 内网 IP → 本地 macOS 虚拟机与模拟器都不可行，云构建是唯一实际路径。

## 管线（`.github/workflows/ios-ipa.yml`）
```
macos-15 (ARM runner, Xcode 16.4, iOS SDK 18.5)
  brew install xcodegen ldid
  xcodegen generate --spec ios/project.yml            # 不往仓库里塞 .xcodeproj
  xcodebuild -scheme FnDog -sdk iphoneos -configuration Release
      CODE_SIGNING_ALLOWED=NO  ARCHS=arm64  -derivedDataPath dd
  ldid -SFnDog.entitlements <binary>                  # 伪签名 + 注入 entitlements，无需任何证书
  zip -qry FnDog-ios.ipa Payload                      # 条目必须是 Payload/FnDog.app/...
  gh release create ios-<sha> FnDog-ios.ipa           # 公开直链，无需登录即可下载
```
最近一次成功运行：`35556855605`（commit `124c114`），Release 标签 `ios-124c114c0b3a38956e2ad7a3298302c56c8a0337`。

## 这一轮真正暴露并修掉的三个问题（都不是"配置调一下"，都是代码/打包缺陷）
| # | 现象 | 根因 | 修法 |
|---|---|---|---|
| 1 | xcodebuild 直接失败 | `SWIFT_VERSION: "5.9"` 不是合法语言模式（只有 `4.2/5.0/6.0`） | 改 `"5.0"` |
| 2 | `ProbeView.swift:33:17: error: generic parameter 'V' could not be inferred` | `ForEach(_:id:)` 的闭包只接受**单个**元素参数，我写成 `{ _, item in }` | `ProbeResult: Identifiable` + `ForEach(results) { row($0) }` |
| 3 | IPA 解开后条目是 `FnDog.app/...`，**没有 `Payload/`** | `cd payload && zip ../x.ipa .` 存的是目录内相对路径 | 从上级目录 `zip -qry FnDog-ios.ipa Payload` |

第 3 项是**下载回来解包才发现的**：如果只看 CI 绿勾就交付，TrollStore 会在安装那步拒收。

## 产物核验（本地解析 `Payload/FnDog.app/FnDog`）
```
magic cf a8 ed fe = thin arm64（CPU_TYPE_ARM64 0x100000c）        ✓ 真机二进制，不是模拟器
LC_BUILD_VERSION  platform=2(iOS)  min=16.0  sdk=18.5             ✓ 满足 ≤ iOS 16.3
LC_CODE_SIGNATURE dataoff=184528 size=3344                        ✓ ldid 已注入签名（无 _CodeSignature 目录属 ldid 正常表现）
Info.plist 含 CFBundleIdentifier=com.fnassistantdog.ios            ✓
IPA 大小 39,896 B，条目：Payload/FnDog.app/{FnDog,Info.plist,PkgInfo}
```
本地副本：`Desktop\堡垒启动器\安装包\FN助手狗-iOS-探针-124c114.ipa`
手机直链：`https://github.com/WSM1314/fn-assistant-dog/releases/download/ios-124c114c0b3a38956e2ad7a3298302c56c8a0337/FnDog-ios.ipa`

## 探针 App 验证什么（不是业务功能，是 iOS 侧四条地基）
1. **能否运行**：TrollStore 装完打开不崩 = 伪签名 + entitlements 组合成立
2. **Keychain 写入/读回**：对应安卓侧 AndroidKeyStore 的角色（凭据只进 Keychain，`AfterFirstUnlockThisDeviceOnly`）
3. **今日商城接口**：原生 URLSession 直连 `fortnite-api.com/v2/shop?language=zh-Hans`，回显条目数 / 可赠送数 / UTC 日 —— 顺带证明 iOS 无 CORS 问题
4. **Epic 端点可达性**：匿名请求 `account-public-service-prod.../public/account/me`，**401/403 即代表网络通**（不是失败）

## 待办（按顺序）
- [ ] 你在 iPhone 上用 TrollStore 安装这个 IPA → 打开 → 点右上「跑探针」→ 回传四行结果（若安装即报错，回传报错原文，那是 entitlements/签名组合问题）
- [ ] 管线成立后：把 `:core` 提升为 KMP `commonMain` 共享模块（Epic OAuth / deviceAuth / MCP / 商城模型），iOS UI 用 SwiftUI 复刻今日商城 + 账号 + 购买/赠送 + STW 警报
- [ ] 仍待做：安卓 phase 2（购买/赠送）与 phase 3（STW 警报）——iOS 功能集与它们对齐，所以先后顺序会直接影响 iOS 端要复刻几屏

## 备注：本轮踩到的环境坑（已写进记忆）
- GitHub MCP 连接器只有读能用，`push_files` 恒超时；改用 `gh` 设备码授权（需 `workflow` scope，否则含 `.github/workflows/*` 的推送被 GitHub 拒收）
- git 走本机代理必须 `-c http.proxy=http://127.0.0.1:7897`；`HTTPS_PROXY` 环境变量对 Git for Windows 无效（schannel / openssl 两后端都握手失败）
- `api.github.com` 经共享代理会间歇 `unexpected EOF` / 限流；构建日志改用浏览器会话读取（步骤名 + 页内 `innerText` 过滤 `error:`）
