import Foundation

public enum KrakenOutboxBackoffPolicy {
    public static let maxRetryDelay: TimeInterval = 30

    public static func retryDelay(afterAttempt attempts: Int) -> TimeInterval {
        guard attempts > 0 else { return 0 }
        return min(pow(2.0, Double(attempts)), maxRetryDelay)
    }
}

public struct KrakenOutboxRecord: Codable, Equatable, Sendable {
    public var messageId: String
    public var attempts: Int
    public var lastError: String?
    public var nextRetryDelay: TimeInterval

    public init(
        messageId: String,
        attempts: Int = 0,
        lastError: String? = nil,
        nextRetryDelay: TimeInterval = 0
    ) {
        self.messageId = messageId
        self.attempts = attempts
        self.lastError = lastError
        self.nextRetryDelay = nextRetryDelay
    }

    public func recordFailure(error: String) -> KrakenOutboxRecord {
        let nextAttempts = attempts + 1
        return KrakenOutboxRecord(
            messageId: messageId,
            attempts: nextAttempts,
            lastError: error,
            nextRetryDelay: KrakenOutboxBackoffPolicy.retryDelay(afterAttempt: nextAttempts)
        )
    }
}
