package ru.inno_sc.penaltycalculation.service.domain

import java.math.BigDecimal
import java.math.RoundingMode.UP
import java.time.LocalDate

data class PenaltyCalculationResult(
    val stageNumber: Int,
    val contractNumber: String,
    val stageAmount: BigDecimal,
    val calculationDate: LocalDate,
    val keyRatePercent: BigDecimal,
    val initialSubmissionPenalty: InitialSubmissionPenalty,
    val revisionsPenalty: AllReportsRevisionPenalty,
    val stageCompletionPenalty: StageCompletionPenalty,
) {
    val totalOverdueDaysCount: Int get() = initialSubmissionPenalty.daysForCalculation.size + revisionsPenalty.daysForCalculation.size + stageCompletionPenalty.overdueDays.size
    val totalPenalty: BigDecimal get() = stageAmount.multiply(keyRatePercent).multiply(BigDecimal.valueOf(totalOverdueDaysCount.toLong())).divide(BigDecimal(30000), 2, UP)
}

data class InitialSubmissionPenalty(
    val overdueDays: List<LocalDate>,
    val daysToExcludeManually: List<LocalDate> = emptyList(),
) {
    val daysForCalculation: List<LocalDate> get() = overdueDays.filterNot { it in daysToExcludeManually }
}

data class AllReportsRevisionPenalty(
    val grantReport: SingleReportRevisionPenalty,
    val extraBudgetReport: SingleReportRevisionPenalty,
    val technicalReport: SingleReportRevisionPenalty,
) {
    val overdueDays: List<LocalDate> get() = (grantReport.overdueDays + extraBudgetReport.overdueDays + technicalReport.overdueDays).sorted().distinct()
    val daysToExcludeManually: List<LocalDate> get() = (grantReport.daysToExcludeManually + extraBudgetReport.daysToExcludeManually + technicalReport.daysToExcludeManually).sorted().distinct()
    val daysForCalculation: List<LocalDate> get() = overdueDays.filterNot { it in daysToExcludeManually }
}

data class SingleReportRevisionPenalty(
    val overdueDays: List<LocalDate>,
    val cases: List<ReportRevisionCase>,
    val daysToExcludeManually: List<LocalDate> = emptyList(),
) {
    val daysForCalculation: List<LocalDate> get() = overdueDays.filterNot { it in daysToExcludeManually }
}

data class ReportRevisionCase(
    val caseNumber: Int,
    val returnedForRevisionDate: LocalDate,
    val plannedSubmissionDate: LocalDate,
    val actualSubmissionDate: LocalDate,
    val remarksCorrected: Boolean,
    val overdueDays: List<LocalDate>,
    val daysToExcludeManually: List<LocalDate> = emptyList(),
) {
    val daysForCalculation: List<LocalDate> get() = overdueDays.filterNot { it in daysToExcludeManually }
}

data class StageCompletionPenalty(
    val overdueDays: List<LocalDate>,
)
