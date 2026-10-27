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
        val days = getCalendarOverdueDays(reports.reportPlannedSubmissionDate, reports.reportActualSubmissionDate)
        return InitialSubmissionPenalty(days, penalty(days.size.toLong(), reports.stageAmount, keyRatePercent))
    }

    private fun getCalendarOverdueDays(planned: LocalDate, actual: LocalDate): List<LocalDate> =
        if (actual.isAfter(planned)) planned.plusDays(1).datesUntil(actual.plusDays(1)).toList() else emptyList()

    private fun penalty(days: Long, amount: BigDecimal, keyRatePercent: BigDecimal): BigDecimal =
        amount.multiply(keyRatePercent).multiply(BigDecimal.valueOf(days)).divide(BigDecimal(30000), 2, UP)

    fun calculateAllReportsRevisionPenalty(reports: CalculatePenaltyRequestDto, keyRatePercent: BigDecimal): AllReportsRevisionPenalty {
        val grantReport = calculateSingleReportRevisionPenalty(reports.grantReport.cases)
        val extraBudgetReport = calculateSingleReportRevisionPenalty(reports.extraBudgetReport.cases)
        val technicalReport = calculateSingleReportRevisionPenalty(reports.technicalReport.cases)
        val days = (grantReport.overdueDays + extraBudgetReport.overdueDays + technicalReport.overdueDays).distinct()
        return AllReportsRevisionPenalty(
            grantReport = grantReport,
            extraBudgetReport = extraBudgetReport,
            technicalReport = technicalReport,
            totalPenalty = penalty(days.size.toLong(), reports.stageAmount, keyRatePercent),
        )
    }

    private fun calculateSingleReportRevisionPenalty(cases: List<ReportCaseDto>): SingleReportRevisionPenalty {
        val reportRevisionCases = calculateReportRevisionCase(cases)
        val days = reportRevisionCases.flatMap { it.overdueDays }.distinct().sorted()
        return SingleReportRevisionPenalty(days, reportRevisionCases)
    }

    private fun calculateReportRevisionCase(cases: List<ReportCaseDto>): List<ReportRevisionCase> {
        val result = mutableListOf<ReportRevisionCase>()
        for ((returnedForRevisionDate, actualSubmissionDate, remarksCorrected) in cases) {
            val previous = result.lastOrNull()
            val caseNumber = when {
                previous == null -> 1
                previous.remarksCorrected -> previous.caseNumber + 1
                else -> previous.caseNumber
            }
            val planned = if (previous != null && !previous.remarksCorrected) {
                // Неисправленная попытка не завершает кейс и не даёт нового срока.
                previous.plannedSubmissionDate
            } else {
                reportRevisionPlannedDateService.getReportRevisionPlannedDate(returnedForRevisionDate, caseNumber)
            }
            result += ReportRevisionCase(caseNumber, returnedForRevisionDate, planned, actualSubmissionDate, remarksCorrected, getCalendarOverdueDays(planned, actualSubmissionDate))
        }
        return result
    }

    fun calculateStageCompletionPenalty(reports: CalculatePenaltyRequestDto, keyRatePercent: BigDecimal): StageCompletionPenalty {
        val technicalReport = reports.technicalReport
        val days = getCalendarOverdueDays(technicalReport.plannedCompletionDate, technicalReport.actualCompletionDate)
        return StageCompletionPenalty(days, penalty(days.size.toLong(), reports.stageAmount, keyRatePercent))
    }
}
