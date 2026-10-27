package ru.inno_sc.penaltycalculation.service.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import ru.inno_sc.penaltycalculation.api.penalty.request.ExtraBudgetReportDto
import ru.inno_sc.penaltycalculation.api.penalty.request.GrantReportDto
import ru.inno_sc.penaltycalculation.api.penalty.request.ReportCaseDto
import ru.inno_sc.penaltycalculation.api.penalty.request.CalculatePenaltyRequestDto
import ru.inno_sc.penaltycalculation.api.penalty.request.TechnicalReportDto
import ru.inno_sc.penaltycalculation.service.AbstractTest
import ru.inno_sc.penaltycalculation.service.domain.ReportRevisionCase
import ru.inno_sc.penaltycalculation.service.config.CommonConfig.Companion.DATE_FORMATTER
import java.math.BigDecimal
import java.time.LocalDate

class PenaltyCalculationServiceTest : AbstractTest() {
    @Autowired
    private lateinit var service: PenaltyCalculationService

    private val keyRatePercent = BigDecimal("14.00")

    @Test
    fun `calculates penalties for the complete Excel example`() {
        val reports = CalculatePenaltyRequestDto(
            contractNumber = "Г-0029",
            stageAmount = BigDecimal("50000000"),
            reportPlannedSubmissionDate = date("23.12.2025"),
            reportActualSubmissionDate = date("25.12.2025"),
            grantReport = GrantReportDto(
                cases = listOf(
                    case("20.01.2026", "23.01.2026"),
                    case("26.01.2026", "28.01.2026"),
                    case("06.03.2026", "12.03.2026"),
                    case("13.03.2026", "18.03.2026"),
                ),
                reportApprovalDate = date("19.03.2026"),
            ),
            extraBudgetReport = ExtraBudgetReportDto(
                cases = listOf(
                    case("20.01.2026", "23.01.2026"),
                    case("26.01.2026", "29.01.2026"),
                    case("06.03.2026", "12.03.2026"),
                    case("13.03.2026", "19.03.2026"),
                ),
                reportApprovalDate = date("19.03.2026"),
            ),
            technicalReport = TechnicalReportDto(
                cases = listOf(
                    case("20.01.2026", "23.01.2026"),
                    case("26.01.2026", "30.01.2026", remarksCorrected = false),
                    case("13.02.2026", "16.02.2026"),
                    case("06.03.2026", "12.03.2026"),
                    case("13.03.2026", "16.03.2026"),
                    case("16.03.2026", "17.03.2026"),
                    case("18.03.2026", "18.03.2026"),
                ),
                reportApprovalDate = date("19.03.2026"),
                plannedCompletionDate = date("22.12.2025"),
                actualCompletionDate = date("10.01.2026"),
            ),
        )

        val calculationDate = date("28.10.2026")
        val result = service.calculate(reports)

        assertEquals(reports.contractNumber, result.contractNumber)
        assertEquals(calculationDate, result.calculationDate)
        assertEquals(reports.stageAmount, result.stageAmount)
        assertEquals(BigDecimal("14.00"), result.keyRatePercent)
        assertStage(
            result.initialSubmissionPenalty.overdueDays,
            result.initialSubmissionPenalty.penalty,
            penalty = "46666.67",
            expectedDays = listOf(date("24.12.2025"), date("25.12.2025")),
        )
        assertStage(
            result.revisionsPenalty.overdueDays,
            result.revisionsPenalty.totalPenalty,
            penalty = "490000.00",
            expectedDays = listOf(
                date("30.01.2026"), date("31.01.2026"),
                date("01.02.2026"), date("02.02.2026"), date("03.02.2026"),
                date("04.02.2026"), date("05.02.2026"), date("06.02.2026"),
                date("07.02.2026"), date("08.02.2026"), date("09.02.2026"),
                date("10.02.2026"), date("11.02.2026"), date("12.02.2026"),
                date("13.02.2026"), date("14.02.2026"), date("15.02.2026"), date("16.02.2026"),
                date("12.03.2026"), date("18.03.2026"), date("19.03.2026"),
            ),
        )
        assertStage(
            result.stageCompletionPenalty.overdueDays,
            result.stageCompletionPenalty.penalty,
            penalty = "443333.34",
            expectedDays = listOf(
                date("23.12.2025"), date("24.12.2025"),
                date("25.12.2025"), date("26.12.2025"), date("27.12.2025"),
                date("28.12.2025"), date("29.12.2025"), date("30.12.2025"),
                date("31.12.2025"), date("01.01.2026"), date("02.01.2026"),
                date("03.01.2026"), date("04.01.2026"), date("05.01.2026"),
                date("06.01.2026"), date("07.01.2026"), date("08.01.2026"),
                date("09.01.2026"), date("10.01.2026"),
            ),
        )
        assertEquals(BigDecimal("980000.01"), result.totalPenalty)

        assertEquals(
            listOf(date("12.03.2026"), date("18.03.2026")),
            result.revisionsPenalty.grantReport.overdueDays,
        )
        assertEquals(
            listOf(date("12.03.2026"), date("18.03.2026"), date("19.03.2026")),
            result.revisionsPenalty.extraBudgetReport.overdueDays,
        )
        assertEquals(
            listOf(
                date("30.01.2026"), date("31.01.2026"),
                date("01.02.2026"), date("02.02.2026"), date("03.02.2026"),
                date("04.02.2026"), date("05.02.2026"), date("06.02.2026"),
                date("07.02.2026"), date("08.02.2026"), date("09.02.2026"),
                date("10.02.2026"), date("11.02.2026"), date("12.02.2026"),
                date("13.02.2026"), date("14.02.2026"), date("15.02.2026"), date("16.02.2026"),
                date("12.03.2026"),
            ),
            result.revisionsPenalty.technicalReport.overdueDays,
        )

        assertCases(
            reports.grantReport.cases, result.revisionsPenalty.grantReport.cases,
            caseNumbers = listOf(1, 2, 3, 4),
            plannedDates = listOf(date("23.01.2026"), date("29.01.2026"), date("11.03.2026"), date("17.03.2026")),
        )
        assertCases(
            reports.extraBudgetReport.cases, result.revisionsPenalty.extraBudgetReport.cases,
            caseNumbers = listOf(1, 2, 3, 4),
            plannedDates = listOf(date("23.01.2026"), date("29.01.2026"), date("11.03.2026"), date("17.03.2026")),
        )
        assertCases(
            reports.technicalReport.cases, result.revisionsPenalty.technicalReport.cases,
            caseNumbers = listOf(1, 2, 2, 3, 4, 5, 6),
            plannedDates = listOf(
                date("23.01.2026"), date("29.01.2026"), date("29.01.2026"), date("11.03.2026"),
                date("17.03.2026"), date("18.03.2026"), date("19.03.2026"),
            ),
        )
    }

