package ru.inno_sc.penaltycalculation.api.penalty.request

import java.math.BigDecimal
import java.time.LocalDate

data class CalculatePenaltyRequestDto(
    val contractNumber: String,
    val stageAmount: BigDecimal,
    val reportPlannedSubmissionDate: LocalDate,
    val reportActualSubmissionDate: LocalDate,
    val grantReport: GrantReportDto,
    val extraBudgetReport: ExtraBudgetReportDto,
    val technicalReport: TechnicalReportDto,
)
