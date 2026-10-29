package ru.inno_sc.penaltycalculation.service.converter

import ru.inno_sc.penaltycalculation.api.penalty.response.AllReportsRevisionPenaltyDto
import ru.inno_sc.penaltycalculation.api.penalty.response.InitialSubmissionPenaltyDto
import ru.inno_sc.penaltycalculation.api.penalty.response.PenaltyCalculationResultDto
import ru.inno_sc.penaltycalculation.api.penalty.response.ReportRevisionCaseDto
import ru.inno_sc.penaltycalculation.api.penalty.response.SingleReportRevisionPenaltyDto
import ru.inno_sc.penaltycalculation.api.penalty.response.StageCompletionPenaltyDto
import ru.inno_sc.penaltycalculation.service.domain.PenaltyCalculationResult
import ru.inno_sc.penaltycalculation.service.domain.SingleReportRevisionPenalty
import ru.inno_sc.penaltycalculation.service.config.CommonConfig.Companion.DATE_FORMATTER
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

fun PenaltyCalculationResult.toDto(): PenaltyCalculationResultDto {
    return PenaltyCalculationResultDto(
        stageNumber = stageNumber,
        contractNumber = contractNumber,
        stageAmount = money(stageAmount),
        calculationDate = calculationDate.format(DATE_FORMATTER),
        keyRatePercent = "${rate(keyRatePercent)}%",
        initialSubmissionPenalty = InitialSubmissionPenaltyDto(
            daysForCalculation = days(initialSubmissionPenalty.daysForCalculation),
            overdueDays = initialSubmissionPenalty.overdueDays,
            daysToExcludeManually = initialSubmissionPenalty.daysToExcludeManually,
            overdueDaysCount = initialSubmissionPenalty.daysForCalculation.size,
            title = "1. Неустойка по несвоевременной первой сдаче отчёта",
        ),
        revisionsPenalty = AllReportsRevisionPenaltyDto(
            grantReport = revisionsPenalty.grantReport.toDto("Грант"),
            extraBudgetReport = revisionsPenalty.extraBudgetReport.toDto("Внебюджет"),
            technicalReport = revisionsPenalty.technicalReport.toDto("Технический отчёт"),
            daysForCalculation = days(revisionsPenalty.daysForCalculation),
            overdueDays = revisionsPenalty.overdueDays,
            daysToExcludeManually = revisionsPenalty.daysToExcludeManually,
            overdueDaysCount = revisionsPenalty.daysForCalculation.size,
            title = "2. Неустойка по несвоевременной доработке отчёта",
            explanation = "Неустойка по доработке рассчитывается по уникальным дням просрочки всех трёх отчётов. Если одна дата встречается в нескольких отчётах, она учитывается один раз.",
            caseExplanation = "Неисправленные попытки подряд продолжают один кейс с прежним номером и исходной плановой датой. Просрочка длится до подачи исправленного отчёта, включая промежутки между попытками; внутри одного отчёта повторяющиеся дни учитываются один раз.",
        ),
        stageCompletionPenalty = StageCompletionPenaltyDto(
            overdueDays = days(stageCompletionPenalty.overdueDays),
            overdueDaysCount = stageCompletionPenalty.overdueDays.size,
            title = "3. Неустойка по просрочке выполнения работ этапа",
        ),
        totalOverdueDaysCount = totalOverdueDaysCount,
        totalPenalty = money(totalPenalty),
        formula = "Формула: сумма этапа × ключевая ставка ЦБ / 100 × сумма дней просрочки по трём типам / 300. Итог округляется вверх до копейки.",
        totalFormula = "${money(stageAmount)} × ${rate(keyRatePercent)} / 100 × (${initialSubmissionPenalty.daysForCalculation.size} + ${revisionsPenalty.daysForCalculation.size} + ${stageCompletionPenalty.overdueDays.size}) / 300 = ${money(totalPenalty)}",
    )
}

private fun SingleReportRevisionPenalty.toDto(name: String) = SingleReportRevisionPenaltyDto(
    daysForCalculation = days(daysForCalculation),
    overdueDays = overdueDays,
    daysToExcludeManually = daysToExcludeManually,
    cases = cases.map { case ->
        ReportRevisionCaseDto(
            caseNumber = case.caseNumber,
            returnedForRevisionDate = case.returnedForRevisionDate.format(DATE_FORMATTER),
            plannedSubmissionDate = case.plannedSubmissionDate.format(DATE_FORMATTER),
            actualSubmissionDate = case.actualSubmissionDate.format(DATE_FORMATTER),
            remarks = if (case.remarksCorrected) "Исправлены" else "Возобновляется фиксация нарушения срока.",
            daysForCalculation = days(case.daysForCalculation),
            overdueDays = case.overdueDays,
            daysToExcludeManually = case.daysToExcludeManually,
        )
    },
    name = name,
    overdueDaysCount = daysForCalculation.size,
)

private fun money(value: BigDecimal): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("ru-RU")).format(value)

private fun rate(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

private fun days(values: List<LocalDate>): String = values.joinToString(", ") { it.format(DATE_FORMATTER) }.ifEmpty { "нет" }
