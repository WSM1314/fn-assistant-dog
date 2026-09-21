# Phase 1 交付报告（进行中：静态验证全绿，真机项待手机）

工程：`C:\Users\Administrator\AndroidProjects\fn-assistant-dog`
生成时间：2026-09-20 23:41（本机）
状态：**编译 / 单测 / lint / APK 产物全部通过；装机冒烟与真机认证验收待手机接入**（`adb devices` 当前为空）。

---

## ① 阶段设计
见同目录 `phase-1-设计.md`（已获批准：D1 applicationId `.debug` 后缀 / D2 先 3 个 tab / D3 代码包 `com.example.fn_app` / D4 `:app`+`:core` 双模块 / D5 AGP 8.13.2 + compileSdk 36 / D6 不引 Hilt）。
设计批准后发生的两处实施级修正已回写在该文件 §0.0（工程搬到纯 ASCII 路径、工具链落地细节）。

## ② 变更文件清单（全部新增，54 个 Kotlin 文件 / 4335 行）

### 构建骨架
| 文件 | 说明 |
|---|---|
| `settings.gradle.kts` | 仓库顺序：aliyun 镜像 → google → mavenCentral（本机实测代理对 maven 主机间歇掐 TLS）；include `:app` `:core` |
| `build.gradle.kts` | 五个插件 `apply false` + wrapper 任务设 `validateDistributionUrl=false` |
| `gradle.properties` | jvmargs / AndroidX / nonTransitiveRClass；代理不放这里（属机器环境，写在 `~/.gradle/gradle.properties`） |
| `gradle/libs.versions.toml` | 版本目录：AGP 8.13.2 / Kotlin 2.2.21 / KSP 2.2.21-2.0.5 / compose-ui 1.9.2 / material3 1.4.0 / room 2.8.1 / work 2.10.2 / retrofit 2.11.0 / okhttp 4.12.0 / gson 2.11.0 / coil 2.7.0 |
| `gradle/wrapper/*`, `gradlew(.bat)` | Gradle 8.14.3 wrapper（distribution 已在本机自举缓存） |
| `local.properties`（不入库）、`.gitignore`、`app/proguard-rules.pro` | 本机 SDK 路径 / 忽略 build 与 keystore / release 混淆占位 |
| `core/build.gradle.kts` | 纯 JVM `java-library`，**零 `android.*` 依赖**，为 phase 4 KMP 预留 |
| `app/build.gradle.kts` | applicationId `com.fnassistantdog`，debug 加 `.debug` 后缀；compile/targetSdk 36、minSdk 24、JVM 17；Room schema 导出目录 |

### `:core` — 平台无关业务与网络（可整体搬进 commonMain）
| 文件 | 说明 |
|---|---|
| `EpicEndpoints.kt` | 5 个基址 + 授权码页 URL，全部取自 dex 常量池取证 |
| `EpicClient.kt` | Basic 客户端凭据常量（dex:13890 那条 base64 字面量），并注明实测证据链 |
| `api/EpicAccountApi.kt` | `oauth/token`（authorization_code / device_auth）、`public/account/{id}`、`deviceAuth` 创建与删除 |
| `api/EpicFriendsApi.kt` | `/summary`、`POST/DELETE {accountId}/friends/{target}`、`EpicUserSearchApi` |
| `api/FortniteApi.kt` | `GET v2/shop?language=zh-Hans` |
| `model/EpicAuthModels.kt` | token / deviceAuth / account DTO，snake 与 camel 双写法兼容 |
| `model/ShopModels.kt` | 按真实抓包形状建模（`brItems`、`finalPrice`、`bundle`、`newDisplayAsset.renderImages`） |
| `model/FriendModels.kt` | `/summary` 的 friends/incoming/outgoing/blocked 与 user-search entities |
| `auth/Ports.kt` | `CredentialCipher`/`Clock`/`AccountStore` 端口 + `AccountRecord` + 状态常量（dex 取证的 5 个取值） |
| `auth/StoredCredentials.kt` | 进 BLOB 的凭据结构（accessToken/refreshToken/deviceId/deviceSecret/过期时间） |
| `auth/EpicAuthRepository.kt` | 授权码→token→deviceAuth→密文入库；device_auth 刷新；批量绑定与逐条校验（上限 20） |
| `auth/EpicSession.kt` | 当前账号 + 有效 token 的持有者（拦截器同步取用） |
| `shop/ShopModels.kt` | `ShopCard`/`ShopSnapshot`（含 `degraded`）/`ShopCache` 端口 |
| `shop/ShopRepository.kt` | 缓存优先加载、失败回退带原因、UTC 日 key、`toCard` 归一化（逐条平移 Glow） |
| `friends/FriendsRepository.kt` | 好友概览 / 发请求 / 接受 / 移除 / 用户搜索 |
| `http/HttpFactory.kt` | 20s 超时；`BearerInterceptor`（仅 Epic 域名、不覆盖显式 Authorization）；`EvidenceInterceptor` 证据日志 |
| `http/ApiCall.kt` | `apiCall`/`apiCallEmpty`：超时/DNS/握手分类 + 解析 `{errorCode,errorMessage}` 原文透传 |
| `error/EpicFailure.kt` | 5 类失败 + `displayMessage()` 中文回显 |
| `error/Redactor.kt` | 原 App 脱敏正则（额外纳入裸 `code=` 与 `deviceSecret=`） |
| `error/LocalMessages.kt` | 本地校验文案（措辞对齐原 App） |
| `util/UtcDay.kt` | 只用 `java.text`/`java.util`（minSdk 24 无 `java.time`），UTC 日工具 |

