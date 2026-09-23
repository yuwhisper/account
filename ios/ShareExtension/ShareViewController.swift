import LedgerCore
import UIKit

final class ShareViewController: UIViewController {
    private let label = UILabel()

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        label.text = "正在识别付款…"
        label.numberOfLines = 0
        label.textAlignment = .center
        label.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(label)
        NSLayoutConstraint.activate([
            label.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            label.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            label.centerYAnchor.constraint(equalTo: view.centerYAnchor),
        ])
        Task { await handle() }
    }

    private func handle() async {
        guard let provider = imageProvider() else {
            finish(message: "没有图片")
            return
        }
        do {
            let data = try await loadData(provider)
            guard let image = UIImage(data: data), let cgImage = image.ledgerCGImage() else {
                finish(message: "没有读到图片")
                return
            }
            let store = LedgerStore(fileURL: LedgerLocations.storeURL())
            let result = try CapturePipeline.ingest(image: cgImage, into: store)
            switch result {
            case .added(let pending):
                label.text = "待确认 \(yuan(pending.amountCents))"
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
        guard let url = URL(string: "yuwhisper://confirm") else {
            finish(message: nil)
            return
        }
        extensionContext?.open(url) { _ in
            self.finish(message: nil)
        }
    }

    private func finish(message: String?) {
        if let message { label.text = message }
        Task {
            if message != nil {
                try? await Task.sleep(nanoseconds: 900_000_000)
            }
            extensionContext?.completeRequest(returningItems: nil)
        }
    }

    private func yuan(_ cents: Int) -> String {
        String(format: "¥%.2f", Double(cents) / 100)
    }
}
