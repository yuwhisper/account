import LedgerCore
import SwiftUI

struct SettingsScreen: View {
    @EnvironmentObject private var store: LedgerStore
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @AppStorage("ledgerAppearance", store: UserDefaults(suiteName: "group.com.yuwhisper.account")) private var appearance = "system"
    @FocusState private var focusedField: Field?
    @State private var apiBaseURL = ""
    @State private var email = ""
    @State private var password = ""
    @State private var message = ""
    @State private var busy = false
    @State private var messageIsError = false
    @State private var showShortcutSteps = false
    @State private var showServer = false
    @State private var showLogout = false

    private enum Field: Hashable { case server, email, password }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 30) {
                    LedgerPageHeading(title: "设置", subtitle: "本机记录，按需同步")
                    VStack(alignment: .leading, spacing: 14) {
                        HStack {
                            Text("外观")
                                .font(.body)
                            Spacer()
                            Picker("外观", selection: $appearance) {
                                Text("跟随系统").tag("system")
                                Text("白天").tag("light")
                                Text("黑夜").tag("dark")
                            }
                            .pickerStyle(.menu)
                            .accessibilityLabel("外观")
                        }
                        .frame(minHeight: 44)
                        fieldRule
                    }
                    cloudSection
                    VStack(alignment: .leading, spacing: 16) {
                        sectionTitle("截图识别", symbol: "text.viewfinder")
                        Text("从相册选择付款截图，或分享到「惜夏记」。核对之后再入账。")
                            .font(.subheadline)
                            .foregroundStyle(LedgerTheme.muted)
                            .fixedSize(horizontal: false, vertical: true)
                        DisclosureGroup("设置快捷指令", isExpanded: $showShortcutSteps) {
                            VStack(alignment: .leading, spacing: 16) {
                                Text("iOS 无法读取支付通知，可通过截图和快捷指令捕获付款。")
                                    .font(.footnote)
                                    .foregroundStyle(LedgerTheme.muted)
                                step("1", "打开快捷指令，新建自动化，选择「截屏时」。")
                                step("2", "添加「获取最新的照片」，数量选 1。")
                                step("3", "添加「识别付款截图」，把照片传进去。")
                                step("4", "关闭「运行前询问」，允许打开 App。核对分类后再入账。")
                            }
                            .padding(.top, 16)
                            .padding(.bottom, 8)
                        }
                        .font(.subheadline)
                        .padding(.vertical, 4)
                        fieldRule
                    }
                    VStack(alignment: .leading, spacing: 16) {
                        DisclosureGroup("同步服务器", isExpanded: $showServer) {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("服务器地址")
                                    .font(.subheadline)
                                    .foregroundStyle(LedgerTheme.muted)
                                TextField("https://…", text: $apiBaseURL)
                                    .textInputAutocapitalization(.never)
                                    .autocorrectionDisabled()
                                    .keyboardType(.URL)
                                    .focused($focusedField, equals: .server)
                                    .padding(.vertical, 12)
                                    .disabled(busy)
                                fieldRule
                                Text("填写你的同步服务器地址。模拟器可用 http://127.0.0.1:8000；真机请填写可访问的服务器地址。")
                                    .font(.footnote)
                                    .foregroundStyle(LedgerTheme.muted)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                            .padding(.top, 18)
                            .padding(.bottom, 8)
                        }
                        .font(.subheadline)
                        fieldRule
                    }
                    HStack(alignment: .top, spacing: 10) {
                        Image(systemName: "lock")
                            .accessibilityHidden(true)
                        Text("账本保存在这台设备上，登录后才与云端同步。")
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .font(.footnote)
                    .foregroundStyle(LedgerTheme.muted)
                }
                .padding(.horizontal, 24)
                .padding(.top, 12)
                .padding(.bottom, 32)
            }
            .scrollDismissesKeyboard(.interactively)
            .ledgerPage()
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("完成") { focusedField = nil }
                }
            }
            .animation(reduceMotion ? nil : LedgerTheme.motion, value: message)
            .animation(reduceMotion ? nil : LedgerTheme.motion, value: showShortcutSteps)
            .animation(reduceMotion ? nil : LedgerTheme.motion, value: showServer)
            .sensoryFeedback(.selection, trigger: appearance)
            .confirmationDialog("退出当前账号？", isPresented: $showLogout, titleVisibility: .visible) {
                Button("退出登录", role: .destructive) {
                    store.logout()
                    message = ""
                }
                Button("取消", role: .cancel) { }
            } message: {
                Text("本机记录会保留。再次登录后可以继续同步。")
            }
            .onAppear {
                if apiBaseURL.isEmpty { apiBaseURL = store.snapshot.apiBaseURL }
                if email.isEmpty { email = store.snapshot.email }
            }
        }
    }

    private var cloudSection: some View {
        VStack(alignment: .leading, spacing: 20) {
            sectionTitle("云同步", symbol: "icloud")
            if store.snapshot.isLoggedIn {
                VStack(alignment: .leading, spacing: 6) {
                    Text("已登录")
                        .font(.footnote)
                        .foregroundStyle(LedgerTheme.income)
                    Text(store.snapshot.email)
                        .font(.body)
                        .textSelection(.enabled)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Button { Task { await sync() } } label: {
                    HStack(spacing: 10) {
                        if busy { ProgressView().tint(LedgerTheme.onRose) }
                        Text(busy ? "正在同步…" : "立即同步")
                    }
                }
                .buttonStyle(LedgerPrimaryButtonStyle())
                .disabled(busy || apiBaseURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                Button("退出登录", role: .destructive) {
                    showLogout = true
                }
                .font(.subheadline)
                .foregroundStyle(LedgerTheme.expense)
                .frame(maxWidth: .infinity, minHeight: 44)
                .disabled(busy)
            } else {
                VStack(alignment: .leading, spacing: 4) {
                    Text("邮箱")
                        .font(.subheadline)
                        .foregroundStyle(LedgerTheme.muted)
                    TextField("输入邮箱地址", text: $email)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.emailAddress)
                        .textContentType(.username)
                        .focused($focusedField, equals: .email)
                        .submitLabel(.next)
                        .onSubmit { focusedField = .password }
                        .padding(.vertical, 12)
                        .disabled(busy)
                    fieldRule
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text("密码")
                        .font(.subheadline)
                        .foregroundStyle(LedgerTheme.muted)
                    SecureField("输入密码", text: $password)
                        .textContentType(.password)
                        .focused($focusedField, equals: .password)
                        .submitLabel(.go)
                        .onSubmit { if canAuthenticate { Task { await authenticate(register: false) } } }
                        .padding(.vertical, 12)
                        .disabled(busy)
                    fieldRule
                }
                VStack(spacing: 10) {
                    Button { Task { await authenticate(register: false) } } label: {
                        HStack(spacing: 10) {
                            if busy { ProgressView().tint(LedgerTheme.onRose) }
                            Text(busy ? "正在连接…" : "登录并同步")
                        }
                    }
                    .buttonStyle(LedgerPrimaryButtonStyle())
                    .disabled(!canAuthenticate)
                    Button("注册新账号") { Task { await authenticate(register: true) } }
                        .font(.subheadline)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .disabled(!canAuthenticate)
                }
            }
            if !message.isEmpty {
                Label(message, systemImage: messageIsError ? "exclamationmark.circle" : "checkmark.circle")
                    .font(.subheadline)
                    .foregroundStyle(messageIsError ? LedgerTheme.expense : LedgerTheme.income)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.vertical, 4)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
            fieldRule
        }
    }

    private func sectionTitle(_ title: String, symbol: String) -> some View {
        HStack(spacing: 10) {
            Text(title)
                .font(LedgerTheme.heading(22))
            Spacer()
            Image(systemName: symbol)
                .font(.body)
                .foregroundStyle(LedgerTheme.muted)
                .accessibilityHidden(true)
        }
    }

    private var fieldRule: some View {
        Rectangle()
            .fill(LedgerTheme.border)
            .frame(height: 0.5)
    }

    private var canAuthenticate: Bool {
        !busy && !email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !password.isEmpty && !apiBaseURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func step(_ index: String, _ text: String) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Text(index)
                .font(LedgerTheme.amount(18))
                .foregroundStyle(LedgerTheme.rose)
                .frame(width: 20, alignment: .leading)
            Text(text)
                .font(.subheadline)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func authenticate(register: Bool) async {
        let base = apiBaseURL.trimmingCharacters(in: .whitespacesAndNewlines)
        store.updateAPIBaseURL(base)
        focusedField = nil
        message = ""
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
            messageIsError = false
            password = ""
        } catch {
            message = error.localizedDescription
            messageIsError = true
        }
    }

    private func sync() async {
        store.updateAPIBaseURL(apiBaseURL)
        focusedField = nil
        message = ""
        busy = true
        defer { busy = false }
        do {
            try await CloudSync.sync(store)
            message = "同步完成"
            messageIsError = false
        } catch {
            message = error.localizedDescription
            messageIsError = true
        }
    }
}
