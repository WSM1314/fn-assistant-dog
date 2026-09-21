# Phase 1 设计：重建 FN 助手狗安卓源码工程骨架

范围：Gradle 工程骨架 + ①多账号认证（授权码 + deviceAuth + Keystore 加密 + Room）②今日商城（tab 名"今日商城" + 每日缓存 + WorkManager）③好友列表。
交付前需你确认本文件 §1 的 6 个决策点。

---

## 0.0 开工后的两处修正（已实施）

1. **工程根目录改为纯 ASCII**：中文父目录被 AGP 8.13.2 硬性拦截（`Failed to apply plugin 'com.android.internal.application' > Your project path contains non-ASCII characters`，见 R3）。经你选定，工程实际位置：
   `C:\Users\Administrator\AndroidProjects\fn-assistant-dog`
   原 `Desktop\堡垒启动器\安卓FN助手源码工程\` 只留一份 `README.md` 指路。本文与后续报告的 `docs\` 均在新位置。
2. **工具链落地实测**：JDK `Corretto 17.0.20.1` @ `dev-tools\jdk-17`；`Gradle 8.14.3` @ `dev-tools\gradle-8.14.3`；SDK @ `%LOCALAPPDATA%\Android\Sdk`（`platforms/android-36`、`build-tools/36.0.0`、`platform-tools 37.0.1`）。
   两个环境坑（已写进 `~/.gradle/gradle.properties` 与 `README`）：
   - 新版 cmdline-tools 的 sdkmanager 包名要写 **sdk-style 斜杠**（`platforms/android-36`），旧的 `platforms;android-37` 分号形式会被拆成两个包名报 `Package platforms not found`；
   - 直连 `repo1.maven.org`/`maven.google.com` 实测超时，须 `systemProp.*.proxyHost=127.0.0.1:7897`；且该代理对 `services.gradle.org` **间歇**不通（同一 URL 时 200 时 000），故 wrapper 任务设 `validateDistributionUrl=false`，distribution 留待网络正常时首次 `gradlew` 自取。

---

## 0. 勘察更正与新增取证（只读，均为实测）

### 0.1 Prompt 中参照文件路径与实际不符（已更正，功能一致）
| Prompt 写的 | 实际存在 |
|---|---|
| `Glow.../src/main/services/shop/ShopManager.ts` | `Glow-Launcher-2.4.1/src/main/managers/shop/ShopManager.ts` |
| `Glow.../src/renderer/src/store/modules/shop.ts` | `Glow-Launcher-2.4.1/src/renderer/pages/shop.ts` |
| `Aerial.../src/main/services/world-info-miadsc.ts` | `Aerial-Launcher-1.12.3/src/kernel/core/world-info-miadsc.ts` |
| `resources.ts` | `Aerial-Launcher-1.12.3/src/config/constants/resources.ts` + `src/lib/parsers/resources.ts` |

### 0.2 从原 APK dex 补到的硬事实（重建时逐字复刻，非我臆造）
- **Room `accounts` 表完整 DDL**（`.temp/fn-dex-strings/dex-ascii-strings.txt:14060-14061`）：
  `accountId TEXT PK, displayName TEXT NOT NULL, encryptedCredentials BLOB NOT NULL, accountStatus TEXT NOT NULL, accessTokenExpiresAt INTEGER NOT NULL, refreshTokenExpiresAt INTEGER NULL, lastRefreshAt INTEGER NOT NULL, lastError TEXT NULL, vbucksBalance INTEGER NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL`
- 第二张表 `profile_snapshots(accountId PK, snapshotJson, updatedAt)` 外键级联到 accounts（同文件 :14062）→ 属第二批功能，**本 phase 不建**（避免建了不用）。
- 加密：`AndroidKeyStore`（:13759）+ `AES/GCM/NoPadding`（:13662）+ `KeyGenParameterSpec`（:15871）— 与 F1 一致。
- 端点基址（:24531-24539，全部实测存在于 dex 常量池）：
  ```
  https://account-public-service-prod.ol.epicgames.com/account/api/
  https://fngw-mcp-gc-livefn.ol.epicgames.com/fortnite/api/
  https://friends-public-service-prod.ol.epicgames.com/friends/api/v1/
  https://user-search-service-prod.ol.epicgames.com/api/v1/search/
  https://fortnite-api.com/v2/shop
  https://fortnite-api.com/v2/cosmetics/br/search/all
  https://www.epicgames.com/id/api/redirect?clientId=3f69e56c7649492c8cc29f1af08a8a12&responseType=code
  ```
- 路径模板：`public/account/{accountId}/deviceAuth`、`.../deviceAuth/{deviceId}`（:25627-25628）、`oauth/token`（:25261）、`{accountId}/friends/{targetAccountId}`（:26709）、`account/detail/{accountId}`（:22762）。
- grant 常量：`authorization_code`（:23037）、`device_auth`（:23475）、错误码 `invalid_device_auth`（:24648）；字段 `deviceId`/`deviceSecret`（:23473-23474）、`createDeviceAuth`/`deleteDeviceAuth`（:23330/:23456）。
- **日志脱敏正则**（:12740，照抄进我们的错误/日志层）：
  `(?i)\b(auth(?:orization)?_?code|access_?token|refresh_?token|device_?id|secret)\b["']?\s*[:=]\s*["']?[^\s,;"']+`
- 文案取证（`.temp/fn-dex-strings/dex-cjk-clean.txt`）：`获取授权码`、`先登录 Epic，再在同一浏览器会话中获取授权码。`、`通过 Epic 官方页面取得授权码后添加`、`一次最多添加 20 个账号`、`请至少输入一个授权码`、`授权码中存在重复项`、`今日商城没有可展示的商品`、`好友服务暂时不可用`、`档案响应缺少完整的 common_core 数据`，以及免责声明：
  `助手狗是非官方 Android 客户端，与 Epic Games 没有隶属、授权或合作关系。` + `请妥善保管 Epic 账号、授权码、Device Auth 和设备登录信息…Epic 可能根据其服务条款限制非官方客户端。`
  → **关键结论：原 App 的"添加账号"是多行文本框、一次粘贴多个授权码批量绑定（上限 20），不是单账号对话框。** 重建沿用此交互。

### 0.3 本机环境实测（影响验收口径）
- 原本机 `java`/`gradle`/`adb` 全部不存在，`ANDROID_HOME` 空 → 已按你选定的方案安装到工程目录外：
  `C:\Users\Administrator\dev-tools\jdk-17`、`...\dev-tools\gradle-8.14.3`、SDK `C:\Users\Administrator\AppData\Local\Android\Sdk`（证据见验收报告 §工具链）。
- 网络：`dl.google.com` 直连通；`services.gradle.org`/`repo1.maven.org`/`maven.google.com` 须经 `127.0.0.1:7897`（gradle.properties 里已按此写 `systemProp`）。直连 Corretto 会卡死，必须走代理。
- 仓库可用性实测（POM HEAD 200，逐个验证过）：`compose.ui 1.9.2`、`material3 1.4.0`、`navigation-compose 2.9.1`、`activity-compose 1.10.1`、`lifecycle-runtime-compose 2.9.1`、`room 2.8.1(runtime/ktx/compiler)`、`work-runtime-ktx 2.10.2`、`datastore-preferences 1.1.7`、`retrofit 2.11.0 + converter-gson`、`okhttp 4.12.0 + logging-interceptor`、`gson 2.11.0`、`kotlinx-coroutines-android 1.10.2`、`coil-compose 2.7.0`、`material-icons-extended 1.7.8`；`AGP 8.13.2`、`Gradle 8.14.3`、`Kotlin 2.2.21`、`KSP 2.2.21-2.0.5`。

---

## 1. 需你确认的 6 个决策点

| # | 决策 | 我的选择 | 理由 |
|---|---|---|---|
| D1 | applicationId | `com.fnassistantdog`，debug 构建加后缀 → `com.fnassistantdog.debug` | 手机已装原版，同 applicationId + 不同签名会 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。加 `.debug` 后缀可与原版共存，且**不覆盖、不删除**你手机上的原版数据 |
| D2 | 底部导航 | Phase 1 三个 tab：**今日商城 / 好友 / 账号**；Phase 3 加第四个 **警报** | MVP 只有这四件。设置页本批不做（原 App settings 属第二批），免责声明放"账号"页顶栏「关于」对话框 |
| D3 | 代码包名 | `applicationId=com.fnassistantdog` / `namespace=code 包=com.example.fn_app` | 逐字复刻 F1（"应用包名 com.fnassistantdog、代码包 com.example.fn_app"）。`:core` 模块用 `com.fnassistantdog.core`（纯逻辑，不参与 APK 身份） |
| D4 | 模块划分 | 两模块：`:app`（Android/Compose）+ `:core`（纯 JVM，OkHttp/Retrofit/Gson/协程，**零 `android.*` import**） | Phase 4 走 KMP 时，`:core` 整块换成 `commonMain` 即可，UI 与平台实现各留两端。代价：多一个 `CredentialCipher`/`SettingsStore` 接口。不做则 phase 4 需重写网络层 |
| D5 | SDK 版本 | `minSdk 24 / targetSdk 36 / compileSdk 36`，AGP 8.13.2 + Gradle 8.14.3 + Kotlin 2.2.21 | 你已选。与 F1 唯一偏差：targetSdk 37→36（纯自用 sideload 无影响）。要追平 37 需 AGP 9.x（DSL 不确定度高，试错成本大） |
| D6 | DI 框架 | **不引 Hilt**，用手写 `AppContainer`（CompositionLocal 注入） | Hilt 不在 F1 栈里（dex 无 dagger 常量），且属"未列明重量级依赖"。约束 2 |

---

## 2. 依赖清单（逐项理由，全部 §0.3 已验证存在）

`:core`（`java-library` + kotlin-jvm）
| 依赖 | 版本 | 理由 |
|---|---|---|
| okhttp | 4.12.0 | F1 指定；15-20s 超时、TLS、错误体读取 |
| retrofit + converter-gson | 2.11.0 | F1 指定；`suspend` 接口 |
| gson | 2.11.0 | F1 指定；Epic/fortnite-api 返回字段不规整，Gson 宽松反序列化优于 kotlinx.serialization |
| kotlinx-coroutines-core | 1.10.2 | Retrofit suspend / Room Flow 的运行时前提 |

`:app` 追加
| 依赖 | 版本 | 理由 |
|---|---|---|
| compose ui / ui-tooling-preview / material3 / material-icons-extended | 1.9.2 / 1.9.2 / 1.4.0 / 1.7.8 | F1 指定 Material3 1.4.0（= compose 1.9 系） |
| activity-compose | 1.10.1 | 单 Activity Compose 入口 |
| navigation-compose | 2.9.1 | F1 指定；8 条路由本 phase 落 4 条 |
| lifecycle-runtime-compose / lifecycle-viewmodel-compose | 2.9.1 | ViewModel + `collectAsStateWithLifecycle` |
| room-runtime / room-ktx / room-compiler(KSP) | 2.8.1 | F1 指定；`accounts` 表 |
| work-runtime-ktx | 2.10.2 | 每日商城刷新（Phase 3 复用于警报） |
| datastore-preferences | 1.1.7 | 商城 JSON 缓存 + 缓存日 key + 设置开关（F1 栈含 DataStore） |
| coil-compose | **2.7.0**（非 coil3） | 商城卡片/详情大图必须网络取图；选 2.x 因 API 确定性高、与 compose 1.9 兼容 |

**不引入**：Hilt、Koin、Paging、Timber、kotlinx-serialization、Ktor、accompanist、Firebase（原 App 无，且属未列明依赖）。

---

## 3. 文件清单（Phase 1，约 48 个新文件，全部在 `安卓FN助手源码工程/fn-assistant-dog/`）

### 3.1 构建
```
settings.gradle.kts                 pluginManagement+dependencyResolutionManagement(含代理说明注释)、include :app :core
gradle.properties                   org.gradle.jvmargs、android.useAndroidX、kotlin.code.style、systemProp http/https 代理 + nonProxyHosts
gradle/libs.versions.toml           版本目录（§0.3 实测版本集中于此）
gradle/wrapper/gradle-wrapper.properties + gradle-wrapper.jar   8.14.3
gradlew / gradlew.bat               wrapper 脚本
.gitignore                          忽略 build/、.gradle/、local.properties、*.keystore（防凭据入仓）
build.gradle.kts                    根：插件 apply false
local.properties                    sdk.dir（不提交）
core/build.gradle.kts               kotlin-jvm + java-library + 5 个依赖
app/build.gradle.kts                com.android.application + compose + ksp + room schema 导出目录
app/src/main/AndroidManifest.xml    单 activity、INTERNET、usesCleartextTraffic=false、Theme
```

### 3.2 `:core` — 平台无关业务/网络（Phase 4 整体搬进 commonMain）
```
core/src/main/kotlin/com/fnassistantdog/core/
  EpicEndpoints.kt          6 个基址 + 路径模板 + clientId（§0.2 逐字取证）
  http/HttpClientFactory.kt 20s connect/read、无明文流量、脱敏 logging-interceptor（仅 debug 注入）
  http/BearerAuthenticator.kt 注入 Authorization: Bearer；401 → 回调上层刷新，仅重放 1 次
  api/EpicAccountApi.kt     POST oauth/token(authz_code / device_auth / refresh) · GET public/account/{id} · POST/DELETE deviceAuth
  api/EpicFriendsApi.kt     GET {accountId}/friends?includeSummary=true · POST/PUT/DELETE {accountId}/friends/{target} · GET 搜索
  api/FortniteApiApi.kt     GET https://fortnite-api.com/v2/shop?language=zh-Hans · GET /v2/cosmetics/br/search/all
  api/EpicMcpApi.kt         POST .../profile/{accountId}/client/{op}?profileId=&rvn=-1（Phase 1 仅声明，Phase 2 用 QueryProfile/Purchase/Gift）
  model/EpicAuthModels.kt   EpicTokenResponse(accountId,access_token,expires_in,tokenType…) · EpicDeviceAuthResponse(deviceId,deviceSecret)
  model/EpicAccountModels.kt AccountSummary(displayName,externalAuths…)
  model/FriendModels.kt      FriendList / FriendSummary(direction,favorite) / UserSearchResult
  model/ShopModels.kt        ShopResponse→data.entries[]（offerId,namespace,price,finalPrice,giftable,images[],items[],bundleOffers…）
  auth/EpicAuthRepository.kt  ★核心链路：code→token→deviceAuth→持久化；device_auth 刷新；凭据失效判定
  auth/CredentialModels.kt   StoredCredentials(accessToken,refreshToken,deviceId,deviceSecret,accountId,expiresAt)
  auth/Ports.kt              interface CredentialCipher(encrypt/decrypt ByteArray) · interface AccountVault(load/save/delete/ids) · interface Clock
  shop/ShopRepository.kt     /v2/shop → 归一化 ShopEntry 列表 + 按 UTC 日缓存读写（注入 ShopCache 端口）
  shop/ShopCache.kt          interface（app 侧用 DataStore 实现）
  friends/FriendsRepository.kt 好友列表/搜索/申请/接受/移除 + 错误归一
  error/EpicFailure.kt        统一错误：HTTP 状态 + 服务端 errorMessage 明文 + 网络分类（不可达/超时/DNS）
  error/Redactor.kt           §0.2 脱敏正则（写日志/错误前统一过一遍）
