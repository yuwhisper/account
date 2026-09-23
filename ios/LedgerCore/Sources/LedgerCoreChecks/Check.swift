import Foundation

enum Check {
    static var failures = 0

    static func eq<T: Equatable>(
        _ actual: T,
        _ expected: T,
        file: StaticString = #fileID,
        line: UInt = #line
    ) {
        if actual != expected {
            failures += 1
            print("FAIL \(file):\(line) expected \(expected), got \(actual)")
        }
    }

    static func yes(_ value: Bool, file: StaticString = #fileID, line: UInt = #line) {
        if !value {
            failures += 1
            print("FAIL \(file):\(line) expected true")
        }
    }

    static func no(_ value: Bool, file: StaticString = #fileID, line: UInt = #line) {
        if value {
            failures += 1
            print("FAIL \(file):\(line) expected false")
        }
    }

    static func isNil(_ value: Any?, file: StaticString = #fileID, line: UInt = #line) {
        if value != nil {
            failures += 1
            print("FAIL \(file):\(line) expected nil, got \(String(describing: value))")
        }
    }
}
