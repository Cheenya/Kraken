package com.disser.kraken.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.disser.kraken.crypto.AdamovaAttackDemoReport
import com.disser.kraken.crypto.AdamovaAdmissionAttackDemoRunner
import com.disser.kraken.crypto.ProductCryptoAdmissionGate
import com.disser.kraken.nativecore.NativeCoreBridge
import com.disser.kraken.research.CurveDiagnosticResult
import com.disser.kraken.research.CurveReportDisplayModel
import com.disser.kraken.research.CurveReportLoadResult
import com.disser.kraken.research.CurveReportRepository
import com.disser.kraken.research.GuidedCurveExample
import com.disser.kraken.research.GuidedCurveExampleCategory
import com.disser.kraken.research.GuidedCurveExampleRepository
import com.disser.kraken.research.GuidedCurveExamplesLoadResult
import com.disser.kraken.research.LargeCoefficientValidationSummary
import com.disser.kraken.research.ResearchBackendBenchmark
import com.disser.kraken.research.ResearchBackendBenchmarkReport
import com.disser.kraken.research.ResearchAttackLogStore
import com.disser.kraken.research.ResearchCurveAttackProgress
import com.disser.kraken.research.ResearchCurveAttackLog
import com.disser.kraken.research.ResearchDiagnosticService
import com.disser.kraken.research.toDisplayModel
import com.disser.kraken.research.StartupResearchCurveAttack
import com.disser.kraken.ui.components.InfoCard
import com.disser.kraken.ui.components.KrakenCompactCard
import com.disser.kraken.ui.components.KrakenListRow
import com.disser.kraken.ui.components.KrakenSectionHeader
import com.disser.kraken.ui.components.LabeledValue
import com.disser.kraken.ui.components.ScreenContainer
import com.disser.kraken.ui.components.StateBadge
import com.disser.kraken.ui.components.TechnicalDetailsDisclosure
import com.disser.kraken.ui.components.WarningCard
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResearchScreen(navController: NavHostController) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    var aText by remember { mutableStateOf("2") }
    var bText by remember { mutableStateOf("3") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var benchmarkRunning by remember { mutableStateOf(false) }
    var benchmarkReport by remember { mutableStateOf<ResearchBackendBenchmarkReport?>(null) }
    var benchmarkError by remember { mutableStateOf<String?>(null) }
    var admissionDemoRunning by remember { mutableStateOf(false) }
    var admissionDemoReport by remember { mutableStateOf<AdamovaAttackDemoReport?>(null) }
    var admissionDemoError by remember { mutableStateOf<String?>(null) }
    val examplesLoadResult = remember { GuidedCurveExampleRepository.loadManifest(context) }
    var selectedExample by remember { mutableStateOf<GuidedCurveExample?>(null) }
    var bundledReportResult by remember {
        mutableStateOf(CurveReportRepository.loadBundledSampleReport(context))
    }
    var attackRunning by remember { mutableStateOf(false) }
    var attackProgress by remember { mutableStateOf<ResearchCurveAttackProgress?>(null) }
    var startupAttackLog by remember { mutableStateOf(ResearchAttackLogStore(context).loadLatest()) }
    var attackError by remember { mutableStateOf<String?>(null) }
    var result by remember {
        mutableStateOf(
            ResearchDiagnosticService.evaluate(
                ResearchDiagnosticService.parseCurveInput(aText, bText).getOrThrow()
            )
        )
    }

    ScreenContainer("Исследование", navController) {
        val runDemoCalculation: () -> Unit = {
            attackRunning = true
            attackError = null
            attackProgress = null
            coroutineScope.launch {
                val store = ResearchAttackLogStore(context)
                runCatching {
                    StartupResearchCurveAttack.runAsFlow().collect { update ->
                        attackProgress = update.progress
                        update.log?.let { log ->
                            store.saveLatest(log)
                            startupAttackLog = log
                        }
                    }
                }.onFailure { error ->
                    attackError = error.message ?: "Демонстрационный расчёт не выполнен."
                }
                attackRunning = false
            }
        }
        val runBenchmark: () -> Unit = {
            benchmarkRunning = true
            benchmarkError = null
            coroutineScope.launch {
                runCatching {
                    withContext(Dispatchers.Default) {
                        ResearchBackendBenchmark.run(warmupRuns = 3, measuredRuns = 12)
                    }
                }.onSuccess { report ->
                    benchmarkReport = report
                    benchmarkRunning = false
                    val outputDir = File(context.filesDir, "research_backend_benchmark").apply { mkdirs() }
                    File(outputDir, "backend_benchmark_latest.json").writeText(report.toJson())
                    File(outputDir, "backend_benchmark_latest.md").writeText(report.toMarkdown())
                }.onFailure { error ->
                    benchmarkRunning = false
                    benchmarkError = error.message ?: "Замер Kotlin/C++ не выполнен."
                }
            }
        }
        val runAdmissionDemo: () -> Unit = {
            admissionDemoRunning = true
            admissionDemoError = null
            coroutineScope.launch {
                runCatching {
                    withContext(Dispatchers.Default) {
                        AdamovaAdmissionAttackDemoRunner(ProductCryptoAdmissionGate()).run()
                    }
                }.onSuccess { report ->
                    admissionDemoReport = report
                    admissionDemoRunning = false
                    val outputDir = File(context.filesDir, "profile_admission_gate").apply { mkdirs() }
                    File(outputDir, "admission_gate_attack_demo_latest.json").writeText(report.toJson())
                    File(outputDir, "admission_gate_attack_demo_latest.md").writeText(report.toMarkdown())
                }.onFailure { error ->
                    admissionDemoRunning = false
                    admissionDemoError = error.message ?: "Проверка допуска профиля не выполнена."
                }
            }
        }

        ResearchOverviewCards(
            attackRunning = attackRunning,
            benchmarkRunning = benchmarkRunning,
            admissionDemoRunning = admissionDemoRunning,
            lastAttackStatus = startupAttackLog?.status?.let(::researchAttackStatusDisplay),
            lastBenchmarkStatus = benchmarkReport?.let { "Ускорение C++: ${formatBenchmarkRatio(it.totalSpeedup)}x" },
            lastAdmissionStatus = admissionDemoReport?.let {
                "Проверка допуска отклонила ${it.metrics.rejectedByAdamovaGate}/${it.metrics.weakProfilesTotal} слабых профилей"
            },
            onRunDemoCalculation = runDemoCalculation,
            onRunBenchmark = runBenchmark,
            onRunAdmissionDemo = runAdmissionDemo,
        )

        TechnicalDetailsDisclosure("Состояние расчётов") {
            WarningCard(
                "Только диагностика",
                listOf(
                    "Математические отчёты загружены локально. Это не боевое шифрование сообщений.",
                    "Python внутри Android не запускается.",
                )
            )
            KrakenListRow(
                title = "Валидированный корпус",
                subtitle = "20 больших кривых · 20 совпадений SageMath · 0 расхождений",
                leadingText = "20",
                badge = "SAGE",
            )
            StartupAttackEvidenceCard(
                log = startupAttackLog,
                running = attackRunning,
                progress = attackProgress,
                error = attackError,
                onRunAttack = runDemoCalculation,
            )
            AdamovaAdmissionGateCard(
                report = admissionDemoReport,
                running = admissionDemoRunning,
                error = admissionDemoError,
                onRunAdmissionDemo = runAdmissionDemo,
            )
            BackendBenchmarkCard(
                report = benchmarkReport,
                running = benchmarkRunning,
                error = benchmarkError,
                onRunBenchmark = runBenchmark,
            )
        }
        KrakenSectionHeader("Сценарии")
        GuidedExamplesSection(
            loadResult = examplesLoadResult,
            selectedExample = selectedExample,
            onLoadExample = { example ->
                selectedExample = example
                aText = example.a
                bText = example.b
                example.toInput()
                    .onSuccess { input ->
                        result = ResearchDiagnosticService.evaluate(input)
                        errorText = null
                    }
                    .onFailure {
                        errorText = "Этот пример не разобран локальным Android-парсером."
                    }
                bundledReportResult = example.assetPath?.let { CurveReportRepository.loadReportAsset(context, it) }
                    ?: CurveReportLoadResult.Error("Для этого примера нет bundled-отчёта; эталонная проверка ожидается.")
            },
            onViewReport = { example ->
                selectedExample = example
                bundledReportResult = example.assetPath?.let { CurveReportRepository.loadReportAsset(context, it) }
                    ?: CurveReportLoadResult.Error("Для этого примера нет bundled-отчёта; эталонная проверка ожидается.")
            },
            onCopyCoefficients = { example ->
                clipboardManager.setText(AnnotatedString("a=${example.a}, b=${example.b}"))
                selectedExample = example
            },
        )
        selectedExample?.let { SelectedExampleCard(it) }
        KrakenSectionHeader("Метрики")
        KrakenCompactCard {
            Text("Ручная проверка коэффициентов", fontWeight = FontWeight.SemiBold)
            Text("Локальная диагностика y² = x³ + ax + b без сетевых вызовов.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = aText,
                    onValueChange = { aText = it },
                    label = { Text("a") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = bText,
                    onValueChange = { bText = it },
                    label = { Text("b") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Button(
                onClick = {
                    ResearchDiagnosticService.parseCurveInput(aText, bText)
                        .onSuccess { input ->
                            result = ResearchDiagnosticService.evaluate(input)
                            errorText = null
                        }
                        .onFailure {
                            errorText = "Введите целые коэффициенты a и b."
                        }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Пересчитать отчёт")
            }
        }
        errorText?.let { WarningCard("Ошибка ввода", listOf(it)) }
        ResearchResultCard(result)
        InfoCard("Нативное ядро", listOf(NativeCoreBridge.statusOrUnavailable()))
        KrakenSectionHeader("Артефакты")
        Button(
            onClick = {
                bundledReportResult = CurveReportRepository.loadBundledSampleReport(context)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Обновить встроенный отчёт")
        }
        TechnicalDetailsDisclosure("Встроенный отчёт") {
            BundledCurveReportCard(bundledReportResult)
        }
        InfoCard("Экспорт отчётов", listOf("Файлы отчётов остаются локальными артефактами для диссертации."))
    }
}

@Composable
private fun ResearchOverviewCards(
    attackRunning: Boolean,
    benchmarkRunning: Boolean,
    admissionDemoRunning: Boolean,
    lastAttackStatus: String?,
    lastBenchmarkStatus: String?,
    lastAdmissionStatus: String?,
    onRunDemoCalculation: () -> Unit,
    onRunBenchmark: () -> Unit,
    onRunAdmissionDemo: () -> Unit,
) {
    KrakenCompactCard {
        Text("Сценарии", fontWeight = FontWeight.SemiBold)
        Text("Учебные, валидационные и исследовательские примеры для локальной проверки.")
        Button(
            onClick = onRunDemoCalculation,
            enabled = !attackRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (attackRunning) "Идёт расчёт..." else "Запустить демонстрационный расчёт")
        }
        lastAttackStatus?.let { Text("Последний расчёт: $it") }
    }
    KrakenCompactCard {
        Text("Метрики", fontWeight = FontWeight.SemiBold)
        Text(lastBenchmarkStatus ?: "Сравнение Kotlin и C++ ещё не запускалось.")
        OutlinedButton(
            onClick = onRunBenchmark,
            enabled = !benchmarkRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (benchmarkRunning) "Идёт замер..." else "Сравнить Kotlin и C++")
        }
    }
    KrakenCompactCard {
        Text("Проверка допуска профиля", fontWeight = FontWeight.SemiBold)
        Text(lastAdmissionStatus ?: "Демонстрация допуска криптопрофиля ещё не запускалась.")
        OutlinedButton(
            onClick = onRunAdmissionDemo,
            enabled = !admissionDemoRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (admissionDemoRunning) "Проверяю профили..." else "Проверить допуск")
        }
    }
    KrakenCompactCard {
        Text("Артефакты", fontWeight = FontWeight.SemiBold)
        Text("Встроенные отчёты остаются локальными файлами доказательств.")
    }
}

@Composable
private fun AdamovaAdmissionGateCard(
    report: AdamovaAttackDemoReport?,
    running: Boolean,
    error: String?,
    onRunAdmissionDemo: () -> Unit,
) {
    InfoCard(
        "Проверка допуска профиля",
        listOf(
            "Проверяет исследовательский профиль кривой до сессии и политики пакета.",
            "Экспериментальный профиль отклоняется до переписки, если C++-проверка находит слабую структуру.",
            "Проверенные стандартные примитивы получают статус «не применимо», без ложной проверки экспериментального контура.",
            "Это исследовательский фильтр, не шифрование payload и не доказательство боевой безопасности.",
        ),
    )
    Button(
        onClick = onRunAdmissionDemo,
        enabled = !running,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (running) "Проверяю профили..." else "Запустить проверку допуска")
    }
    error?.let {
        WarningCard("Проверка допуска не выполнена", listOf(it))
    }
    report?.let {
        val metrics = it.metrics
        InfoCard(
            "Последний запуск",
            listOf(
                "Слабых профилей: ${metrics.weakProfilesTotal}.",
                "Принято без предварительной проверки: ${metrics.acceptedWithoutPrecheck}.",
                "Принято только по дискриминанту: ${metrics.acceptedByDiscriminantOnly}.",
                "Принято проверкой допуска: ${metrics.acceptedByAdamovaGate}.",
                "Отклонено проверкой допуска: ${metrics.rejectedByAdamovaGate}.",
                "Нужна эталонная проверка: ${metrics.needsReferenceValidation}; ограничено размером: ${metrics.sizeGuarded}.",
                "Медианная задержка: ${formatAdmissionLatency(metrics.medianGateLatencyMs)} мс; p95: ${formatAdmissionLatency(metrics.p95GateLatencyMs)} мс.",
                "Отчёт сохранён локально в каталоге profile_admission_gate.",
            ),
        )
        TechnicalDetailsDisclosure("Сценарии проверки допуска") {
            it.results.forEach { result ->
                KrakenListRow(
                    title = result.scenarioId,
                    subtitle = "${result.kind} · решение проверки=${result.adamovaDecision ?: "нет"} · принято=${result.adamovaAccepted}",
                    leadingText = if (result.adamovaAccepted) "ДА" else "СТОП",
                    badge = when {
                        result.adamovaAccepted -> "принято"
                        result.discriminantOnlyAccepted -> "блок"
                        else -> "отказ"
                    },
                )
            }
        }
    }
}

@Composable
private fun BackendBenchmarkCard(
    report: ResearchBackendBenchmarkReport?,
    running: Boolean,
    error: String?,
    onRunBenchmark: () -> Unit,
) {
    InfoCard(
        "Замер Kotlin и C++",
        listOf(
            "Ручной замер одной диагностической задачи на Kotlin BigInteger и C++.",
            "Это замер исследовательской диагностики, не боевого шифрования.",
        ),
    )
    Button(
        onClick = onRunBenchmark,
        enabled = !running,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (running) "Идёт замер..." else "Сравнить Kotlin и C++")
    }
    error?.let {
        WarningCard("Замер не выполнен", listOf(it))
    }
    report?.let {
        val comparable = it.comparableRows.size
        InfoCard(
            "Последний замер",
            listOf(
                "Сценариев: ${it.rows.size}; точно сравнимых: $comparable.",
                "Суммарная медиана Kotlin: ${formatBenchmarkMs(it.kotlinMedianTotalNs)} мс.",
                "Суммарная медиана C++: ${formatBenchmarkMs(it.nativeMedianTotalNs)} мс.",
                "Ускорение C++: ${formatBenchmarkRatio(it.totalSpeedup)}x.",
                "Ускорение C++ на точно сравнимых сценариях: ${formatBenchmarkRatio(it.comparableSpeedup)}x.",
                "Отчёт сохранён локально: research_backend_benchmark/backend_benchmark_latest.json и .md.",
            ),
        )
    }
}