core/src/test/kotlin/…        RedactorTest · ShopRepositoryParseTest(fixture JSON) · EpicAuthRepositoryTest（假 Cipher/假 Vault/假 Clock）
```

### 3.3 `:app` — Android/Compose
```
app/src/main/kotlin/com/example/fn_app/
  FnDogApp.kt                 Application：建 AppContainer、触发 WorkManager 排期
  di/AppContainer.kt          手写依赖图 + CompositionLocalProvider
  MainActivity.kt             enableEdgeToEdge + setContent { FnDogTheme { FnNavHost() } }
  crypto/AndroidKeystoreCipher.kt  ★AndroidKeyStore AES/GCM/NoPadding：12B IV 前置写入 BLOB；密钥不落盘、不可导出
  data/FnDogDatabase.kt        Room(version=1, exportSchema=true)
  data/AccountEntity.kt        @Entity("accounts") 逐字段复刻 §0.2 DDL（含 nullable 与默认）
  data/AccountDao.kt           Flow<List<AccountEntity>> · upsert · delete · updateStatus/LastError/Vbucks
  data/AccountVaultImpl.kt     :core 的 AccountVault 实现（Entity ↔ StoredCredentials 加解密）
  data/DataStoreShopCache.kt   :core 的 ShopCache 实现（key=shop_json_utc_<yyyyMMdd>，只留最近 3 天）
  data/SettingsStoreImpl.kt    Wi-Fi-only 开关、当前账号 id
  work/ShopRefreshWorker.kt    CoroutineWorker：拉 /v2/shop 写缓存；网络失败→retry + 明确 errorMessage 落 lastError
  work/WorkSchedulers.kt       每日 1 次周期任务 + 冷启动补偿（不引 WorkManager 之外的调度库）
  ui/theme/{Color.kt,Theme.kt,Type.kt}   Material3，深色默认（原 App 观感）
  ui/nav/{FnDestinations.kt,FnNavHost.kt}  today / friends / account / account/detail/{accountId} + 对话框路由
  ui/common/{StateViews.kt,ErrorBanner.kt,ConfirmDialog.kt,RedactedTokenText.kt}
  ui/picker/AccountPickerHost.kt  全局"当前账号"切换器（赠送/余额都跟着它）
  ui/todayshop/{TodayShopScreen.kt,TodayShopViewModel.kt,ShopGrid.kt}          ★tab 文案"今日商城"
  ui/friends/{FriendsScreen.kt,FriendsViewModel.kt,FriendRow.kt,AddFriendSheet.kt}
  ui/account/{AccountScreen.kt,AccountViewModel.kt,AddAccountsScreen.kt,AboutDialog.kt}
      AddAccountsScreen = 多行授权码输入（每行一个）+ 批量绑定 + 上限 20 + 重复项校验（复刻原交互）
  copy/Copy.kt                硬编码中文文案集中处（沿用原 App 取证措辞 + 免责声明）
  res/values/{strings.xml(仅 app_name 等),themes.xml}
  res/xml/backup_rules.xml, data_extraction_rules.xml   排除 Room/Keystore 备份（防凭据外流）