### `:app` — Android / Compose
| 文件 | 说明 |
|---|---|
| `MainActivity.kt` / `FnDogApp.kt` | 单 Activity + edge-to-edge；Application 建 `AppContainer` 并排每日任务 |
| `crypto/AndroidKeystoreCipher.kt` | AndroidKeyStore AES/GCM/NoPadding，256 位不可导出密钥，密文 = 12B IV 前缀 + GCM |
| `data/AccountEntity.kt`/`AccountDao.kt`/`FnDogDatabase.kt`/`AccountStoreImpl.kt` | Room `accounts` 表（DDL 逐字符复刻取证）+ 端口实现 |
| `data/Prefs.kt` | DataStore 商城缓存（`shop_json_<UTC日>`，保留 3 天）+ 当前选中账号 |
| `di/AppContainer.kt`/`FnViewModelFactory.kt` | 手写依赖图 + CompositionLocal + ViewModel 工厂（无 Hilt） |
| `work/ShopRefreshWorker.kt` | CoroutineWorker 每日下载，失败退避重试 2 次 |
| `ui/nav/*`、`ui/theme/*`、`ui/common/*` | 3 个 tab 导航、Material3 深/浅色、Empty/Loading/Error/Confirm/AccountPicker 组件 |
| `ui/todayshop/*` | 今日商城：自适应网格 + 稀有度配色 + 折扣价 + 状态行 + 手动刷新 |
| `ui/friends/*` | 好友：账号切换 + 三段分组 + 搜索加友 + 接受/拒绝/移除 |
| `ui/account/*` | 账号：多行授权码批量绑定 + 刷新/删除（含二次确认）+ 关于对话框（免责声明） |
| `copy/Copy.kt` | 硬编码中文文案集中处（含复刻的免责声明） |
| `res/*` | 应用名、深色启动壳、vector 图标、`data_extraction_rules.xml`（云备份与设备迁移全排除） |
| `core/src/test/*` + `shop-fixture.json` | 27 条 JVM 单测；fixture 由真实抓包裁出 |

