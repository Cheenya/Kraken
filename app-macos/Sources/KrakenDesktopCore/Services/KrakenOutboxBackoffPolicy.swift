import Foundation

public enum KrakenOutboxBackoffPolicy {
    public static let maxRetryDelay: TimeInterval = 30

    public static func retryDelay(afterAttempt attempts: Int) -> TimeInterval {
        guard attempts > 0 else { return 0 }
        return min(pow(2.0, Double(attempts)), maxRetryDelay)
    }
}