docs/phase-1-设计.md（本文件）· docs/phase-1-验收报告.md（后续产出）
```

---

## 4. 数据流

### 4.1 认证（F1 全套复刻）
```
账号页 →「获取授权码」：Intent(ACTION_VIEW) 打开
  https://www.epicgames.com/id/api/redirect?clientId=3f69e56c…&responseType=code
  （浏览器不可达/域名被阻断 → 走 §4.4 的错误回显，不改链路）
用户从浏览器结果里复制 code（可多行粘贴，每行 1 个，上限 20）
  ↓ AddAccountsViewModel
EpicAuthRepository.bind(code):
 1) POST {ACCOUNT}/oauth/token   grant_type=authorization_code  code=…
      → access_token, account_id, expires_in
 2) GET  {ACCOUNT}/public/account/{accountId}  → displayName（写 accounts.displayName）
 3) POST {ACCOUNT}/public/account/{accountId}/deviceAuth → deviceId + deviceSecret
 4) StoredCredentials → JSON → CredentialCipher.encrypt() (Keystore AES/GCM)
    → accounts.encryptedCredentials BLOB + accessTokenExpiresAt + createdAt/updatedAt
    （授权码本身：不落盘、不进日志；日志过 Redactor）
后续 refresh(accountId):
 grant_type=device_auth（device_id+device_secret）→ 新 access_token → 重新加密落库
 token 过期前 5 分钟主动刷新；401 → 刷新一次后重放一次（不自动重试扣款类请求，见约束 3）