    @Test
    fun `first submission excludes deadline and includes submission day`() {
        val request = request().copy(
            reportPlannedSubmissionDate = date("23.12.2025"),
            reportActualSubmissionDate = date("25.12.2025"),
        )

        val result = service.calculateInitialSubmissionPenalty(request, keyRatePercent)

        assertEquals(listOf(date("24.12.2025"), date("25.12.2025")), result.overdueDays)
        assertEquals(BigDecimal("46666.67"), result.penalty)
        val onTime = service.calculateInitialSubmissionPenalty(request.copy(reportActualSubmissionDate = date("23.12.2025")), keyRatePercent)
        assertEquals(emptyList<LocalDate>(), onTime.overdueDays)
        assertEquals(BigDecimal("0.00"), onTime.penalty)
        val early = service.calculateInitialSubmissionPenalty(request.copy(reportActualSubmissionDate = date("22.12.2025")), keyRatePercent)
        assertEquals(emptyList<LocalDate>(), early.overdueDays)
        assertEquals(BigDecimal("0.00"), early.penalty)
    }

    @Test
    fun `revision penalty counts overlapping report dates only once`() {
        val request = request().copy(
            grantReport = GrantReportDto(listOf(case("02.03.2026", "09.03.2026")), date("10.03.2026")),
            extraBudgetReport = ExtraBudgetReportDto(listOf(case("02.03.2026", "06.03.2026")), date("10.03.2026")),
        )

        val result = service.calculateAllReportsRevisionPenalty(request, keyRatePercent)

        assertEquals(listOf(
            date("06.03.2026"), date("07.03.2026"), date("08.03.2026"), date("09.03.2026"),
        ), result.grantReport.overdueDays)
        assertEquals(listOf(date("06.03.2026")), result.extraBudgetReport.overdueDays)
        assertEquals(emptyList<LocalDate>(), result.technicalReport.overdueDays)
        assertEquals(listOf(
            date("06.03.2026"), date("07.03.2026"), date("08.03.2026"), date("09.03.2026"),
        ), result.overdueDays)
        assertEquals(BigDecimal("93333.34"), result.totalPenalty)
    }