@Composable
private fun StartupAttackEvidenceCard(
    log: ResearchCurveAttackLog?,
    running: Boolean,
    progress: ResearchCurveAttackProgress?,
    error: String?,
    onRunAttack: () -> Unit,
) {
    if (log == null) {
        InfoCard(
            "Демонстрационный расчёт",
            listOf(
                "Запускается вручную из исследовательского режима, обычный старт приложения больше не блокируется.",
                "Расчёт выполняется локально против контролируемой ослабленной ECDLP-задачи.",
            ),
        )
    } else {
        InfoCard(
            "Демонстрационный расчёт",
            listOf(
                "${log.attackName}: ${researchAttackStatusDisplay(log.status)}.",
                "Проверено наборов-кандидатов: ${log.testedChallenges}; слабый кандидат: ${log.weakChallengeId ?: "не найден"}.",
                "Проверено кандидатов: ${log.checkedCandidates}/${log.candidateLimit}.",
                "Восстановленный секрет: ${log.recoveredSecret ?: "не найден"}.",
                "Решение проверки: ${log.validationDecision}",
                "Публичная точка Q=(${log.publicPoint.x}, ${log.publicPoint.y}); время: ${log.elapsedMs} мс.",
                log.summary,
                log.caveat,
            ),
        )
    }
    progress?.let {
        InfoCard(
            "Ход выполнения",
            listOf(
                it.attackPhase,
                it.validationPhase,
                "Кандидат: ${it.currentCandidateLabel.ifBlank { "подготовка" }}.",
                "Проверено: ${it.checkedCandidates}/${it.candidateLimit}.",
            ),
        )
    }
    error?.let {
        WarningCard("Демонстрационный расчёт не выполнен", listOf(it))
    }
    Button(
        onClick = onRunAttack,
        enabled = !running,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (running) "Идёт расчёт..." else "Запустить демонстрационный расчёт")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuidedExamplesSection(
    loadResult: GuidedCurveExamplesLoadResult,
    selectedExample: GuidedCurveExample?,
    onLoadExample: (GuidedCurveExample) -> Unit,
    onViewReport: (GuidedCurveExample) -> Unit,
    onCopyCoefficients: (GuidedCurveExample) -> Unit,
) {
    when (loadResult) {
        is GuidedCurveExamplesLoadResult.Error -> WarningCard("Примеры недоступны", listOf(loadResult.reason))
        is GuidedCurveExamplesLoadResult.Success -> {
            loadResult.manifest.largeCoefficientValidation?.let {
                LargeCoefficientSummaryCard(it)
            }
            GuidedCurveExampleCategory.values().forEach { category ->
                val examples = loadResult.manifest.examples.filter { it.category == category }
                if (examples.isNotEmpty()) {
                    Text(
                        categoryDisplayLabel(category),
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    examples.forEach { example ->
                        GuidedExampleCard(
                            example = example,
                            selected = selectedExample?.exampleId == example.exampleId,
                            onLoadExample = { onLoadExample(example) },
                            onViewReport = { onViewReport(example) },
                            onCopyCoefficients = { onCopyCoefficients(example) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuidedExampleCard(
    example: GuidedCurveExample,
    selected: Boolean,
    onLoadExample: () -> Unit,
    onViewReport: () -> Unit,
    onCopyCoefficients: () -> Unit,
) {
    KrakenCompactCard {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StateBadge(categoryDisplayLabel(example.category))
                    StateBadge(validationStatusDisplayLabel(example.validationStatus))
                    if (example.teachingOnly) StateBadge("учебный")
                    if (selected) StateBadge("выбран")
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        exampleTitleDisplay(example),
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        example.equation,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    LabeledValue("a", example.a, Modifier.weight(1f))
                    LabeledValue("b", example.b, Modifier.weight(1f))
                }
                Text(
                    "${coefficientSizeDisplay(example.coefficientSize)} · ${expectedResultDisplay(example.expectedResult)}",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                example.caveat?.let {
                    Text(
                        caveatDisplay(it),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onLoadExample) { Text("Загрузить") }
                    OutlinedButton(onClick = onViewReport) { Text("Отчёт") }
                    OutlinedButton(onClick = onCopyCoefficients) { Text("a,b") }
                }
    }
}

@Composable
private fun LargeCoefficientSummaryCard(summary: LargeCoefficientValidationSummary) {
    InfoCard(
        summaryTitleDisplay(summary.title),
        listOf(
            "${summary.sageCompared} кривых сравнены с SageMath.",
            "${summary.directMatches} прямых совпадений; ${summary.mismatches} расхождений.",
            "Замер: ${summary.benchmarkRuns} прогонов; медиана %.4f мс; p95 %.4f мс.".format(
                summary.medianTotalRuntimeMs,
                summary.p95TotalRuntimeMs,
            ),
            caveatDisplay(summary.caveat),
        ),
    )
}

@Composable
private fun SelectedExampleCard(example: GuidedCurveExample) {
    val caveats = buildList {
        if (example.teachingOnly) {
            add("Учебный пример, не криптографический масштаб.")
        }
        if (example.category == GuidedCurveExampleCategory.RESEARCH_SCALE) {
            add("Большие коэффициенты над Q; это не боевая finite-field ECC.")
        }
        if (!example.validationStatus.equals("SageMath direct match", ignoreCase = true)) {
            add("Эталонная проверка для примера не завершена.")
        }
        example.caveat?.let { add(caveatDisplay(it)) }
    }
    if (caveats.isNotEmpty()) {
        WarningCard("Ограничения примера", caveats)
    }
}

private fun categoryDisplayLabel(category: GuidedCurveExampleCategory): String =
    when (category) {
        GuidedCurveExampleCategory.TEACHING -> "Учебные"
        GuidedCurveExampleCategory.VALIDATION -> "Валидация"
        GuidedCurveExampleCategory.RESEARCH_SCALE -> "Исследовательские"
    }

private fun validationStatusDisplayLabel(status: String): String =
    when (status) {
        "SageMath direct match" -> "SageMath: совпало"
        "Unsupported local torsion comparison" -> "локально не поддержано"
        "Local teaching example" -> "учебный пример"
        else -> status
    }

private fun summaryTitleDisplay(title: String): String =
    when (title) {
        "Large coefficient validation corpus" -> "Корпус больших коэффициентов"
        else -> title
    }

private fun exampleTitleDisplay(example: GuidedCurveExample): String =
    when (example.exampleId) {
        "teaching_nonzero_sanity_check" -> "Невырожденная учебная проверка"
        "validation_no_rational_two_torsion" -> "Нет рационального кручения порядка 2"
        "validation_lutz_nagell_order_four" -> "Lutz-Nagell и проверка порядка 4"
        "research_scale_prime_coefficients" -> "Около 32 бит без очевидного корня"
        "research_scale_lc64_scaled_order_four" -> "Около 64 бит с контролируемым кручением"
        "research_scale_lc128_large_a_small_b" -> "Около 128 бит с большим a"
        "research_scale_lc128_scaled_order_four" -> "Около 128 бит с контролируемым кручением"
        "research_scale_lutz_large_discriminant_stress" -> "Большой дискриминант: стресс-проверка"
        else -> example.title
    }

private fun coefficientSizeDisplay(value: String): String =
    value
        .replace("teaching coefficients", "учебные коэффициенты")
        .replace("small nonzero", "малые ненулевые")
        .replace("small validation coefficients", "малые коэффициенты для проверки")
        .replace("bits", "бит")
        .replace("bit", "бит")
        .replace("controlled torsion", "контролируемое кручение")

private fun expectedResultDisplay(value: String): String =
    when (value) {
        "Nonsingular sanity-check with nonzero a and b." ->
            "Невырожденная учебная проверка с ненулевыми a и b."
        "Full rational 2-torsion with three nontrivial points." ->
            "Полное рациональное кручение порядка 2: три нетривиальные точки."
        "Singular curve; group diagnostics are skipped." ->
            "Сингулярная кривая; групповая диагностика пропущена."
        "No rational 2-torsion." ->
            "Рациональное кручение порядка 2 не найдено."
        "One rational 2-torsion point with bounded order-four probe points." ->
            "Одна рациональная точка кручения порядка 2 и ограниченная проверка порядка 4."
        "One nontrivial rational 2-torsion point." ->
            "Одна нетривиальная рациональная точка кручения порядка 2."
        "No rational 2-torsion; local Lutz-Nagell helper is size-guarded." ->
            "Нет рационального кручения порядка 2; проверка Lutz-Nagell ограничена размером."
        else -> value
    }

private fun caveatDisplay(value: String): String =
    when (value) {
        "These are rational diagnostics over Q, not production finite-field ECC curves." ->
            "Это рациональная диагностика над Q, не боевая ECC над конечным полем."
        "Rational diagnostic over Q, not production finite-field crypto." ->
            "Диагностика над Q, не боевая криптография над конечным полем."
        else -> value
    }

private fun formatBenchmarkMs(ns: Long): String =
    String.format(Locale.US, "%.4f", ns.toDouble() / 1_000_000.0)

private fun formatBenchmarkRatio(value: Double): String =
    String.format(Locale.US, "%.4f", value)

private fun formatAdmissionLatency(value: Double): String =
    String.format(Locale.US, "%.6f", value)

private fun researchAttackStatusDisplay(status: String): String =
    when (status) {
        "weak_candidate_found" -> "слабый кандидат найден"
        "no_weak_candidate_found" -> "слабый кандидат не найден"
        else -> status
    }

@Composable
private fun ResearchResultCard(result: CurveDiagnosticResult) {
    InfoCard(
        "Результат диагностики",
        listOf(result.input.shortName, result.diagnosticOnlyWarning),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        LabeledValue("Невырожденная", result.nonsingular.toString(), Modifier.weight(1f))
        LabeledValue("Корни кручения 2", result.twoTorsionRootCount.toString(), Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        LabeledValue("Индикатор кручения 3", result.hasThreeTorsionIndicator.toString(), Modifier.weight(1f))
        LabeledValue("Случай", result.classificationCase, Modifier.weight(1f))
    }
    InfoCard(
        "Кандидаты кручения",
        when {
            !result.localDiagnosticSupported -> listOf(
                "Локальный Android-расчёт ограничен размером входа.",
                "Откройте встроенный math-core отчёт для проверенного evidence по этому примеру.",
            )
            result.allowedTorsionTypes.isEmpty() -> listOf("Для сингулярной кривой список не строится.")
            else -> result.allowedTorsionTypes
        },
    )
    if (!result.localDiagnosticSupported) {
        WarningCard("Локальная диагностика ограничена", result.unsupportedReasons)
    }
    InfoCard("Примечание", listOf(result.note))
}

@Composable
private fun BundledCurveReportCard(loadResult: CurveReportLoadResult) {
    when (loadResult) {
        is CurveReportLoadResult.Error -> WarningCard(
            "Встроенный отчёт недоступен",
            listOf(loadResult.reason),
        )
        is CurveReportLoadResult.Success -> BundledCurveReportContent(loadResult.report.toDisplayModel())
    }
}

@Composable
private fun BundledCurveReportContent(model: CurveReportDisplayModel) {
    WarningCard(
        "Встроенный диагностический отчёт",
        listOf(
            model.warning,
            "Сгенерировано локально через math-core.",
            "Android читает встроенный отчёт без Python runtime, сервера и облака.",
        ),
    )
    InfoCard("Уравнение кривой", listOf(model.curveEquation))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        model.invariantRows.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (label, value) ->
                    LabeledValue(label, value, Modifier.weight(1f))
                }
                if (row.size == 1) {
                    LabeledValue("", "", Modifier.weight(1f))
                }
            }
        }
    }
    InfoCard("Кручение порядка 2", listOf(model.twoTorsionSummary))
    InfoCard("Кандидаты Lutz-Nagell", listOf(model.lutzNagellSummary))
    InfoCard("Проверка кручения", listOf(model.torsionProbeSummary))
    InfoCard("Замер", listOf(model.benchmarkSummary))
    InfoCard(
        "Предупреждения и ограничения",
        (model.warnings + model.unsupportedCases).ifEmpty {
            listOf("Во встроенном примере нет предупреждений или неподдержанных сценариев.")
        },
    )
}
