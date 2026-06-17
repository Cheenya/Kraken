package com.disser.kraken.research

import android.content.Context
import com.disser.kraken.storage.KrakenStorageKeys
import java.math.BigInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max

@Serializable
data class ResearchCurvePoint(
    val x: String,
    val y: String,
) {
    fun toBigPoint(): BigCurvePoint = BigCurvePoint(BigInteger(x), BigInteger(y))

    companion object {
        fun from(point: BigCurvePoint): ResearchCurvePoint =
            ResearchCurvePoint(point.x.toString(), point.y.toString())
    }
}

data class BigCurvePoint(
    val x: BigInteger,
    val y: BigInteger,
)

@Serializable
data class ResearchCurveAttackChallenge(
    val challengeId: String,
    val label: String,
    val curve: String,
    val fieldPrime: String,
    val coefficientA: String,
    val coefficientB: String,
    val basePoint: ResearchCurvePoint,
    val publicPoint: ResearchCurvePoint,
    val candidateLimit: Int,
    val expectedWeakSecretForDemo: Int?,
    val validationGateExpectedDecision: String,
    val validationGateReason: String,
    val scope: String,
)

@Serializable
data class ResearchCurveAttackLog(
    val challengeId: String,
    val attackName: String,
    val status: String,
    val testedChallenges: Int,
    val weakChallengeId: String?,
    val checkedCandidates: Int,
    val candidateLimit: Int,
    val recoveredSecret: Int?,
    val publicPoint: ResearchCurvePoint,
    val validationCheckedChallenges: Int,
    val validationRejectedChallenges: Int,
    val validationDecision: String,
    val elapsedMs: Long,
    val summary: String,
    val caveat: String,
)

data class ResearchCurveAttackProgress(
    val checkedCandidates: Int,
    val candidateLimit: Int,
    val attackPhase: String,
    val validationPhase: String,
    val testedChallenges: Int = 0,
    val validationCheckedChallenges: Int = 0,
    val validationRejectedChallenges: Int = 0,
    val currentCandidateLabel: String = "",
    val recoveredSecret: Int? = null,
) {
    val progress: Float = checkedCandidates.toFloat() / max(candidateLimit, 1).toFloat()
}

data class ResearchCurveAttackRunUpdate(
    val progress: ResearchCurveAttackProgress,
    val log: ResearchCurveAttackLog? = null,
)

data class ResearchCurveAttackProfile(
    val passCandidateCount: Int,
    val candidateBudgetPerChallenge: Int,
    val weakSecret: Int,
    val progressEmitStep: Int,
)

object StartupResearchCurveAttack {
    val StartupProfile = ResearchCurveAttackProfile(
        passCandidateCount = 3,
        candidateBudgetPerChallenge = 1_500_000,
        weakSecret = 1_350_331,
        progressEmitStep = 20_000,
    )

    val TestProfile = ResearchCurveAttackProfile(
        passCandidateCount = 2,
        candidateBudgetPerChallenge = 2_500,
        weakSecret = 1_800,
        progressEmitStep = 250,
    )

    const val ExpectedSecret = 1_350_331
    val CandidateLimit: Int = StartupProfile.totalCandidateLimit()

