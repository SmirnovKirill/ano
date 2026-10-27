package ru.inno_sc.penaltycalculation.api.penalty.response

data class PenaltyCalculationResultDto(
    val contractNumber: String,
    val stageAmount: String,
    val calculationDate: String,
    val keyRatePercent: String,
    val initialSubmissionPenalty: InitialSubmissionPenaltyDto,
    val revisionsPenalty: AllReportsRevisionPenaltyDto,
    val stageCompletionPenalty: StageCompletionPenaltyDto,
    val totalPenalty: String,
    val formula: String,
    val overdueDaysExplanation: String,
    val totalFormula: String,
)

data class InitialSubmissionPenaltyDto(
    val overdueDays: String,
    val title: String,
    val summary: String,
    val formula: String,
)

data class AllReportsRevisionPenaltyDto(
    val grantReport: SingleReportRevisionPenaltyDto,
    val extraBudgetReport: SingleReportRevisionPenaltyDto,
    val technicalReport: SingleReportRevisionPenaltyDto,
    val overdueDays: String,
    val title: String,
    val summary: String,
    val formula: String,
    val explanation: String,
    val caseExplanation: String,
)

data class SingleReportRevisionPenaltyDto(
    val overdueDays: String,
    val cases: List<ReportRevisionCaseDto>,
    val name: String,
    val overdueDaysCount: Int,
)

data class ReportRevisionCaseDto(
    val caseNumber: Int,
    val returnedForRevisionDate: String,
    val plannedSubmissionDate: String,
    val actualSubmissionDate: String,
    val remarks: String,
    val overdueDays: String,
)

data class StageCompletionPenaltyDto(
    val overdueDays: String,
    val title: String,
    val summary: String,
    val formula: String,
)