凭据彻底失效 → accountStatus=NEEDS_REAUTH + lastError 明文 + UI 提示"须获取新的 Epic 授权码"（禁无限重铸）
```
待实测项（见 §6 R1）：`oauth/token` 是否要求 `Authorization: Basic base64(clientId:clientSecret)`。dex 里只有 clientId `3f69e56c…`，未见对应 secret 常量（`de933971312db67329ae0c5e8bb5721c` 经核对是 Room identity_hash 的巧合，不是凭据）。实施第一步用 `curl` 做**不含任何用户凭据**的匿名探测确定请求形状，再落码；不猜、不臆造。

### 4.2 今日商城
```
冷启动 / 每日 WorkManager / 下拉刷新
  ↓ ShopRepository.refresh()
GET https://fortnite-api.com/v2/shop?language=zh-Hans   （20s 超时；不可达即失败回显）
  → 归一化 List<ShopEntry>（含 offerId/giftable/价格/图片/子商品）
  → DataStoreShopCache：key 前缀 + UTC 日；保留最近 3 天；写 stale 标记
UI：缓存优先渲染 + 后台静默更新（断网也能看到上次内容）
失败：横幅显示服务端 errorMessage 或"网络不可达，显示的是 <UTC日> 的缓存"
```

### 4.3 好友
```
GET {FRIENDS}{accountId}/friends?includeSummary=true → 好友(含方向/昵称)
GET {USER_SEARCH}{platform}?q=<关键词> → 候选（显示 displayName，取 accountId）
POST/PUT/DELETE {FRIENDS}{accountId}/friends/{targetAccountId} → 申请 / 接受 / 移除
（Phase 2 的赠送好友选择器直接复用 FriendsRepository + 账号切换器）
```

### 4.4 错误与网络判定纪律（约束 + network_facts）
- 分类：`NETWORK_UNREACHABLE` / `TIMEOUT` / `HTTP_4xx(带 errorMessage)` / `HTTP_5xx` / `PARSE`，UI 一律显示中文可读文案 + 原始服务端 `errorMessage`（不吞）。
- Epic 域名 403/超时 → 报告为环境阻断（附 curl 复现命令），**不动链路代码**。
- 所有写日志入口先过 `Redactor`（§0.2 正则）。

---

## 5. UI 线框（Material3，深色，单 Activity + Navigation Compose）

```
┌─ 今日商城（tab 文案＝"今日商城"）────────────┐
│ [账号切换器 ▾ 昵称·V-Bucks(phase2)]  [刷新 ⟳] │
│ ─ 状态条：更新自 09-20(UTC) · 缓存/在线 · 错误横幅 ─
│ ┌────────┐ ┌────────┐                        │
│ │ 大图   │ │ 大图   │   2 列网格（LazyVerticalGrid）
│ │ 名称   │ │ 名称   │   卡片：图/名称/类别/价格
│ │ ⛁950  │ │ ⛁1500 │   （giftable 角标 phase2 用）
│ └────────┘ └────────┘                        │
│ 空态：今日商城没有可展示的商品                  │
└──────────────────────────────────────────────┘
底部导航： 今日商城 | 好友 | 账号        （Phase 3 → + 警报）

