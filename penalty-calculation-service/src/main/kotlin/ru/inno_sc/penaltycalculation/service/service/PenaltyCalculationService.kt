package ru.inno_sc.penaltycalculation.service.service

import ru.inno_sc.penaltycalculation.api.penalty.request.ReportCaseDto
import ru.inno_sc.penaltycalculation.api.penalty.request.CalculatePenaltyRequestDto
import ru.inno_sc.penaltycalculation.service.domain.PenaltyCalculationResult
import ru.inno_sc.penaltycalculation.service.domain.InitialSubmissionPenalty
import ru.inno_sc.penaltycalculation.service.domain.SingleReportRevisionPenalty
import ru.inno_sc.penaltycalculation.service.domain.ReportRevisionCase
import ru.inno_sc.penaltycalculation.service.domain.AllReportsRevisionPenalty
import ru.inno_sc.penaltycalculation.service.domain.StageCompletionPenalty
import ru.inno_sc.penaltycalculation.service.validation.PenaltyCalculationValidator
import java.math.BigDecimal
import java.math.RoundingMode.UP
import java.time.Clock
import java.time.LocalDate

class PenaltyCalculationService(
    private val reportRevisionPlannedDateService: ReportRevisionPlannedDateService,
    private val keyRates: KeyRateService,
    private val validator: PenaltyCalculationValidator,
    private val clock: Clock,
) {
    fun calculate(reports: CalculatePenaltyRequestDto): PenaltyCalculationResult {
        val calculationDate = LocalDate.now(clock)
        validator.validate(reports, calculationDate)
        val keyRatePercent = keyRates.rateOn(calculationDate)

        val initialSubmissionPenalty = calculateInitialSubmissionPenalty(reports, keyRatePercent)
        val revisionsPenalty = calculateAllReportsRevisionPenalty(reports, keyRatePercent)
        val stageCompletionPenalty = calculateStageCompletionPenalty(reports, keyRatePercent)

        return PenaltyCalculationResult(
            contractNumber = reports.contractNumber,
            calculationDate = calculationDate,
            stageAmount = reports.stageAmount,
            keyRatePercent = keyRatePercent,
            initialSubmissionPenalty = initialSubmissionPenalty,
            revisionsPenalty = revisionsPenalty,
            stageCompletionPenalty = stageCompletionPenalty,
        )
    }

    fun calculateInitialSubmissionPenalty(reports: CalculatePenaltyRequestDto, keyRatePercent: BigDecimal): InitialSubmissionPenalty {
        val overdueDays = getCalendarOverdueDays(reports.reportPlannedSubmissionDate, reports.reportActualSubmissionDate)
        val daysToExcludeManually = overdueDays.filter { it in reports.daysToExcludeManually }
        return InitialSubmissionPenalty(
            penalty = penalty((overdueDays.size - daysToExcludeManually.size).toLong(), reports.stageAmount, keyRatePercent),
            overdueDays = overdueDays,
            daysToExcludeManually = daysToExcludeManually,
        )
    }

    private fun getCalendarOverdueDays(planned: LocalDate, actual: LocalDate): List<LocalDate> =
        if (actual.isAfter(planned)) planned.plusDays(1).datesUntil(actual.plusDays(1)).toList() else emptyList()

    private fun penalty(overdueDaysCount: Long, amount: BigDecimal, keyRatePercent: BigDecimal): BigDecimal =
        amount.multiply(keyRatePercent).multiply(BigDecimal.valueOf(overdueDaysCount)).divide(BigDecimal(30000), 2, UP)

    fun calculateAllReportsRevisionPenalty(reports: CalculatePenaltyRequestDto, keyRatePercent: BigDecimal): AllReportsRevisionPenalty {
        val daysToExcludeManually = reports.daysToExcludeManually.toSet()
        val grantReport = calculateSingleReportRevisionPenalty(reports.grantReport.cases, daysToExcludeManually)
        val extraBudgetReport = calculateSingleReportRevisionPenalty(reports.extraBudgetReport.cases, daysToExcludeManually)
        val technicalReport = calculateSingleReportRevisionPenalty(reports.technicalReport.cases, daysToExcludeManually)
        val result = AllReportsRevisionPenalty(
            grantReport = grantReport,
            extraBudgetReport = extraBudgetReport,
            technicalReport = technicalReport,
            totalPenalty = BigDecimal.ZERO,
        )
        return result.copy(totalPenalty = penalty(result.daysForCalculation.size.toLong(), reports.stageAmount, keyRatePercent))
    }

    private fun calculateSingleReportRevisionPenalty(cases: List<ReportCaseDto>, daysToExcludeManually: Set<LocalDate>): SingleReportRevisionPenalty {
        val reportRevisionCases = calculateReportRevisionCase(cases, daysToExcludeManually)
        return SingleReportRevisionPenalty(
            cases = reportRevisionCases,
            overdueDays = reportRevisionCases.flatMap { it.overdueDays }.distinct().sorted(),
            daysToExcludeManually = reportRevisionCases.flatMap { it.daysToExcludeManually }.distinct().sorted(),
        )
    }

    private fun calculateReportRevisionCase(cases: List<ReportCaseDto>, daysToExcludeManually: Set<LocalDate>): List<ReportRevisionCase> {
        val result = mutableListOf<ReportRevisionCase>()
        for ((returnedForRevisionDate, actualSubmissionDate, remarksCorrected) in cases) {
            val previous = result.lastOrNull()
            val caseNumber = when {
                previous == null -> 1
                previous.remarksCorrected -> previous.caseNumber + 1
                else -> previous.caseNumber
            }
            val plannedSubmissionDate = if (previous != null && !previous.remarksCorrected) {
                // Неисправленная попытка не завершает кейс и не даёт нового срока.
                previous.plannedSubmissionDate
            } else {
                reportRevisionPlannedDateService.getReportRevisionPlannedDate(returnedForRevisionDate, caseNumber)
            }
            val overdueDays = getCalendarOverdueDays(plannedSubmissionDate, actualSubmissionDate)
            result += ReportRevisionCase(
                caseNumber, returnedForRevisionDate, plannedSubmissionDate, actualSubmissionDate, remarksCorrected,
                overdueDays = overdueDays,
                daysToExcludeManually = overdueDays.filter { it in daysToExcludeManually },
            )
        }
        return result
    }

    fun calculateStageCompletionPenalty(reports: CalculatePenaltyRequestDto, keyRatePercent: BigDecimal): StageCompletionPenalty {
        val technicalReport = reports.technicalReport
        val overdueDays = if (technicalReport.actualCompletionDate.isAfter(technicalReport.plannedCompletionDate)) {
            technicalReport.plannedCompletionDate.plusDays(1).datesUntil(technicalReport.actualCompletionDate).toList()
        } else emptyList() //Тут свои правила расчета, это корректная логика
        return StageCompletionPenalty(overdueDays, penalty(overdueDays.size.toLong(), reports.stageAmount, keyRatePercent))
    }
}
