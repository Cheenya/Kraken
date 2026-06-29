package com.disser.kraken.nativecore

import java.math.BigInteger

data class NativeAdamovaResult(
    val a: BigInteger,
    val b: BigInteger,
    val singular: Boolean,
    val discriminant: BigInteger,
    val twoTorsionRootCount: Int,
    val twoTorsionRoots: List<String>,
    val threeTorsionRootCount: Int,
    val threeTorsionRoots: List<String>,
    val hasThreeTorsionIndicator: Boolean,
    val hasThreeTorsionInconsistency: Boolean,
    val classificationCase: String,
    val roots3CandidatesTotal: Long,
    val roots3RejectedMod: Long,
    val roots3RejectedBound: Long,
    val roots3PassedFilters: Long,
    val roots3ExactChecked: Long,
    val roots3ExactZero: Long,
    val roots3SquarecheckPass: Long,
    val divisorCountA2: Long,
    val factorizationSteps: Long,
    val xSquare: List<String>,
    val earlyStopHit: Boolean,
)

object NativeCoreBridge {
    private val loadResult: Result<Unit> = runCatching {
        System.loadLibrary("kraken_native_placeholder")
    }

    fun statusOrUnavailable(): String =
        loadResult.fold(
            onSuccess = { runCatching { getNativeCoreStatus() }.getOrDefault(UNAVAILABLE_STATUS) },
            onFailure = { UNAVAILABLE_STATUS },
        )

    fun classifyAdamovaOrNull(a: BigInteger, b: BigInteger): NativeAdamovaResult? {
        if (loadResult.isFailure) return null
        val raw = runCatching { classifyAdamovaV3Decimal(a.toString(), b.toString()) }.getOrNull()
            ?: return null
        return parseAdamovaResult(raw)
    }

    external fun getNativeCoreStatus(): String

    private external fun classifyAdamovaV3(a: Long, b: Long): String

    private external fun classifyAdamovaV3Decimal(a: String, b: String): String

    private const val UNAVAILABLE_STATUS = "Нативное ядро Kraken недоступно в этой среде."

    private fun parseAdamovaResult(raw: String): NativeAdamovaResult? {
        val fields = raw.split('\t')
        if (fields.firstOrNull() != "ok" || fields.size < 23) return null
        return runCatching {
            NativeAdamovaResult(
                a = fields[1].toBigInteger(),
                b = fields[2].toBigInteger(),
                singular = fields[3] == "1",
                discriminant = fields[4].toBigInteger(),
                twoTorsionRootCount = fields[5].toInt(),
                twoTorsionRoots = decodeList(fields[6]),
                threeTorsionRootCount = fields[7].toInt(),
                threeTorsionRoots = decodeList(fields[8]),
                hasThreeTorsionIndicator = fields[9] == "1",
                hasThreeTorsionInconsistency = fields[10] == "1",
                classificationCase = fields[11],
                roots3CandidatesTotal = fields[12].toLong(),
                roots3RejectedMod = fields[13].toLong(),
                roots3RejectedBound = fields[14].toLong(),
                roots3PassedFilters = fields[15].toLong(),
                roots3ExactChecked = fields[16].toLong(),
                roots3ExactZero = fields[17].toLong(),
                roots3SquarecheckPass = fields[18].toLong(),
                divisorCountA2 = fields[19].toLong(),
                factorizationSteps = fields[20].toLong(),
                xSquare = decodeList(fields[21]),
                earlyStopHit = fields[22] == "1",
            )
        }.getOrNull()
    }

    private fun decodeList(raw: String): List<String> =
        if (raw == "-") emptyList() else raw.split(',').filter { it.isNotBlank() }
}
