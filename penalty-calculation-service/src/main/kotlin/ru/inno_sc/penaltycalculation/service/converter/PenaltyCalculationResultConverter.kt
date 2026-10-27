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
        contractNumber = contractNumber,
        stageAmount = money(stageAmount),
        calculationDate = calculationDate.format(DATE_FORMATTER),
        keyRatePercent = "${rate(keyRatePercent)}%",
        initialSubmissionPenalty = InitialSubmissionPenaltyDto(
            overdueDays = days(initialSubmissionPenalty.overdueDays),
            title = "1. Неустойка по несвоевременной первой сдаче отчёта",
            summary = summary(initialSubmissionPenalty.overdueDays.size, initialSubmissionPenalty.penalty),
            formula = formula(initialSubmissionPenalty.overdueDays.size, initialSubmissionPenalty.penalty),
        ),
        revisionsPenalty = AllReportsRevisionPenaltyDto(
            grantReport = revisionsPenalty.grantReport.toDto("Грант"),
            extraBudgetReport = revisionsPenalty.extraBudgetReport.toDto("Внебюджет"),
            technicalReport = revisionsPenalty.technicalReport.toDto("Технический отчёт"),
            overdueDays = days(revisionsPenalty.overdueDays),
            title = "2. Неустойка по несвоевременной доработке отчёта",
            summary = "Уникальных дней просрочки: ${revisionsPenalty.overdueDays.size}. Неустойка: ${money(revisionsPenalty.totalPenalty)}.",
            formula = formula(revisionsPenalty.overdueDays.size, revisionsPenalty.totalPenalty),
            explanation = "Неустойка по доработке рассчитывается по уникальным дням просрочки всех трёх отчётов. Если одна дата встречается в нескольких отчётах, она учитывается один раз.",
            caseExplanation = "Неисправленные попытки подряд продолжают один кейс с прежним номером и исходной плановой датой. Просрочка длится до подачи исправленного отчёта, включая промежутки между попытками; внутри одного отчёта повторяющиеся дни учитываются один раз.",
        ),
        stageCompletionPenalty = StageCompletionPenaltyDto(
            overdueDays = days(stageCompletionPenalty.overdueDays),
            title = "3. Неустойка по просрочке выполнения работ этапа",
            summary = summary(stageCompletionPenalty.overdueDays.size, stageCompletionPenalty.penalty),
            formula = formula(stageCompletionPenalty.overdueDays.size, stageCompletionPenalty.penalty),
        ),
        totalPenalty = money(totalPenalty),
        formula = "Формула: сумма этапа × ключевая ставка ЦБ / 100 × число дней просрочки / 300. Каждая сумма округляется вверх до копейки.",
        overdueDaysExplanation = "Календарные дни просрочки: со следующего дня после плановой даты по фактическую дату включительно.",
        totalFormula = "${money(initialSubmissionPenalty.penalty)} + ${money(revisionsPenalty.totalPenalty)} + ${money(stageCompletionPenalty.penalty)} = ${money(totalPenalty)}",
    )
}

private fun SingleReportRevisionPenalty.toDto(name: String) = SingleReportRevisionPenaltyDto(
    overdueDays = days(overdueDays),
    cases = cases.map { case ->
        ReportRevisionCaseDto(
            caseNumber = case.caseNumber,
            returnedForRevisionDate = case.returnedForRevisionDate.format(DATE_FORMATTER),
            plannedSubmissionDate = case.plannedSubmissionDate.format(DATE_FORMATTER),
            actualSubmissionDate = case.actualSubmissionDate.format(DATE_FORMATTER),
            remarks = if (case.remarksCorrected) "Исправлены" else "Не исправлены — срок сохраняется",
            overdueDays = days(case.overdueDays),
        )
    },
    name = name,
    overdueDaysCount = overdueDays.size,
)

private fun money(value: BigDecimal): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("ru-RU")).format(value)

private fun rate(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

private fun days(values: List<LocalDate>): String = values.takeIf { it.isNotEmpty() }
    ?.joinToString(", ") { it.format(DATE_FORMATTER) } ?: "Нет просрочки"

private fun PenaltyCalculationResult.formula(dayCount: Int, penalty: BigDecimal): String =
    "${money(stageAmount)} × ${rate(keyRatePercent)} / 100 × $dayCount / 300 = ${money(penalty)}"

private fun summary(dayCount: Int, penalty: BigDecimal): String = "Дней просрочки: $dayCount. Неустойка: ${money(penalty)}."
