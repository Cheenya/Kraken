package com.disser.kraken.mesh

internal object WifiDirectConnectDiagnostics {
    fun failureReasonName(reason: Int?): String? = when (reason) {
        null -> null
        0 -> "ERROR"
        1 -> "P2P_UNSUPPORTED"
        2 -> "BUSY"
        3 -> "NO_SERVICE_REQUESTS"
        else -> "UNKNOWN_$reason"
    }

    fun resultLabel(result: String, attempt: Int, groupOwnerIntent: Int, reason: Int? = null): String =
        buildString {
            append(result)
            append(":attempt=")
            append(attempt)
            append(":intent=")
            append(groupOwnerIntent)
            if (reason != null) {
                append(":reason=")
                append(failureReasonName(reason))
                append("(")
                append(reason)
                append(")")
            }
        }

    fun actionFailureLabel(prefix: String, reason: Int): String =
        "$prefix:${failureReasonName(reason)}($reason)"
}