    private val p: BigInteger = BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE)
    private val a: BigInteger = BigInteger.valueOf(5)
    private val b: BigInteger = BigInteger.valueOf(7)
    private val basePoint = BigCurvePoint(BigInteger.valueOf(2), BigInteger.valueOf(5))
    private val curveEquation = "E(F_p): y^2 = x^3 + 5x + 7, p = 2^127 - 1"

    val candidateChallenges: List<ResearchCurveAttackChallenge> = buildChallenges(StartupProfile)

    fun buildChallenges(profile: ResearchCurveAttackProfile): List<ResearchCurveAttackChallenge> = buildList {
        repeat(profile.passCandidateCount) { index ->
            val hiddenSecretOutsideBudget = profile.candidateBudgetPerChallenge + 180_000 + index * 55_000
            add(
                ResearchCurveAttackChallenge(
                    challengeId = "candidate_budget_pass_${index + 1}_p127_v1",
                    label = "Candidate ${index + 1}: validation passes",
                    curve = curveEquation,
                    fieldPrime = p.toString(),
                    coefficientA = a.toString(),
                    coefficientB = b.toString(),
                    basePoint = ResearchCurvePoint.from(basePoint),
                    publicPoint = ResearchCurvePoint.from(
                        scalarMultiply(
                            point = basePoint,
                            scalar = hiddenSecretOutsideBudget,
                            a = a,
                            p = p,
                        ) ?: error("Pass candidate public point must not be infinity."),
                    ),
                    candidateLimit = profile.candidateBudgetPerChallenge,
                    expectedWeakSecretForDemo = null,
                    validationGateExpectedDecision = "PASS",
                    validationGateReason = "Generated scalar is outside the configured bounded attack budget.",
                    scope = "Research-scale finite-field ECDLP candidate over a 127-bit prime field.",
                )
            )
        }

        add(
            ResearchCurveAttackChallenge(
                challengeId = "candidate_weak_recovered_p127_v1",
                label = "Candidate ${profile.passCandidateCount + 1}: weak without validation",
                curve = curveEquation,
                fieldPrime = p.toString(),
                coefficientA = a.toString(),
                coefficientB = b.toString(),
                basePoint = ResearchCurvePoint.from(basePoint),
                publicPoint = ResearchCurvePoint.from(
                    scalarMultiply(
                        point = basePoint,
                        scalar = profile.weakSecret,
                        a = a,
                        p = p,
                    ) ?: error("Weak candidate public point must not be infinity."),
                ),
                candidateLimit = profile.candidateBudgetPerChallenge,
                expectedWeakSecretForDemo = profile.weakSecret,
                validationGateExpectedDecision = "REJECT",
                validationGateReason = "Generated scalar falls inside the bounded ECDLP attack budget.",
                scope = "Controlled weak candidate used to show why validation must run before accepting parameters.",
            )
        )
    }

    fun run(
        profile: ResearchCurveAttackProfile = TestProfile,
        progress: (ResearchCurveAttackProgress) -> Unit = {},
    ): ResearchCurveAttackLog =
        kotlinx.coroutines.runBlocking {
            runInternal(
                profile = profile,
                emitProgress = { progress(it) },
            )
        }

    fun runAsFlow(profile: ResearchCurveAttackProfile = StartupProfile): Flow<ResearchCurveAttackRunUpdate> = flow {
        var finalLog: ResearchCurveAttackLog? = null
        runInternal(
            profile = profile,
            emitProgress = { progress ->
                emit(ResearchCurveAttackRunUpdate(progress))
            },
            emitLog = { log ->
                finalLog = log
                emit(
                    ResearchCurveAttackRunUpdate(
                        progress = progressFor(
                            profile = profile,
                            checkedCandidates = log.checkedCandidates,
                            testedChallenges = log.testedChallenges,
                            validationCheckedChallenges = log.validationCheckedChallenges,
                            validationRejectedChallenges = log.validationRejectedChallenges,
                            candidateLabel = log.weakChallengeId ?: "validation complete",
                            recoveredSecret = log.recoveredSecret,
                        ),
                        log = log,
                    )
                )
            },
        )
        require(finalLog != null) { "Research attack must finish with a log." }
    }.flowOn(Dispatchers.Default)

    fun isOnCurve(point: ResearchCurvePoint): Boolean =
        isOnCurve(
            point = point.toBigPoint(),
            a = a,
            b = b,
            p = p,
        )

    private suspend fun runInternal(
        profile: ResearchCurveAttackProfile,
        emitProgress: suspend (ResearchCurveAttackProgress) -> Unit,
        emitLog: (suspend (ResearchCurveAttackLog) -> Unit)? = null,
    ): ResearchCurveAttackLog {
        val challenges = buildChallenges(profile)
        val totalCandidateLimit = profile.totalCandidateLimit()
        val startNanos = System.nanoTime()
        var totalChecked = 0
        var validationChecked = 0
        var validationRejected = 0

        suspend fun emit(
            checked: Int,
            tested: Int,
            label: String,
            recovered: Int?,
        ) {
            emitProgress(
                progressFor(
                    profile = profile,
                    checkedCandidates = checked,
                    testedChallenges = tested,
                    validationCheckedChallenges = validationChecked,
                    validationRejectedChallenges = validationRejected,
                    candidateLabel = label,
                    recoveredSecret = recovered,
                )
            )
        }

        emit(0, 0, "candidate queue", null)

        challenges.forEachIndexed { index, challenge ->
            val bigA = BigInteger(challenge.coefficientA)
            val bigB = BigInteger(challenge.coefficientB)
            val bigP = BigInteger(challenge.fieldPrime)
            val base = challenge.basePoint.toBigPoint()
            val public = challenge.publicPoint.toBigPoint()
            require(isOnCurve(base, bigA, bigB, bigP)) { "Base point must be on ${challenge.challengeId}." }
            require(isOnCurve(public, bigA, bigB, bigP)) { "Public point must be on ${challenge.challengeId}." }

            validationChecked += 1
            if (challenge.validationGateExpectedDecision == "REJECT") {
                validationRejected += 1
            }
            emit(totalChecked, index + 1, challenge.label, null)

            var current: BigCurvePoint? = null
            for (candidate in 1..challenge.candidateLimit) {
                current = pointAdd(current, base, bigA, bigP)
                totalChecked += 1
                if (candidate % profile.progressEmitStep == 0) {
                    emit(totalChecked, index + 1, challenge.label, null)
                }
                if (current == public) {
                    val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                    val log = ResearchCurveAttackLog(
                        challengeId = challenge.challengeId,
                        attackName = "Parallel validation-gate vs unvalidated ECDLP attack",
                        status = "weak_candidate_found",
                        testedChallenges = index + 1,
                        weakChallengeId = challenge.challengeId,
                        checkedCandidates = totalChecked,
                        candidateLimit = totalCandidateLimit,
                        recoveredSecret = candidate,
                        publicPoint = challenge.publicPoint,
                        validationCheckedChallenges = validationChecked,
                        validationRejectedChallenges = validationRejected,
                        validationDecision = "REJECT: validation gate marked the weak candidate before it could be accepted.",
                        elapsedMs = elapsedMs,
                        summary = "Without validation, ${challenge.label} exposes Q=dG with recoverable d=$candidate. With validation, the candidate is rejected before acceptance.",
                        caveat = "Controlled research demonstration only. It does not attack production Kraken identities or message encryption.",
                    )
                    emit(totalChecked, index + 1, challenge.label, candidate)
                    emitLog?.invoke(log)
                    return log
                }
            }
        }

        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
        val log = ResearchCurveAttackLog(
            challengeId = "startup_ecdlp_validation_gate_v1",
            attackName = "Parallel validation-gate vs unvalidated ECDLP attack",
            status = "no_weak_candidate_found",
            testedChallenges = challenges.size,
            weakChallengeId = null,
            checkedCandidates = totalChecked,
            candidateLimit = totalCandidateLimit,
            recoveredSecret = null,
            publicPoint = challenges.last().publicPoint,
            validationCheckedChallenges = validationChecked,
            validationRejectedChallenges = validationRejected,
            validationDecision = "PASS: no candidate secret was recovered inside the configured attack budget.",
            elapsedMs = elapsedMs,
            summary = "No unvalidated candidate failed inside the bounded startup attack budget.",
            caveat = "Controlled research demonstration only. It does not attack production Kraken identities or message encryption.",
        )
        emitLog?.invoke(log)
        return log
    }

    private fun progressFor(
        profile: ResearchCurveAttackProfile,
        checkedCandidates: Int,
        testedChallenges: Int,
        validationCheckedChallenges: Int,
        validationRejectedChallenges: Int,
        candidateLabel: String,
        recoveredSecret: Int?,
    ): ResearchCurveAttackProgress {
        val attackPhase = when {
            recoveredSecret != null -> "Без validation: слабый секрет восстановлен"
            testedChallenges == 0 -> "Без validation: подготовка атаки"
            testedChallenges <= profile.passCandidateCount -> "Без validation: атака candidate #$testedChallenges"
            else -> "Без validation: финальная атака weak candidate"
        }
        val validationPhase = when {
            validationRejectedChallenges > 0 -> "Validation gate: weak candidate rejected"
            validationCheckedChallenges == 0 -> "Validation gate: ожидание candidate"
            else -> "Validation gate: проверено $validationCheckedChallenges, rejected 0"
        }
        return ResearchCurveAttackProgress(
            checkedCandidates = checkedCandidates,
            candidateLimit = profile.totalCandidateLimit(),
            attackPhase = attackPhase,
            validationPhase = validationPhase,
            testedChallenges = testedChallenges,
            validationCheckedChallenges = validationCheckedChallenges,
            validationRejectedChallenges = validationRejectedChallenges,
            currentCandidateLabel = candidateLabel,
            recoveredSecret = recoveredSecret,
        )
    }
}

class ResearchAttackLogStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        KrakenStorageKeys.Preferences.RESEARCH_ATTACK,
        Context.MODE_PRIVATE,
    )
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun saveLatest(log: ResearchCurveAttackLog) {
        preferences.edit()
            .putString(KrakenStorageKeys.ResearchAttack.LATEST_LOG, json.encodeToString(log))
            .apply()
    }

    fun loadLatest(): ResearchCurveAttackLog? =
        preferences.getString(KrakenStorageKeys.ResearchAttack.LATEST_LOG, null)
            ?.let { runCatching { json.decodeFromString<ResearchCurveAttackLog>(it) }.getOrNull() }
}

fun formatResearchAttackCandidateProgress(checkedCandidates: Int, candidateLimit: Int): String =
    "${checkedCandidates.coerceIn(0, candidateLimit)}/$candidateLimit"

private fun ResearchCurveAttackProfile.totalCandidateLimit(): Int =
    (passCandidateCount + 1) * candidateBudgetPerChallenge

private fun isOnCurve(
    point: BigCurvePoint,
    a: BigInteger,
    b: BigInteger,
    p: BigInteger,
): Boolean {
    val left = point.y.multiply(point.y).mod(p)
    val right = point.x.multiply(point.x).mod(p).multiply(point.x).add(a.multiply(point.x)).add(b).mod(p)
    return left == right
}