### 本地运行验证（用户批准引入 Robolectric 后新增）
| 文件 | 说明 |
|---|---|
| `app/src/test/.../TestFnDogApp.kt` | 测试用 Application 子类 + `FakeEpic`（MockWebServer 按路径应答并录制请求）+ `ReversibleCipher`（JVM 沙箱无 AndroidKeyStore）+ 与真实形状一致的响应样本 |
| `app/src/test/.../Waiters.kt` | `pumpUntil`：显式泵 Robolectric 的暂停主 looper，超时把"Mock 收到过哪些请求"写进异常信息 |
| `app/src/test/.../AppSmokeTest.kt` | 真启动 MainActivity：三个 tab、商城条目渲染、状态行、服务端错误原文透传、关于对话框免责声明、商城请求不带 Authorization、首屏语义树文本留证 |
| `app/src/test/.../AccountBindFlowTest.kt` | 端到端绑定：UI 粘贴授权码→`oauth/token`(Basic)→`deviceAuth`(Bearer)→Room 密文行（断言 BLOB 里查不到明文令牌/设备密钥）→好友页渲染；批量多行的逐条校验回显 |
| `di/AppContainer.kt`（改） | 开两个注入点：`bases`（四个 API 基址）与 `cipherFactory`，默认仍是 PROD 基址 + AndroidKeystoreCipher |
| `FnDogApp.kt`（改） | 实现 `Configuration.Provider`（见 §③6），`buildContainer` 改 open 供测试子类替换 |
| `AndroidManifest.xml`（改） | 摘掉 `androidx.work.WorkManagerInitializer`（lint `RemoveWorkManagerInitializer` 要求，与 Configuration.Provider 配套） |
| `build.gradle.kts`(app)/`libs.versions.toml`（改） | `testOptions.unitTests.isIncludeAndroidResources`；新增 test-only 依赖：robolectric 4.17、androidx.test:core 1.7.0、androidx.test.ext:junit 1.3.0、compose ui-test-junit4 1.9.2、ui-test-manifest 1.9.2、mockwebserver 4.12.0（已获你批准） |
| `core/src/test/.../BearerInterceptorTest.kt` | 逐条证明 token 注入规则：Epic 域名注入 / fortnite-api 不注入 / 调用点显式 Basic 不被覆盖 / 无 token 不写头 |

## ③ 验证证据（已完成的四件）

**1) 编译零错误**
```
$ ./gradlew :app:assembleDebug :core:test :app:testDebugUnitTest :app:lintDebug --console=plain
BUILD SUCCESSFUL in 52s        # 2026-09-21 09:38，含真跑 App 的 Robolectric 用例
```

**2) APK 产物**
```
app/build/outputs/apk/debug/app-debug.apk   20,542,413 字节（2026-09-21 09:38）
$ aapt2 dump badging app-debug.apk
package: name='com.fnassistantdog.debug' versionCode='1' versionName='1.0.0-debug'
         compileSdkVersion='36' platformBuildVersionCode='36'
targetSdkVersion:'36'    application-label:'FN助手狗'
# merged manifest：android:minSdkVersion="24"
```
D1 生效：debug 包名 `com.fnassistantdog.debug`，可与手机上已装原版共存，不需要卸载。

**3) 单测 35 条全绿（27 条 :core 纯逻辑 + 8 条 :app 真启动 App）**
```
EpicAuthRepositoryTest   tests=8 fail=0     AppSmokeTest          tests=5 fail=0
RedactorTest             tests=4 fail=0     AccountBindFlowTest   tests=3 fail=0
BearerInterceptorTest    tests=4 fail=0     ShopCacheTest         tests=4 fail=0
ShopNormalizeTest        tests=4 fail=0     UtcDayTest            tests=3 fail=0
                         合计 tests=35  失败=0
```
:core 覆盖：授权码逐条校验（空格/长度/重复/上限 20）→ 绑定成功写密文且 BLOB 里查不到明文 token → 设备凭据缺失时标 EXPIRED 且不空刷 → 令牌临期自动 device_auth 换新并回写密文 → 有效令牌不被无谓刷新；商城缓存命中不打网络 / 网络失败回退缓存并带出原因 / 无缓存时抛错不静默 / 按 UTC 日写并裁剪旧日；真实 fixture 的捆绑包折扣、`brItems` 缺失跳过、TBD 名跳过；token 注入规则四条。

**6) 本地真跑 App（Robolectric，JVM 内跑真实 MainActivity + Compose + Room + OkHttp）**
Epic 与 fortnite-api 的基址换成进程内 MockWebServer、AndroidKeyStore 换成可逆假加密，其余全用真实实现。跑通并留证的运行时事实：