    @Test
    fun `revision penalty combines disjoint report dates before rounding`() {
        val request = request()
        val result = service.calculateAllReportsRevisionPenalty(request.copy(
            stageAmount = BigDecimal("10.71"),
            grantReport = GrantReportDto(listOf(case("04.03.2026", "11.03.2026")), date("11.03.2026")),
            extraBudgetReport = request.extraBudgetReport.copy(cases = listOf(case("02.03.2026", "06.03.2026"))),
            technicalReport = request.technicalReport.copy(cases = listOf(case("03.03.2026", "07.03.2026"))),
        ), keyRatePercent)

        assertEquals(listOf(date("11.03.2026")), result.grantReport.overdueDays)
        assertEquals(listOf(date("06.03.2026")), result.extraBudgetReport.overdueDays)
        assertEquals(listOf(date("07.03.2026")), result.technicalReport.overdueDays)
        assertEquals(listOf(date("06.03.2026"), date("07.03.2026"), date("11.03.2026")), result.overdueDays)
        assertEquals(BigDecimal("0.02"), result.totalPenalty)
    }

    @Test
    fun `revision penalty is zero when all reports have no revision cases`() {
        val request = request()
        val result = service.calculateAllReportsRevisionPenalty(request.copy(
            grantReport = request.grantReport.copy(cases = emptyList()),
            extraBudgetReport = request.extraBudgetReport.copy(cases = emptyList()),
            technicalReport = request.technicalReport.copy(cases = emptyList()),
        ), keyRatePercent)

        for (report in listOf(result.grantReport, result.extraBudgetReport, result.technicalReport)) {
            assertEquals(emptyList<LocalDate>(), report.overdueDays)
            assertEquals(emptyList<ReportRevisionCase>(), report.cases)
        }
        assertEquals(emptyList<LocalDate>(), result.overdueDays)
        assertEquals(BigDecimal("0.00"), result.totalPenalty)
    }

    @Test
    fun `work completion counts weekends and year boundary`() {
        val request = request()
        val result = service.calculateStageCompletionPenalty(request.copy(technicalReport = request.technicalReport.copy(
            plannedCompletionDate = date("22.12.2025"),
            actualCompletionDate = date("10.01.2026"),
        )), keyRatePercent)

        assertEquals(listOf(
            date("23.12.2025"), date("24.12.2025"),
            date("25.12.2025"), date("26.12.2025"), date("27.12.2025"),
            date("28.12.2025"), date("29.12.2025"), date("30.12.2025"),
            date("31.12.2025"), date("01.01.2026"), date("02.01.2026"),
            date("03.01.2026"), date("04.01.2026"), date("05.01.2026"),
            date("06.01.2026"), date("07.01.2026"), date("08.01.2026"),
            date("09.01.2026"), date("10.01.2026"),
        ), result.overdueDays)
        assertEquals(BigDecimal("443333.34"), result.penalty)
    }

