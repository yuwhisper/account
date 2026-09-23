import AppIntents
import LedgerCore
import UIKit
import UniformTypeIdentifiers

struct RecognizePaymentImageIntent: AppIntent {
    static var title: LocalizedStringResource = "识别付款截图"
    static var description = IntentDescription("识别微信或支付宝付款成功截图，放入待确认，不会直接入账。")
    static var openAppWhenRun = true

    @Parameter(title: "截图", supportedContentTypes: [.image])
    var image: IntentFile

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let data = try await image.data(contentType: .image)
        guard let uiImage = UIImage(data: data), let cgImage = uiImage.ledgerCGImage() else {
            return .result(dialog: "没有读到截图")
        }
        let store = LedgerStore(fileURL: LedgerLocations.storeURL())
        switch try CapturePipeline.ingest(image: cgImage, into: store) {
        case .added(let pending):
            return .result(dialog: "待确认 \(Money.yuan(pending.amountCents))")
        case .duplicate:
            return .result(dialog: "这笔已经在待确认里")
        case .notPayment:
            return .result(dialog: "没有识别到付款成功")
        }
    }
}