```
$ ./gradlew :app:testDebugUnitTest
AppSmokeTest          5 tests 0 failures      # 启动、三 tab、商城渲染、错误透传、免责声明
AccountBindFlowTest   3 tests 0 failures      # 授权码→token→deviceAuth→Room 密文→好友页
```
- 首屏语义树（测试内 `printToString()` 落进 `app/build/test-results/.../TEST-com.example.fn_app.AppSmokeTest.xml` 的 system-out）：
  ```
  Node #615  Text = '[今日商城]'
  Node #619  ContentDescription = '[刷新]'  Role = 'Button'
  Node #621  Text = '[更新自 2026-09-20 (UTC) · 在线]'
  Node #626  CollectionInfo / VerticalScrollAxisRange=ScrollAxisRange(value=0.0, maxValue=100.0)   # 商城网格
  ```
- 真实请求形状被录制并断言：`POST /account/api/oauth/token` 带 `Authorization: Basic …` 且表单体含 `grant_type=authorization_code&code=…`；随后 `POST /account/api/public/account/{id}/deviceAuth` 带 `Authorization: Bearer <access_token>`；`GET /v2/shop` 不带任何 Authorization。
- 落库断言：`accounts` 恰一行，`accountId/displayName/accountStatus=ACTIVE/accessTokenExpiresAt` 正确，BLOB 带加密前缀且**明文令牌与设备密钥都不在 BLOB 里**。
- 这一轮还暴露两处真问题并当场修掉：① WorkManager 按需初始化在没有 `Configuration.Provider` 时直接 `IllegalStateException`（真机上由 startup provider 兜住，但显式化更稳）；② 修完 lint 立刻报 `RemoveWorkManagerInitializer`，遂在 manifest 摘掉默认 initializer —— 即 lint 与运行时互相咬合，说明两处都必须成对存在。

**单测当场抓到 1 个真实缺陷**：`UtcDay.fromIso` 用了 `Regex.matches()`（全串语义），对 `"2026-09-20T00:00:00Z"` 返回 null → 商城缓存 key 会静默退回时钟值。已修（截前 10 位再比）并加回归用例。

**4) lint**
`./gradlew :app:lintDebug` → **0 errors, 17 warnings**（报告：`app/build/reports/lint-results-debug.txt`）。
过程中修掉 lint 报的 3 个真实错误：`AccountScreen` 里 `mutableStateOf` 未 `remember`（切 tab 丢对话框状态）、以及 `Configuration.Provider` 引入后必需的 `RemoveWorkManagerInitializer`。
剩余 17 条：16 条是"有更新版本可用"（AGP 8.13.2→9.4.1、compose-ui 1.9.2→1.12.1、room 2.8.1→2.8.5、work 2.10.2→2.11.2、lifecycle 2.9.1→2.9.4、retrofit 2.11.0→3.0.0…），属刻意版本锁定（设计 D5 + 与取证到的原 App 栈对齐）；1 条是建议 minSdk≤30 再配 `fullBackupContent`，理由见 §④C。

**5) 复刻取证复核**
- Room 导出的 `accounts` DDL（`app/schemas/com.example.fn_app.data.FnDogDatabase/1.json`）与原 dex 取证串**逐字符一致**（列序、可空性、主键）：
  ```
  CREATE TABLE IF NOT EXISTS `accounts` (`accountId` TEXT NOT NULL, `displayName` TEXT NOT NULL,
  `encryptedCredentials` BLOB NOT NULL, `accountStatus` TEXT NOT NULL, `accessTokenExpiresAt` INTEGER NOT NULL,
  `refreshTokenExpiresAt` INTEGER, `lastRefreshAt` INTEGER NOT NULL, `lastError` TEXT,
  `vbucksBalance` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`accountId`))
  ```
- **R1 认证形状实测（不含任何用户凭据的匿名探测）**：
  ```
  不带 Authorization            -> 400 errors.com.epicgames.common.oauth.invalid_client
  Basic base64("clientId:")     -> 400 同上
  用 dex:13890 的 base64 常量   -> 400 errors.com.epicgames.account.oauth.authorization_code_not_found (invalid_grant)
  ```
  解码即 `3f69e56c7649492c8cc29f1af08a8a12:b51ee9cb12234f50a69efa67ef53812e`，末次响应说明客户端被服务端接受、只差真授权码；`oauth/token` 走 `POST form-urlencoded` + `Authorization: Basic`。
