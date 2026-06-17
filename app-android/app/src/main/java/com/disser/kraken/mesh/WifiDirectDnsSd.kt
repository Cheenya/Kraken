package com.disser.kraken.mesh

internal object WifiDirectDnsSd {
    const val SERVICE_TYPE = "_kraken._tcp"
    const val ATTR_FINGERPRINT = "fingerprint"
    const val ATTR_DISPLAY_NAME = "display"
    const val ATTR_PORT = "port"

    fun isKrakenService(
        fullDomainName: String?,
        record: Map<String, String>,
    ): Boolean {
        val domain = fullDomainName.orEmpty().lowercase()
        return domain.contains(SERVICE_TYPE) ||
            record.containsKey(ATTR_FINGERPRINT) ||
            record.containsKey(ATTR_PORT)
    }

    fun port(record: Map<String, String>): Int? =
        record[ATTR_PORT]?.toIntOrNull()?.takeIf { it in 1..65535 }

    fun fingerprint(record: Map<String, String>): String? =
        record[ATTR_FINGERPRINT]?.trim()?.takeIf { it.isNotBlank() }
}