    @Test
    fun `uncorrected submissions retain their original deadline without duplicate days`() {
        val request = request().copy(grantReport = GrantReportDto(listOf(
            case("02.03.2026", "04.03.2026", remarksCorrected = false),
            case("05.03.2026", "06.03.2026", remarksCorrected = false),
            case("09.03.2026", "10.03.2026"),
        ), date("10.03.2026")))

        val result = service.calculateAllReportsRevisionPenalty(request, keyRatePercent)
        val grant = result.grantReport

        assertEquals(listOf(1, 1, 1), grant.cases.map { it.caseNumber })
        assertEquals(listOf(date("05.03.2026"), date("05.03.2026"), date("05.03.2026")), grant.cases.map { it.plannedSubmissionDate })
        assertEquals(emptyList<LocalDate>(), grant.cases[0].overdueDays)
        assertEquals(listOf(date("06.03.2026")), grant.cases[1].overdueDays)
        assertEquals(listOf(
            date("06.03.2026"), date("07.03.2026"), date("08.03.2026"),
            date("09.03.2026"), date("10.03.2026"),
        ), grant.cases[2].overdueDays)
        assertEquals(listOf(
            date("06.03.2026"), date("07.03.2026"), date("08.03.2026"),
            date("09.03.2026"), date("10.03.2026"),
        ), grant.overdueDays)
        assertEquals(BigDecimal("116666.67"), result.totalPenalty)
        assertEquals(grant.overdueDays, result.overdueDays)
    }

    @Test
    fun `on-time report does not extend another reports delay`() {
        val request = request().copy(
            grantReport = GrantReportDto(listOf(case("02.03.2026", "06.03.2026")), date("10.03.2026")),
            extraBudgetReport = ExtraBudgetReportDto(listOf(case("06.03.2026", "10.03.2026")), date("10.03.2026")),
        )

        val result = service.calculateAllReportsRevisionPenalty(request, keyRatePercent)

        assertEquals(listOf(date("06.03.2026")), result.grantReport.overdueDays)
        assertEquals(emptyList<LocalDate>(), result.extraBudgetReport.overdueDays)
        assertEquals(listOf(date("06.03.2026")), result.overdueDays)
        assertEquals(BigDecimal("23333.34"), result.totalPenalty)
    }

    @Test
    fun `rounds each penalty kind up before summing and counts identical revision dates once`() {
        val case = case("06.03.2026", "13.03.2026")
        val request = request().copy(
            stageAmount = BigDecimal("10.71"),
            reportPlannedSubmissionDate = date("04.03.2026"),
            reportActualSubmissionDate = date("05.03.2026"),
            grantReport = GrantReportDto(listOf(case), date("13.03.2026")),
            extraBudgetReport = ExtraBudgetReportDto(listOf(case), date("13.03.2026")),
            technicalReport = TechnicalReportDto(listOf(case), date("13.03.2026"), date("03.03.2026"), date("04.03.2026")),
        )

        val result = service.calculate(request)

        assertEquals(date("28.10.2026"), result.calculationDate)
        assertEquals(keyRatePercent, result.keyRatePercent)
        assertEquals(listOf(date("05.03.2026")), result.initialSubmissionPenalty.overdueDays)
        assertEquals(listOf(date("13.03.2026")), result.revisionsPenalty.overdueDays)
        assertEquals(listOf(date("04.03.2026")), result.stageCompletionPenalty.overdueDays)
        assertEquals(List(3) { listOf(date("13.03.2026")) }, listOf(
            result.revisionsPenalty.grantReport.overdueDays,
            result.revisionsPenalty.extraBudgetReport.overdueDays,
            result.revisionsPenalty.technicalReport.overdueDays,
        ))
        assertEquals(listOf(BigDecimal("0.01"), BigDecimal("0.01"), BigDecimal("0.01")), listOf(
            result.initialSubmissionPenalty.penalty,
            result.revisionsPenalty.totalPenalty,
            result.stageCompletionPenalty.penalty,
        ))
        assertEquals(BigDecimal("0.03"), result.totalPenalty)
    }