┌─ 好友 ───────────────────────────────────────┐
│ [当前账号 ▾]                     [+ 添加好友]  │
│ 搜索框（昵称 → user-search）                   │
│ 分组：好友(3) / 待处理请求(1) / 已屏蔽(0)       │
│ 行：头像 昵称  <在线状态·平台>   [⋯ 接受/拒绝/移除]
└──────────────────────────────────────────────┘

┌─ 账号 ────────────────────────────────[关于 ⓘ]┐
│ 已保存账号（多账号，上限 20）                    │
│  ▸ 昵称A  凭据正常 · token 剩 3h20m   [刷新][删除]│
│  ▸ 昵称B  需重新授权 · lastError: …    [重新获取] │
│ ─────────────────────────────────────────────  │
│ [获取授权码并添加]                              │
│  提示：先登录 Epic，再在同一浏览器会话中获取授权码。│
│  多行输入框（每行一个授权码，一次最多 20 个）      │
│  校验：不能为空 / 不能包含空格 / 长度 / 重复项     │
│  [绑定 2 个账号]  ← 逐条处理，失败明说哪条        │
│ 关于对话框：非官方客户端免责声明 + Keystore 说明  │
└──────────────────────────────────────────────┘
```
交互约束：Phase 1 无资产写操作（购买/赠送在 Phase 2，届时长二次确认弹窗）。删除账号需确认对话框。

---

## 6. 风险与对策

| # | 风险 | 对策 |
|---|---|---|
| R1 | `oauth/token` 的 client 凭据形状未定（是否需 Basic） | 实施首步 `curl` 匿名探测 3 种组合，按真实响应定稿；不猜字段。若 Epic 域名被网络阻断 → 停下汇报（约束 4） |
| R2 | 授权码流验收需要你在场操作 Epic 登录 | 我把工程装到手机后，你亲手走一遍；我负责 logcat/DB 侧证据。你不在时该项标"未验证" |
| R3 | 工程父目录含中文（`安卓FN助手源码工程`） | 叶子目录用 ASCII（`fn-assistant-dog`）。骨架编译立刻验证；若 Gradle/KSP 对非 ASCII 路径报错，我会提出改 ASCII 路径（D5 之外的环境级变更，需你点头） |
| R4 | 手机 adb 需 USB 驱动（云服务器） | 装完 platform-tools 先跑 `adb devices`；识别不到则停下给你驱动指引，不改代码 |
| R5 | `fortnite-api.com` 慢 | 20s 超时 + 缓存优先 + 状态条明示；Phase 3 沿用同一策略 |
| R6 | Material3 1.4.0 / Kotlin 2.2 若与 AGP 8.13 有版本协商问题 | 报错信息逐条定位，同一问题最多 3 轮（约束 4），超限即汇报 |

---

## 7. 验收清单（每项都要留证据）
1. `gradlew :app:assembleDebug` → `BUILD SUCCESSFUL` 原文 + APK 路径/大小
2. `./gradlew :core:test` → 单测全绿（Redactor / shop JSON 解析 / 假凭据链路）
3. `adb devices` 列出你的手机 → `adb install -r` → `am start` → `logcat -d` 无 `FATAL EXCEPTION`/`AndroidRuntime`
4. 认证：你提供 1 个真实授权码 → 账号出现且昵称正确；DB 里 `encryptedCredentials` 非空 BLOB（`adb shell run-as` + sqlite 取证，只贴长度和十六进制前 8 字节，**不贴明文**）；点"刷新"→ device_auth 换新 token 成功（logcat 摘录，脱敏）
5. 今日商城：显示真实商品（截图说明 + `curl /v2/shop` 条目数与 UI 数一致）
6. 断网冷启动仍渲染缓存（飞行模式或关代理，截图 + 状态条文案）
7. 好友列表加载（截图 + 服务端返回条数与 UI 一致）
8. 遗留问题清单（含未验证项，明确区分"已做/已查/未验"）

---

## 8. 实施顺序（确认后按此推进，每步都可编译）
```
A 工具链就绪（进行中）→ B 骨架 + wrapper + 空 Compose 导航，先跑通 assembleDebug
C :core 端点常量 + §0.2 实测（curl）+ HTTP 层 → D Keystore 加密 + Room accounts
E 授权码/deviceAuth 全链路 + 添加账号页 → F 今日商城 + WorkManager 缓存
G 好友页 → H 装机冒烟 + 验收报告
```
每步完成即在终端留一份命令输出；B 之后每步都编译一次，不把编译错误攒到最后。
