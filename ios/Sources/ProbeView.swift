import SwiftUI
import Security

/// Phase 4 管线验证用探针 App：不含业务界面，只验证 iOS 侧四条地基
/// ①原生 HTTPS 取今日商城数据（无 CORS 问题，与安卓端同一个接口）
/// ②Keychain 写入/读回（对应安卓侧 AndroidKeyStore 的角色）
/// ③Epic 端点可达性（不带任何凭据，401 即代表网络通）
/// ④TrollStore 安装后能否正常运行（崩溃=签名/entitlements 不对）
@main
struct FnDogProbeApp: App {
    var body: some Scene {
        WindowGroup { ProbeView() }
    }
}

private struct ProbeResult {
    var title: String
    var detail: String
    var ok: Bool
}

struct ProbeView: View {
    @State private var results: [ProbeResult] = []
    @State private var running = false

    var body: some View {
        NavigationStack {
            List {
                Section("环境") {
                    row(ProbeResult(title: "系统版本", detail: UIDevice.current.systemVersion, ok: true))
                    row(ProbeResult(title: "构建", detail: "\(bundleVersion) (\(bundleShort))", ok: true))
                }
                Section("探针") {
                    if results.isEmpty {
                        Text("点右上「跑探针」").foregroundStyle(.secondary)
                    } else {
                        ForEach(Array(results.enumerated), id: \.offset) { _, item in row(item) }
                    }
                }
            }
            .navigationTitle("FN助手狗 iOS")
            .toolbar {
                Button(running ? "跑动中…" : "跑探针") { run() }
                    .disabled(running)
            }
            .refreshable { run() }
        }
    }

    @ViewBuilder
    private func row(_ item: ProbeResult) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(item.title).font(.headline)
                Spacer()
                Text(item.ok ? "OK" : "失败").foregroundStyle(item.ok ? .green : .red)
            }
            Text(item.detail).font(.caption).foregroundStyle(.secondary).textSelection(.enabled)
        }
    }

    private func run() {
        running = true
        Task {
            var out: [ProbeResult] = []
            out.append(keychainProbe())
            out.append(await shopProbe())
            out.append(await epicProbe())
            await MainActor.run {
                results = out
                running = false
            }
        }
    }

    private var bundleVersion: String {
        (Bundle.main.infoDictionary?["CFBundleVersion"] as? String) ?? "?"
    }

    private var bundleShort: String {
        (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String) ?? "?"
    }
}

// ── ② Keychain 往返 ────────────────────────────────────────────
private func keychainProbe() -> ProbeResult {
    let account = "fn-dog-probe"
    let secret = "probe-\(UUID().uuidString.prefix(8))"
    let base: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrAccount as String: account,
        kSecAttrService as String: "com.fnassistantdog.ios",
    ]
    SecItemDelete(base as CFDictionary)
    var add = base
    add[kSecValueData as String] = secret.data(using: .utf8) as Any
    add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
    let addStatus = SecItemAdd(add as CFDictionary, nil)
    guard addStatus == errSecSuccess else {
        return ProbeResult(title: "Keychain 写入", detail: "SecItemAdd 返回 \(addStatus)", ok: false)
    }
    let copyQuery: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrAccount as String: account,
        kSecAttrService as String: "com.fnassistantdog.ios",
        kSecReturnData as String: true,
        kSecMatchLimit as String: kSecMatchLimitOne,
    ]
    var copied: CFTypeRef?
    let copyStatus = SecItemCopyMatching(copyQuery as CFDictionary, &copied)
    let back = (copyStatus == errSecSuccess ? (copied as? Data) : nil).flatMap { String(data: $0, encoding: .utf8) }
    SecItemDelete(base as CFDictionary)
    return ProbeResult(
        title: "Keychain 写入/读回",
        detail: back == secret ? "读回一致（\(secret)）" : "读回不一致：\(back ?? "nil") status=\(copyStatus)",
        ok: back == secret,
    )
}

// ── ① 今日商城数据源（与安卓端同一接口） ──────────────────────
private func shopProbe() async -> ProbeResult {
    let url = URL(string: "https://fortnite-api.com/v2/shop?language=zh-Hans")!
    let request = URLRequest(url: url, timeoutInterval: 25)
    do {
        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? -1
        guard status == 200 else {
            return ProbeResult(title: "今日商城接口", detail: "HTTP \(status)", ok: false)
        }
        guard
            let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
            let payload = root["data"] as? [String: Any],
            let entries = payload["entries"] as? [Any]
        else {
            return ProbeResult(title: "今日商城接口", detail: "响应缺少 data.entries（\(data.count) 字节）", ok: false)
        }
        let giftable = entries.compactMap { $0 as? [String: Any] }.filter { ($0["giftable"] as? Bool) == true }.count
        return ProbeResult(
            title: "今日商城接口",
            detail: "条目 \(entries.count) · 可赠送 \(giftable) · UTC 日 \(payload["date"] as? String ?? "?")",
            ok: entries.isEmpty == false,
        )
    } catch {
        return ProbeResult(title: "今日商城接口", detail: "\(type(of: error))：\(error.localizedDescription)", ok: false)
    }
}

// ── ③ Epic 端点可达性（不带凭据；401 表示网络通、只是没登录） ──
private func epicProbe() async -> ProbeResult {
    let url = URL(string: "https://account-public-service-prod.ol.epicgames.com/account/api/public/account/me")!
    let request = URLRequest(url: url, timeoutInterval: 25)
    do {
        let (_, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? -1
        let reachable = status > 0
        return ProbeResult(
            title: "Epic 端点可达性",
            detail: reachable
                ? "HTTP \(status)（401/403 即网络通）"
                : "无响应",
            ok: reachable,
        )
    } catch {
        return ProbeResult(title: "Epic 端点可达性", detail: "\(type(of: error))：\(error.localizedDescription)", ok: false)
    }
}
