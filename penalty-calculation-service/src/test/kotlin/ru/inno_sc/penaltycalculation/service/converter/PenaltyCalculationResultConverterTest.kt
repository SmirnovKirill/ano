package ru.inno_sc.penaltycalculation.service.converter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import ru.inno_sc.penaltycalculation.service.AbstractTest
import ru.inno_sc.penaltycalculation.service.domain.AllReportsRevisionPenalty
import ru.inno_sc.penaltycalculation.service.domain.InitialSubmissionPenalty
import ru.inno_sc.penaltycalculation.service.domain.PenaltyCalculationResult
import ru.inno_sc.penaltycalculation.service.domain.ReportRevisionCase
import ru.inno_sc.penaltycalculation.service.domain.SingleReportRevisionPenalty
import ru.inno_sc.penaltycalculation.service.domain.StageCompletionPenalty
import ru.inno_sc.penaltycalculation.service.config.CommonConfig.Companion.DATE_FORMATTER
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.time.LocalDate

class PenaltyCalculationResultConverterTest : AbstractTest() {
    @Autowired
    private lateinit var mapper: JsonMapper

    @Test
    fun `renders revision penalty calculated from unique overdue days across reports`() {
        val report = SingleReportRevisionPenalty(listOf(date("05.03.2026")), emptyList())

        val dto = result(grantReport = report, extraBudgetReport = report).copy(stageNumber = 4).toDto()

        assertEquals(4, dto.stageNumber)
        assertEquals("28.10.2026", dto.calculationDate)
        assertEquals("14%", dto.keyRatePercent)
        assertEquals("05.03.2026", dto.revisionsPenalty.daysForCalculation)
        assertEquals(1, dto.totalOverdueDaysCount)
        assertEquals("23\u00a0333,34\u00a0₽", dto.totalPenalty)
        assertEquals("Уникальных дней просрочки: 1.", dto.revisionsPenalty.summary)
        assertEquals("50\u00a0000\u00a0000,00\u00a0₽ × 14 / 100 × (0 + 1 + 0) / 300 = 23\u00a0333,34\u00a0₽", dto.totalFormula)
        assertEquals(1, dto.revisionsPenalty.grantReport.overdueDaysCount)
        assertEquals(1, dto.revisionsPenalty.extraBudgetReport.overdueDaysCount)
        assertEquals(1, dto.revisionsPenalty.overdueDaysCount)
        assertEquals(0, dto.initialSubmissionPenalty.overdueDaysCount)
        assertEquals(0, dto.stageCompletionPenalty.overdueDaysCount)
        assertEquals("Неустойка по доработке рассчитывается по уникальным дням просрочки всех трёх отчётов. Если одна дата встречается в нескольких отчётах, она учитывается один раз.", dto.revisionsPenalty.explanation)
    }

    @Test
    fun `renders revision history and absent overdue dates as ready-to-display values`() {
        val days = listOf(date("06.03.2026"), date("07.03.2026"), date("08.03.2026"), date("09.03.2026"))
        val report = SingleReportRevisionPenalty(days, listOf(
            ReportRevisionCase(1, date("02.03.2026"), date("05.03.2026"), date("06.03.2026"), false, listOf(date("06.03.2026"))),
            ReportRevisionCase(1, date("09.03.2026"), date("05.03.2026"), date("09.03.2026"), true, days),
        ))

        val dto = result(grantReport = report).toDto()
        val cases = dto.revisionsPenalty.grantReport.cases

        assertEquals(listOf(1, 1), cases.map { it.caseNumber })
        assertEquals(listOf("05.03.2026", "05.03.2026"), cases.map { it.plannedSubmissionDate })
        assertEquals(listOf("Возобновляется фиксация нарушения срока.", "Исправлены"), cases.map { it.remarks })
        assertEquals("02.03.2026", cases.first().returnedForRevisionDate)
        assertEquals("09.03.2026", cases.last().actualSubmissionDate)
        assertEquals("06.03.2026, 07.03.2026, 08.03.2026, 09.03.2026", cases.last().daysForCalculation)
        assertEquals(4, dto.revisionsPenalty.grantReport.overdueDaysCount)
        assertEquals("Нет просрочки", dto.revisionsPenalty.extraBudgetReport.daysForCalculation)
        assertEquals(0, dto.revisionsPenalty.extraBudgetReport.overdueDaysCount)
        assertEquals("Нет просрочки", dto.initialSubmissionPenalty.daysForCalculation)
    }

    @Test
    fun `keeps exact kopecks when amounts exceed JavaScript integer precision`() {
        val dto = result(
            stageAmount = BigDecimal("9007199254740993.01"),
            initialSubmissionPenalty = InitialSubmissionPenalty(listOf(date("05.03.2026"))),
        ).toDto()

        assertEquals("9\u00a0007\u00a0199\u00a0254\u00a0740\u00a0993,01\u00a0₽", dto.stageAmount)
        assertEquals("4\u00a0203\u00a0359\u00a0652\u00a0212,47\u00a0₽", dto.totalPenalty)
        assertEquals("9\u00a0007\u00a0199\u00a0254\u00a0740\u00a0993,01\u00a0₽ × 14 / 100 × (1 + 0 + 0) / 300 = 4\u00a0203\u00a0359\u00a0652\u00a0212,47\u00a0₽", dto.totalFormula)
    }

    @Test
    fun `serializes only one penalty amount after adding days of all three types`() {
        val overdueDays = listOf(date("05.03.2026"))
        val dto = result(
            stageAmount = BigDecimal("1"),
            initialSubmissionPenalty = InitialSubmissionPenalty(overdueDays),
            grantReport = SingleReportRevisionPenalty(overdueDays, emptyList()),
            stageCompletionPenalty = StageCompletionPenalty(overdueDays),
        ).toDto()
        val json = mapper.readTree(mapper.writeValueAsString(dto))

        assertEquals(3, dto.totalOverdueDaysCount)
        assertEquals("0,01\u00a0₽", dto.totalPenalty)
        assertEquals("1,00\u00a0₽ × 14 / 100 × (1 + 1 + 1) / 300 = 0,01\u00a0₽", dto.totalFormula)
        assertTrue(dto.formula.contains("один раз"))
        for (name in listOf("initialSubmissionPenalty", "revisionsPenalty", "stageCompletionPenalty")) {
            val section = json.path(name)
            assertFalse(section.has("penalty"), name)
            assertFalse(section.has("totalPenalty"), name)
            assertFalse(section.has("formula"), name)
        }
    }

    private fun result(
        stageAmount: BigDecimal = BigDecimal("50000000"),
        initialSubmissionPenalty: InitialSubmissionPenalty = InitialSubmissionPenalty(emptyList()),
        grantReport: SingleReportRevisionPenalty = emptyReport(),
        extraBudgetReport: SingleReportRevisionPenalty = emptyReport(),
        stageCompletionPenalty: StageCompletionPenalty = StageCompletionPenalty(emptyList()),
    ) = PenaltyCalculationResult(
        stageNumber = 1,
        contractNumber = "Г-0029",
        stageAmount = stageAmount,
        calculationDate = date("28.10.2026"),
        keyRatePercent = BigDecimal("14.00"),
        initialSubmissionPenalty = initialSubmissionPenalty,
        revisionsPenalty = AllReportsRevisionPenalty(grantReport, extraBudgetReport, emptyReport()),
        stageCompletionPenalty = stageCompletionPenalty,
    )

    private fun emptyReport() = SingleReportRevisionPenalty(emptyList(), emptyList())

    private fun date(value: String): LocalDate = LocalDate.parse(value, DATE_FORMATTER)
}
