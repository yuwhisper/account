import LedgerCore
import UIKit

final class ShareViewController: UIViewController {
    private let label = UILabel()
    private let detailLabel = UILabel()
    private let amountLabel = UILabel()
    private let statusIcon = UIImageView()
    private let spinner = UIActivityIndicatorView(style: .medium)
    private let closeButton = UIButton(type: .system)
    private let card = UIView()
    private var captureTask: Task<Void, Never>?
    private var completed = false

    private enum Palette {
        static let paper = color(0xF4F5EE, dark: 0x101513)
        static let ink = color(0x273F35, dark: 0xDCE5D8)
        static let muted = color(0x617064, dark: 0xA5B49F)
        static let green = color(0x273F35, dark: 0x9DB7A3)
        static let line = color(0xC5CDC1, dark: 0x303D35)
        static let seal = color(0xA54B3B, dark: 0x9DB7A3)
        static let onGreen = color(0xF4F5EE, dark: 0x101513)

        private static func color(_ light: UInt32, dark: UInt32) -> UIColor {
            UIColor { traits in
                let value = traits.userInterfaceStyle == .dark ? dark : light
                return UIColor(red: CGFloat((value >> 16) & 0xFF) / 255, green: CGFloat((value >> 8) & 0xFF) / 255, blue: CGFloat(value & 0xFF) / 255, alpha: 1)
            }
        }
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        switch UserDefaults(suiteName: "group.com.yuwhisper.account")?.string(forKey: "ledgerAppearance") {
        case "light": overrideUserInterfaceStyle = .light
        case "dark": overrideUserInterfaceStyle = .dark
        default: overrideUserInterfaceStyle = .unspecified
        }
        view.backgroundColor = Palette.paper
        view.tintColor = Palette.green
        let scrollView = UIScrollView()
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)
        NSLayoutConstraint.activate([
            scrollView.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
            scrollView.contentLayoutGuide.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor),
        ])
        card.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(card)

        let brandLabel = UILabel()
        brandLabel.text = "惜夏记"
        brandLabel.font = UIFontMetrics(forTextStyle: .headline).scaledFont(for: UIFont(name: "YuWhisperOrchidSerif-Regular", size: 22) ?? .systemFont(ofSize: 22))
        brandLabel.textColor = Palette.ink
        brandLabel.adjustsFontForContentSizeCategory = true
        let seal = UILabel()
        seal.text = "兰"
        seal.font = UIFont(name: "YuWhisperOrchidSerif-Regular", size: 18) ?? .systemFont(ofSize: 18)
        seal.textColor = Palette.seal
        seal.textAlignment = .center
        seal.layer.borderWidth = 0.7
        seal.layer.borderColor = Palette.seal.resolvedColor(with: traitCollection).cgColor
        seal.isAccessibilityElement = false
        NSLayoutConstraint.activate([
            seal.widthAnchor.constraint(equalToConstant: 28),
            seal.heightAnchor.constraint(equalToConstant: 32),
        ])
        let brandRow = UIStackView(arrangedSubviews: [brandLabel, UIView(), seal])
        brandRow.alignment = .center

        let artwork = UIImageView(image: UIImage(named: "OrchidArtwork"))
        artwork.contentMode = .scaleAspectFit
        artwork.isAccessibilityElement = false
        artwork.heightAnchor.constraint(equalToConstant: 174).isActive = true
        let rule = UIView()
        rule.backgroundColor = Palette.line
        rule.heightAnchor.constraint(equalToConstant: 0.5).isActive = true

        statusIcon.image = UIImage(systemName: "text.viewfinder", withConfiguration: UIImage.SymbolConfiguration(pointSize: 20, weight: .regular))
        statusIcon.tintColor = Palette.green
        statusIcon.contentMode = .scaleAspectFit
        statusIcon.isAccessibilityElement = false
        NSLayoutConstraint.activate([
            statusIcon.widthAnchor.constraint(equalToConstant: 24),
            statusIcon.heightAnchor.constraint(equalToConstant: 24),
        ])
        label.text = "正在识别付款"
        label.font = UIFontMetrics(forTextStyle: .title2).scaledFont(for: UIFont(name: "YuWhisperOrchidSerif-Regular", size: 26) ?? .systemFont(ofSize: 26))
        label.textColor = Palette.ink
        label.adjustsFontForContentSizeCategory = true
        label.numberOfLines = 0
        let statusRow = UIStackView(arrangedSubviews: [label, statusIcon])
        statusRow.alignment = .center
        statusRow.spacing = 12

        amountLabel.font = UIFontMetrics(forTextStyle: .largeTitle).scaledFont(for: UIFont(name: "Georgia", size: 40) ?? .systemFont(ofSize: 40))
        amountLabel.textColor = Palette.ink
        amountLabel.adjustsFontForContentSizeCategory = true
        amountLabel.adjustsFontSizeToFitWidth = true
        amountLabel.minimumScaleFactor = 0.6
        amountLabel.isHidden = true
        detailLabel.text = "识别金额与商户，核对之后再入账。"
        detailLabel.font = .preferredFont(forTextStyle: .subheadline)
        detailLabel.textColor = Palette.muted
        detailLabel.adjustsFontForContentSizeCategory = true
        detailLabel.numberOfLines = 0
        spinner.color = Palette.green
        spinner.hidesWhenStopped = true
        spinner.startAnimating()

        var buttonConfiguration = UIButton.Configuration.filled()
        buttonConfiguration.title = "取消"
        buttonConfiguration.baseBackgroundColor = Palette.green
        buttonConfiguration.baseForegroundColor = Palette.onGreen
        buttonConfiguration.background.cornerRadius = 12
        buttonConfiguration.contentInsets = NSDirectionalEdgeInsets(top: 16, leading: 20, bottom: 16, trailing: 20)
        closeButton.configuration = buttonConfiguration
        closeButton.titleLabel?.adjustsFontForContentSizeCategory = true
        closeButton.addAction(UIAction { [weak self] _ in
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            self?.complete()
        }, for: .touchUpInside)

        let stack = UIStackView(arrangedSubviews: [brandRow, artwork, rule, statusRow, amountLabel, detailLabel, spinner, closeButton])
        stack.axis = .vertical
        stack.spacing = 22
        stack.setCustomSpacing(28, after: rule)
        stack.setCustomSpacing(12, after: amountLabel)
        stack.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(stack)
        let widthConstraint = card.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor, constant: -48)
        widthConstraint.priority = .defaultHigh
        NSLayoutConstraint.activate([
            card.centerXAnchor.constraint(equalTo: scrollView.contentLayoutGuide.centerXAnchor),
            widthConstraint,
            card.widthAnchor.constraint(lessThanOrEqualToConstant: 560),
            card.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 24),
            card.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -24),
            stack.leadingAnchor.constraint(equalTo: card.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: card.trailingAnchor),
            stack.topAnchor.constraint(equalTo: card.topAnchor),
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor),
        ])
        captureTask = Task { await handle() }
    }

    private func handle() async {
        guard !completed && !Task.isCancelled else { return }
        guard let provider = imageProvider() else {
            finish(message: "没有图片")
            return
        }
        do {
            let data = try await loadData(provider)
            guard !completed && !Task.isCancelled else { return }
            guard let image = UIImage(data: data), let cgImage = image.ledgerCGImage() else {
                finish(message: "没有读到图片")
                return
            }
            let store = LedgerStore(fileURL: LedgerLocations.storeURL())
            let result = try CapturePipeline.ingest(image: cgImage, into: store)
            switch result {
            case .added(let pending):
                updateStatus(title: "已放入待确认", detail: "请在账本中核对分类，确认后入账。", symbol: "checkmark.circle", amount: yuan(pending.amountCents))
                openApp()
            case .duplicate:
                finish(message: "这笔已经在待确认里")
            case .notPayment:
                finish(message: "没有识别到付款成功")
            }
        } catch {
            finish(message: "识别失败")
        }
    }

    private func imageProvider() -> NSItemProvider? {
        let items = extensionContext?.inputItems.compactMap { $0 as? NSExtensionItem } ?? []
        return items.lazy.compactMap(\.attachments).joined().first {
            $0.hasItemConformingToTypeIdentifier("public.image")
        }
    }

    private func loadData(_ provider: NSItemProvider) async throws -> Data {
        try await withCheckedThrowingContinuation { continuation in
            provider.loadDataRepresentation(forTypeIdentifier: "public.image") { data, error in
                if let data {
                    continuation.resume(returning: data)
                } else {
                    continuation.resume(throwing: error ?? CocoaError(.fileReadUnknown))
                }
            }
        }
    }

    private func openApp() {
        guard !completed && !Task.isCancelled else { return }
        guard let url = URL(string: "yuwhisper://confirm") else {
            finish(message: nil)
            return
        }
        extensionContext?.open(url) { opened in
            guard !self.completed else { return }
            if opened {
                self.finish(message: nil)
            } else {
                self.updateStatus(title: "已放入待确认", detail: "打开「惜夏记」，核对分类后完成入账。", symbol: "checkmark.circle")
            }
        }
    }

    private func finish(message: String?) {
        guard !completed && !Task.isCancelled else { return }
        if let message {
            let detail: String
            switch message {
            case "这笔已经在待确认里": detail = "打开账本，核对这笔记录即可。"
            case "没有识别到付款成功": detail = "请选择包含付款成功、金额和商户的截图，再试一次。"
            default: detail = "请返回选择一张清晰的付款截图，再试一次。"
            }
            updateStatus(title: message, detail: detail, symbol: message == "这笔已经在待确认里" ? "checkmark.circle" : "exclamationmark.circle")
        } else {
            complete()
        }
    }

    private func complete() {
        guard !completed else { return }
        completed = true
        captureTask?.cancel()
        spinner.stopAnimating()
        extensionContext?.completeRequest(returningItems: nil)
    }

    private func updateStatus(title: String, detail: String, symbol: String, amount: String? = nil) {
        guard !completed && !Task.isCancelled else { return }
        label.text = title
        detailLabel.text = detail
        statusIcon.image = UIImage(systemName: symbol, withConfiguration: UIImage.SymbolConfiguration(pointSize: 20, weight: .regular))
        spinner.stopAnimating()
        closeButton.configuration?.title = "完成"
        if let amount {
            amountLabel.text = amount
            amountLabel.isHidden = false
        }
        if UIAccessibility.isReduceMotionEnabled {
            view.layoutIfNeeded()
        } else {
            statusIcon.transform = CGAffineTransform(scaleX: 0.9, y: 0.9)
            if amount != nil {
                amountLabel.alpha = 0
                amountLabel.transform = CGAffineTransform(translationX: 0, y: 6)
            }
            UIView.animate(withDuration: 0.28, delay: 0, usingSpringWithDamping: 1, initialSpringVelocity: 0, options: [.allowUserInteraction, .beginFromCurrentState]) {
                self.statusIcon.transform = .identity
                self.amountLabel.alpha = 1
                self.amountLabel.transform = .identity
                self.view.layoutIfNeeded()
            }
        }
        UIAccessibility.post(notification: .announcement, argument: title + "。" + detail)
    }

    private func yuan(_ cents: Int) -> String {
        String(format: "¥%.2f", Double(cents) / 100)
    }
}
