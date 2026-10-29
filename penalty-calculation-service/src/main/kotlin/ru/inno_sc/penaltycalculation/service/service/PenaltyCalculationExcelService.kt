package ru.inno_sc.penaltycalculation.service.service

import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import ru.inno_sc.penaltycalculation.api.penalty.request.CalculatePenaltyRequestDto
import ru.inno_sc.penaltycalculation.api.penalty.response.PenaltyCalculationResultDto
import ru.inno_sc.penaltycalculation.api.penalty.response.SingleReportRevisionPenaltyDto
import ru.inno_sc.penaltycalculation.service.config.CommonConfig.Companion.DATE_FORMATTER
import java.io.ByteArrayOutputStream
import java.time.LocalDate

class PenaltyCalculationExcelService {
    fun generate(request: CalculatePenaltyRequestDto, result: PenaltyCalculationResultDto): ByteArray = XSSFWorkbook().use { workbook ->
        val textStyle = workbook.createCellStyle().apply {
            wrapText = true
            verticalAlignment = VerticalAlignment.TOP
        }
        val headingStyle = workbook.createCellStyle().apply {
            cloneStyleFrom(textStyle)
            setFont(workbook.createFont().apply { bold = true })
        }
        val summary = workbook.createSheet("Итог").apply {
            setColumnWidth(0, 48 * 256)
            setColumnWidth(1, 100 * 256)
        }
        row(summary, headingStyle, "Расчёт неустойки")
        row(summary, textStyle, "Договор", result.contractNumber)
        row(summary, textStyle, "Дата расчёта", result.calculationDate)
        row(summary, textStyle, "Сумма этапа", result.stageAmount)
        row(summary, textStyle, "Ключевая ставка", result.keyRatePercent)
        row(summary, textStyle, "Плановая подача отчётности", request.reportPlannedSubmissionDate.format(DATE_FORMATTER))
        row(summary, textStyle, "Фактическая подача отчётности", request.reportActualSubmissionDate.format(DATE_FORMATTER))
        row(summary, textStyle, "Плановое завершение работ", request.technicalReport.plannedCompletionDate.format(DATE_FORMATTER))
        row(summary, textStyle, "Фактическое завершение работ", request.technicalReport.actualCompletionDate.format(DATE_FORMATTER))
        row(summary, textStyle, "Формула", result.formula)
        row(summary, textStyle, "Правила подсчёта дней", result.overdueDaysExplanation)
        result.initialSubmissionPenalty.let {
            stage(summary, textStyle, headingStyle, it.title, it.summary, it.formula, it.daysForCalculation, it.overdueDays, it.daysToExcludeManually)
        }
        result.revisionsPenalty.let {
            stage(summary, textStyle, headingStyle, it.title, it.summary, it.formula, it.daysForCalculation, it.overdueDays, it.daysToExcludeManually)
            row(summary, textStyle, "Правило доработки", it.explanation)
        }
        result.stageCompletionPenalty.let { stage(summary, textStyle, headingStyle, it.title, it.summary, it.formula, it.overdueDays) }
        row(summary, headingStyle, "Итого", result.totalPenalty)
        row(summary, textStyle, "Сложение неустоек", result.totalFormula)

        report(workbook, textStyle, headingStyle, "Грант", result.revisionsPenalty.grantReport, request.grantReport.reportApprovalDate)
        report(workbook, textStyle, headingStyle, "Внебюджет", result.revisionsPenalty.extraBudgetReport, request.extraBudgetReport.reportApprovalDate)
        report(workbook, textStyle, headingStyle, "Тех", result.revisionsPenalty.technicalReport, request.technicalReport.reportApprovalDate)
        ByteArrayOutputStream().use { output ->
            workbook.write(output)
            output.toByteArray()
        }
    }

    private fun row(sheet: Sheet, style: CellStyle, vararg values: String) {
        val row = sheet.createRow(sheet.physicalNumberOfRows)
        values.forEachIndexed { index, value ->
            row.createCell(index).apply {
                setCellValue(value)
                cellStyle = style
            }
        }
        val lines = values.mapIndexed { index, value ->
            val width = (sheet.getColumnWidth(index) / 256 - 2).coerceAtLeast(8)
            value.lines().sumOf { (it.length + width - 1).div(width).coerceAtLeast(1) }
        }.maxOrNull() ?: 1
        row.heightInPoints = (lines * 15f + 5).coerceAtMost(400f)
    }

    private fun days(values: List<LocalDate>): String = values.joinToString(", ") { it.format(DATE_FORMATTER) }

    private fun daySteps(overdueDays: List<LocalDate>, daysToExcludeManually: List<LocalDate>, daysForCalculation: String): String =
        if (daysToExcludeManually.isEmpty()) daysForCalculation else
            "Исходные дни: ${days(overdueDays)}\nИсключены вручную: ${days(daysToExcludeManually)}\nДля расчёта: $daysForCalculation"

    private fun stage(
        sheet: Sheet,
        textStyle: CellStyle,
        headingStyle: CellStyle,
        title: String,
        summary: String,
        formula: String,
        daysForCalculation: String,
        overdueDays: List<LocalDate> = emptyList(),
        daysToExcludeManually: List<LocalDate> = emptyList(),
    ) {
        row(sheet, headingStyle, title)
        row(sheet, textStyle, "Дни просрочки", daySteps(overdueDays, daysToExcludeManually, daysForCalculation))
        row(sheet, textStyle, "Неустойка", summary)
        row(sheet, textStyle, "Расчёт", formula)
    }

    private fun report(
        workbook: XSSFWorkbook,
        textStyle: CellStyle,
        headingStyle: CellStyle,
        name: String,
        report: SingleReportRevisionPenaltyDto,
        approvalDate: LocalDate,
    ) {
        val sheet = workbook.createSheet(name)
        listOf(9, 18, 18, 18, 42, 85).forEachIndexed { index, width -> sheet.setColumnWidth(index, width * 256) }
        row(sheet, headingStyle, report.name)
        row(sheet, textStyle, "Дата согласования", approvalDate.format(DATE_FORMATTER))
        row(sheet, textStyle, "Дней просрочки", report.overdueDaysCount.toString())
        row(sheet, textStyle, "Дни просрочки", daySteps(report.overdueDays, report.daysToExcludeManually, report.daysForCalculation))
        row(sheet, headingStyle, "Кейс", "Возврат на доработку", "Плановая подача", "Фактическая подача", "Замечания", "Дни просрочки")
        report.cases.forEach { case ->
            row(sheet, textStyle, case.caseNumber.toString(), case.returnedForRevisionDate, case.plannedSubmissionDate,
                case.actualSubmissionDate, case.remarks, daySteps(case.overdueDays, case.daysToExcludeManually, case.daysForCalculation))
        }
    }
}
