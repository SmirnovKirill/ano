package ru.inno_sc.penaltycalculation.service.converter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.inno_sc.penaltycalculation.service.AbstractTest
import ru.inno_sc.penaltycalculation.service.domain.AllReportsRevisionPenalty
import ru.inno_sc.penaltycalculation.service.domain.InitialSubmissionPenalty
import ru.inno_sc.penaltycalculation.service.domain.PenaltyCalculationResult
import ru.inno_sc.penaltycalculation.service.domain.ReportRevisionCase
import ru.inno_sc.penaltycalculation.service.domain.SingleReportRevisionPenalty
import ru.inno_sc.penaltycalculation.service.domain.StageCompletionPenalty
import ru.inno_sc.penaltycalculation.service.config.CommonConfig.Companion.DATE_FORMATTER
import java.math.BigDecimal
import java.time.LocalDate

class PenaltyCalculationResultConverterTest : AbstractTest() {
    @Test
    fun `renders revision penalty calculated from unique overdue days across reports`() {
        val report = SingleReportRevisionPenalty(listOf(date("05.03.2026")), emptyList())

        val dto = result(grantReport = report, extraBudgetReport = report, revisionsTotalPenalty = BigDecimal("23333.34")).toDto()

        assertEquals("28.10.2026", dto.calculationDate)
        assertEquals("14%", dto.keyRatePercent)
        assertEquals("05.03.2026", dto.revisionsPenalty.overdueDays)
        assertEquals("23\u00a0333,34\u00a0₽", dto.totalPenalty)
        assertEquals("Уникальных дней просрочки: 1. Неустойка: 23\u00a0333,34\u00a0₽.", dto.revisionsPenalty.summary)
        assertEquals("50\u00a0000\u00a0000,00\u00a0₽ × 14 / 100 × 1 / 300 = 23\u00a0333,34\u00a0₽", dto.revisionsPenalty.formula)
        assertEquals("0,00\u00a0₽ + 23\u00a0333,34\u00a0₽ + 0,00\u00a0₽ = 23\u00a0333,34\u00a0₽", dto.totalFormula)
        assertEquals(1, dto.revisionsPenalty.grantReport.overdueDaysCount)
        assertEquals(1, dto.revisionsPenalty.extraBudgetReport.overdueDaysCount)
        assertEquals("Неустойка по доработке рассчитывается по уникальным дням просрочки всех трёх отчётов. Если одна дата встречается в нескольких отчётах, она учитывается один раз.", dto.revisionsPenalty.explanation)
    }

    @Test
    fun `renders revision history and absent overdue dates as ready-to-display values`() {
        val days = listOf(date("06.03.2026"), date("07.03.2026"), date("08.03.2026"), date("09.03.2026"))
        val report = SingleReportRevisionPenalty(days, listOf(
            ReportRevisionCase(1, date("02.03.2026"), date("05.03.2026"), date("06.03.2026"), false, listOf(date("06.03.2026"))),
            ReportRevisionCase(1, date("09.03.2026"), date("05.03.2026"), date("09.03.2026"), true, days),
        ))

        val dto = result(grantReport = report, revisionsTotalPenalty = BigDecimal("93333.34")).toDto()
        val cases = dto.revisionsPenalty.grantReport.cases

        assertEquals(listOf(1, 1), cases.map { it.caseNumber })
        assertEquals(listOf("05.03.2026", "05.03.2026"), cases.map { it.plannedSubmissionDate })
        assertEquals(listOf("Не исправлены — срок сохраняется", "Исправлены"), cases.map { it.remarks })
        assertEquals("02.03.2026", cases.first().returnedForRevisionDate)
        assertEquals("09.03.2026", cases.last().actualSubmissionDate)
        assertEquals("06.03.2026, 07.03.2026, 08.03.2026, 09.03.2026", cases.last().overdueDays)
        assertEquals(4, dto.revisionsPenalty.grantReport.overdueDaysCount)
        assertEquals("Нет просрочки", dto.revisionsPenalty.extraBudgetReport.overdueDays)
        assertEquals(0, dto.revisionsPenalty.extraBudgetReport.overdueDaysCount)
        assertEquals("Нет просрочки", dto.initialSubmissionPenalty.overdueDays)
    }

    @Test
    fun `keeps exact kopecks when amounts exceed JavaScript integer precision`() {
        val dto = result(
            stageAmount = BigDecimal("9007199254740993.01"),
            initialSubmissionPenalty = InitialSubmissionPenalty(listOf(date("05.03.2026")), BigDecimal("4203359652212.47")),
        ).toDto()

        assertEquals("9\u00a0007\u00a0199\u00a0254\u00a0740\u00a0993,01\u00a0₽", dto.stageAmount)
        assertEquals("4\u00a0203\u00a0359\u00a0652\u00a0212,47\u00a0₽", dto.totalPenalty)
        assertEquals("9\u00a0007\u00a0199\u00a0254\u00a0740\u00a0993,01\u00a0₽ × 14 / 100 × 1 / 300 = 4\u00a0203\u00a0359\u00a0652\u00a0212,47\u00a0₽", dto.initialSubmissionPenalty.formula)
        assertEquals("4\u00a0203\u00a0359\u00a0652\u00a0212,47\u00a0₽ + 0,00\u00a0₽ + 0,00\u00a0₽ = 4\u00a0203\u00a0359\u00a0652\u00a0212,47\u00a0₽", dto.totalFormula)
    }

    private fun result(
        stageAmount: BigDecimal = BigDecimal("50000000"),
        initialSubmissionPenalty: InitialSubmissionPenalty = InitialSubmissionPenalty(emptyList(), BigDecimal("0.00")),
        grantReport: SingleReportRevisionPenalty = emptyReport(),
        extraBudgetReport: SingleReportRevisionPenalty = emptyReport(),
        revisionsTotalPenalty: BigDecimal = BigDecimal("0.00"),
    ) = PenaltyCalculationResult(
        contractNumber = "Г-0029",
        stageAmount = stageAmount,
        calculationDate = date("28.10.2026"),
        keyRatePercent = BigDecimal("14.00"),
        initialSubmissionPenalty = initialSubmissionPenalty,
        revisionsPenalty = AllReportsRevisionPenalty(grantReport, extraBudgetReport, emptyReport(), revisionsTotalPenalty),
        stageCompletionPenalty = StageCompletionPenalty(emptyList(), BigDecimal("0.00")),
    )

    private fun emptyReport() = SingleReportRevisionPenalty(emptyList(), emptyList())

    private fun date(value: String): LocalDate = LocalDate.parse(value, DATE_FORMATTER)
}