private fun scalarMultiply(
    point: BigCurvePoint,
    scalar: Int,
    a: BigInteger,
    p: BigInteger,
): BigCurvePoint? {
    var result: BigCurvePoint? = null
    var addend: BigCurvePoint? = point
    var k = scalar
    while (k > 0) {
        if ((k and 1) == 1) {
            result = pointAdd(result, addend, a, p)
        }
        addend = pointAdd(addend, addend, a, p)
        k = k shr 1
    }
    return result
}

private fun pointAdd(
    first: BigCurvePoint?,
    second: BigCurvePoint?,
    a: BigInteger,
    p: BigInteger,
): BigCurvePoint? {
    if (first == null) return second
    if (second == null) return first
    if (first.x == second.x && first.y.add(second.y).mod(p).signum() == 0) return null

    val slope = if (first == second) {
        first.x.multiply(first.x)
            .multiply(BigInteger.valueOf(3))
            .add(a)
            .multiply(first.y.multiply(BigInteger.valueOf(2)).modInverse(p))
            .mod(p)
    } else {
        second.y.subtract(first.y)
            .multiply(second.x.subtract(first.x).modInverse(p))
            .mod(p)
    }

    val x = slope.multiply(slope).subtract(first.x).subtract(second.x).mod(p)
    val y = slope.multiply(first.x.subtract(x)).subtract(first.y).mod(p)
    return BigCurvePoint(x, y)
}