    @Test
    fun `all penalty kinds exclude planned date and include actual date`() {
        val request = request()
        val result = service.calculate(request.copy(
            reportPlannedSubmissionDate = date("31.12.2025"),
            reportActualSubmissionDate = date("01.01.2026"),
            grantReport = GrantReportDto(listOf(case("02.03.2026", "06.03.2026")), date("10.03.2026")),
            technicalReport = request.technicalReport.copy(
                plannedCompletionDate = date("31.12.2025"),
                actualCompletionDate = date("05.01.2026"),
            ),
        ))

        assertEquals(listOf(date("01.01.2026")), result.initialSubmissionPenalty.overdueDays)
        assertEquals(listOf(date("06.03.2026")), result.revisionsPenalty.grantReport.overdueDays)
        assertEquals(listOf(date("06.03.2026")), result.revisionsPenalty.grantReport.cases.single().overdueDays)
        assertEquals(emptyList<LocalDate>(), result.revisionsPenalty.extraBudgetReport.overdueDays)
        assertEquals(emptyList<LocalDate>(), result.revisionsPenalty.technicalReport.overdueDays)
        assertEquals(listOf(
            date("01.01.2026"), date("02.01.2026"), date("03.01.2026"),
            date("04.01.2026"), date("05.01.2026"),
        ), result.stageCompletionPenalty.overdueDays)
    }

    private fun request(): CalculatePenaltyRequestDto {
        val case = case("02.03.2026", "05.03.2026")
        return CalculatePenaltyRequestDto(
            contractNumber = "Г-0001",
            stageAmount = BigDecimal("50000000"),
            reportPlannedSubmissionDate = date("27.02.2026"),
            reportActualSubmissionDate = date("27.02.2026"),
            grantReport = GrantReportDto(listOf(case), date("10.03.2026")),
            extraBudgetReport = ExtraBudgetReportDto(listOf(case), date("10.03.2026")),
            technicalReport = TechnicalReportDto(listOf(case), date("10.03.2026"), date("26.02.2026"), date("26.02.2026")),
        )
    }

    private fun assertCases(input: List<ReportCaseDto>, calculated: List<ReportRevisionCase>, caseNumbers: List<Int>, plannedDates: List<LocalDate>) {
        assertEquals(input.size, calculated.size)
        assertEquals(caseNumbers, calculated.map { it.caseNumber })
        assertEquals(plannedDates, calculated.map { it.plannedSubmissionDate })
        for ((original, result) in input.zip(calculated)) {
            assertEquals(original.returnedForRevisionDate, result.returnedForRevisionDate)
            assertEquals(original.actualSubmissionDate, result.actualSubmissionDate)
            assertEquals(original.remarksCorrected, result.remarksCorrected)
        }
    }

    private fun assertStage(overdueDays: List<LocalDate>, actualPenalty: BigDecimal, penalty: String, expectedDays: List<LocalDate>) {
        assertEquals(expectedDays, overdueDays)
        assertEquals(BigDecimal(penalty), actualPenalty)
    }

    private fun case(returned: String, submitted: String, remarksCorrected: Boolean = true) = ReportCaseDto(
        returnedForRevisionDate = date(returned),
        actualSubmissionDate = date(submitted),
        remarksCorrected = remarksCorrected,
    )

    private fun date(value: String): LocalDate = LocalDate.parse(value, DATE_FORMATTER)
}
