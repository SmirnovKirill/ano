package ru.inno_sc.penaltycalculation.service.domain

import java.math.BigDecimal
import java.time.LocalDate

data class PenaltyCalculationResult(
    val contractNumber: String,
    val stageAmount: BigDecimal,
    val calculationDate: LocalDate,
    val keyRatePercent: BigDecimal,
    val initialSubmissionPenalty: InitialSubmissionPenalty,
    val revisionsPenalty: AllReportsRevisionPenalty,
    val stageCompletionPenalty: StageCompletionPenalty
) {
    val totalPenalty: BigDecimal get() = initialSubmissionPenalty.penalty + revisionsPenalty.totalPenalty + stageCompletionPenalty.penalty
}

data class InitialSubmissionPenalty(
    val overdueDays: List<LocalDate>,
    val penalty: BigDecimal,
)

data class AllReportsRevisionPenalty(
    val grantReport: SingleReportRevisionPenalty,
    val extraBudgetReport: SingleReportRevisionPenalty,
    val technicalReport: SingleReportRevisionPenalty,
    val totalPenalty: BigDecimal
) {
    val overdueDays: List<LocalDate> = (grantReport.overdueDays + extraBudgetReport.overdueDays + technicalReport.overdueDays).sorted().distinct()
}

data class SingleReportRevisionPenalty(
    val overdueDays: List<LocalDate>,
    val cases: List<ReportRevisionCase>,
)

data class ReportRevisionCase(
    val caseNumber: Int,
    val returnedForRevisionDate: LocalDate,
    val plannedSubmissionDate: LocalDate,
    val actualSubmissionDate: LocalDate,
    val remarksCorrected: Boolean,
    val overdueDays: List<LocalDate>,
)

data class StageCompletionPenalty(
    val overdueDays: List<LocalDate>,
    val penalty: BigDecimal,
)
