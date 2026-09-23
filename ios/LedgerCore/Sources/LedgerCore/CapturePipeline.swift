import CoreGraphics
import Foundation

#if canImport(Vision)
import Vision
#endif

#if canImport(UIKit)
import UIKit

public extension UIImage {
    /// Redraws so OCR sees the pixels in the orientation the user saw.
    func ledgerCGImage() -> CGImage? {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        return renderer.image { _ in
            draw(in: CGRect(origin: .zero, size: size))
        }.cgImage
    }
}
#endif

public enum VisionTextRecognizer {
    public static func recognize(_ image: CGImage) throws -> String {
        #if canImport(Vision)
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.usesLanguageCorrection = true
        request.recognitionLanguages = ["zh-Hans", "en-US"]
        let handler = VNImageRequestHandler(cgImage: image, options: [:])
        do {
            try handler.perform([request])
        } catch {
            request.recognitionLanguages = ["en-US"]
            try handler.perform([request])
        }
        let observations = (request.results ?? []).sorted { $0.boundingBox.midY > $1.boundingBox.midY }
        return observations.compactMap { $0.topCandidates(1).first?.string }.joined(separator: "\n")
        #else
        _ = image
        throw SyncFailure.decoding("当前系统不能识别图片文字")
        #endif
    }
}

public enum CapturePipeline {
    public static func ingest(text: String, into store: LedgerStore, at date: Date = Date()) -> IngestResult {
        store.ingest(text: text, at: date)
    }

    public static func ingest(image: CGImage, into store: LedgerStore, at date: Date = Date()) throws -> IngestResult {
        let text = try VisionTextRecognizer.recognize(image)
        return store.ingest(text: text, at: date)
    }
}
