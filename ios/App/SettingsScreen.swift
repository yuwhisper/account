import LedgerCore
import SwiftUI

struct SettingsScreen: View {
    @EnvironmentObject private var store: LedgerStore
    @State private var apiBaseURL = ""
    @State private var email = ""
    @State private var password = ""
    @State private var message = ""
    @State private var busy = false

    var body: some View {
        NavigationStack {
            Form {
                Section("自动记账") {
                    Text("iOS 不能读取微信或支付宝的通知和页面。截屏之后，用快捷指令把最新截图交给「识别付款截图」，识别到付款才会进入待确认。")
                    VStack(alignment: .leading, spacing: 6) {
                        step("1", "打开快捷指令，新建自动化，选择「截屏时」。")
                        step("2", "添加「获取最新的照片」，数量选 1。")
                        step("3", "添加本 App 的「识别付款截图」，把照片传进去。")
                        step("4", "关闭「运行前询问」，并允许打开 App。确认分类后再入账。")
                    }
                    .font(.subheadline)
                    Text("也可以在流水页从相册识别，或在微信里把付款截图分享到「语声记账」。")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                Section("云同步") {
                    TextField("API 地址", text: $apiBaseURL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                    Text("模拟器用 http://127.0.0.1:8000。真机改成电脑的局域网地址，先在这台电脑跑后端。")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    if store.snapshot.isLoggedIn {
                        LabeledContent("账号", value: store.snapshot.email)
                        Button("立即同步") { Task { await sync() } }
                            .disabled(busy)
                        Button("退出登录", role: .destructive) { store.logout() }
                    } else {
                        TextField("邮箱", text: $email)
                            .textInputAutocapitalization(.never)
                            .keyboardType(.emailAddress)
                        SecureField("密码", text: $password)
                        Button("登录") { Task { await authenticate(register: false) } }
                            .disabled(busy)
                        Button("注册并登录") { Task { await authenticate(register: true) } }
                            .disabled(busy)
                    }
                }
                if !message.isEmpty {
                    Section {
                        Text(message)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("设置")
            .onAppear {
                if apiBaseURL.isEmpty { apiBaseURL = store.snapshot.apiBaseURL }
                if email.isEmpty { email = store.snapshot.email }
            }
        }
    }

    private func step(_ index: String, _ text: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text(index)
                .font(.caption.weight(.semibold))
                .frame(width: 18, height: 18)
                .background(LedgerTheme.pine.opacity(0.15), in: Circle())
            Text(text)
        }
    }

    private func authenticate(register: Bool) async {
        let base = apiBaseURL.trimmingCharacters(in: .whitespacesAndNewlines)
        store.updateAPIBaseURL(base)
        busy = true
        defer { busy = false }
        do {
            if register {
                try await CloudSync.register(email: email, password: password, apiBaseURL: base)
            }
            let token = try await CloudSync.login(email: email, password: password, apiBaseURL: base)
            store.updateSession(token: token, email: email, apiBaseURL: base)
            try await CloudSync.sync(store)
            message = "已登录并同步"
            password = ""
        } catch {
            message = error.localizedDescription
        }
    }

    private func sync() async {
        store.updateAPIBaseURL(apiBaseURL)
        busy = true
        defer { busy = false }
        do {
            try await CloudSync.sync(store)
            message = "同步完成"
        } catch {
            message = error.localizedDescription
        }
    }
}