- 工具链/网络实测结论已写进 `~/.gradle/gradle.properties` 注释与设计文档 §0.0。

## ④ 遗留问题

**A. 只剩真机能覆盖的项（Robolectric 已把能替的都替了，以下是它替代不了的）**
1. `adb install -r` 后进程真实启动、`logcat -d` 无 `FATAL EXCEPTION`（JVM 侧已证明 Application/Activity/Compose 树不崩，但换不到真机 ROM 与真 GPU）
2. **真 AndroidKeyStore**：AES/GCM 密钥生成与加解密只在设备上跑过；Robolectric 用的是可逆假实现（接口行为一致，密钥硬件属性不一致）
3. **真 Epic 端点**：授权码换 token 需要你本人浏览器登录取码；device_auth 刷新、`/summary` 好友真数据、`/v2/shop` 真数据（JVM 侧用的是按真实形状造的样本）
4. 断网冷启动出缓存（真机飞行模式验证；JVM 侧已用假缓存验证过同一分支逻辑）
5. 北京时间 08:00 前取到当日 UTC 数据的真机日志证据
6. Compose 图片加载（Coil）在真机上的大图渲染，JVM 侧不测网络取图

**A2. 本机为什么跑不了真机（实测，别再重复试）**
`adb devices` 空；USB 侧只有 `Clouddesk Virtual USB Host Controller/Root HUB`（云桌面虚拟 USB，无设备透传）；本机 IPv4 仅 `192.168.24.122`（VPC 内网）→ 手机无线 adb 不可达；`HyperVPresent=False`、`VirtualizationFirmwareEnabled=False` → Android 模拟器无硬件加速。要跑真机需你在天翼云客户端开 USB 重定向（或直接把 APK 传到手机装，但那样我拿不到 logcat）。

**B. 形状待真机核对的字段（刻意做了宽容解析，不臆测）**
- `POST deviceAuth` 的密钥字段名：现同时接受 `secret` 与 `deviceSecret`
- token 响应字段：snake/camel 双写法都收
- user-search 的查询参数（`prefix`/`displayNamePrefix`/`maxResults`）与 `entities[].description`：Epic 未认证时无法验证，首次真机调用会由 `EvidenceInterceptor` 打出原始响应体，据此一次改对
- 好友"拒绝收到的请求"与"移除好友"在 Epic 侧是否真为同一 DELETE：照 Glow 已跑通实现平移，待真机确认

**C. 本批刻意不做的（按 MVP 范围）**
- 皮肤仓库 / 运行日志页 / STW 每日任务 / 羊驼领取 / 账号档案快照 / 设置页 / 「仅 Wi-Fi 下载」开关（WorkManager 只加了 CONNECTED 约束）
- 与设计文档的一处收口：设计把 `EpicMcpApi`（购买/赠送/余额）列进了 phase 1 文件清单，实际按"不写无人调用的代码"推到 phase 2；`EpicEndpoints.MCP_BASE` 已就位
- 底部导航暂 3 个 tab（今日商城 / 好友 / 账号），phase 3 加「警报」成 4 个
- 今日商城头部未放账号切换器（phase 1 它在商城页无功能），phase 2 加 V-Bucks 余额时一并放
- `fullBackupContent`（API≤30 备份过滤器）**刻意未加**：`allowBackup="false"` 在 API≤30 已同时关掉云备份与 `adb backup`，再加一个 `<exclude path="."/>` 只是把 lint 话术变成一份运行期无意义的配置；Android 12+ 一侧已用 `dataExtractionRules` 显式全排除。该 warning 保留，如需清零我再加。

**D. 环境类，不改代码**
- Clash 代理对 `services.gradle.org` / `repo.maven.apache.org` 间歇掐 TLS：已用 aliyun 镜像 + 代理绕过组合解决；下次构建若报握手失败，先确认 Clash 是否在 7897
- `*.ol.epicgames.com` 间歇超时：真机验收时若整链路失败，按环境类汇报，不动 API 链路代码
