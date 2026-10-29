package ru.inno_sc.penaltycalculation.service.service

import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.Row
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
        row(summary, textStyle, "Номер этапа", result.stageNumber.toString())
        row(summary, textStyle, "Дата расчёта", result.calculationDate)
        row(summary, textStyle, "Сумма этапа", result.stageAmount)
        row(summary, textStyle, "Ключевая ставка", result.keyRatePercent)
        row(summary, textStyle, "Плановая подача отчётности", request.reportPlannedSubmissionDate.format(DATE_FORMATTER))
        row(summary, textStyle, "Фактическая подача отчётности", request.reportActualSubmissionDate.format(DATE_FORMATTER))
        row(summary, textStyle, "Плановое завершение работ", request.technicalReport.plannedCompletionDate.format(DATE_FORMATTER))
        row(summary, textStyle, "Фактическое завершение работ", request.technicalReport.actualCompletionDate.format(DATE_FORMATTER))
        result.initialSubmissionPenalty.let {
            stage(summary, textStyle, headingStyle, it.title, it.daysForCalculation, it.overdueDaysCount, it.overdueDays, it.daysToExcludeManually)
        }
        result.revisionsPenalty.let {
            stage(summary, textStyle, headingStyle, it.title, it.daysForCalculation, it.overdueDaysCount, it.overdueDays, it.daysToExcludeManually)
            row(summary, textStyle, "Правило доработки", it.explanation)
        }
        result.stageCompletionPenalty.let { stage(summary, textStyle, headingStyle, it.title, it.overdueDays, it.overdueDaysCount) }
        row(summary, textStyle)
        row(summary, headingStyle, "Всего дней просрочки", result.totalOverdueDaysCount.toString())
        row(summary, textStyle, "Формула", result.formula)
        row(summary, textStyle, "Итоговый расчёт", result.totalFormula)
        row(summary, headingStyle, "Итого", result.totalPenalty)

        report(workbook, textStyle, headingStyle, "Грант", result.revisionsPenalty.grantReport, request.grantReport.reportApprovalDate)
        report(workbook, textStyle, headingStyle, "Внебюджет", result.revisionsPenalty.extraBudgetReport, request.extraBudgetReport.reportApprovalDate)
        report(workbook, textStyle, headingStyle, "Тех", result.revisionsPenalty.technicalReport, request.technicalReport.reportApprovalDate)
        ByteArrayOutputStream().use { output ->
            workbook.write(output)
            output.toByteArray()
        }
    }

    private fun row(sheet: Sheet, style: CellStyle, vararg values: String): Row {
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
        return row
    }

    private fun days(values: List<LocalDate>): String = values.joinToString(", ") { it.format(DATE_FORMATTER) }

    private fun daySteps(
        overdueDays: List<LocalDate>,
        daysToExcludeManually: List<LocalDate>,
        daysForCalculation: String,
        count: Int = overdueDays.size - daysToExcludeManually.size,
    ): List<Pair<String, String>> = (if (daysToExcludeManually.isEmpty()) emptyList() else listOf(
        "Исходные дни просрочки (${overdueDays.size})" to days(overdueDays),
        "Исключены вручную (${daysToExcludeManually.size})" to days(daysToExcludeManually),
    )) + ("Дни просрочки ($count)" to daysForCalculation)

    private fun dayRows(
        sheet: Sheet,
        textStyle: CellStyle,
        headingStyle: CellStyle,
        daysForCalculation: String,
        count: Int,
        overdueDays: List<LocalDate>,
        daysToExcludeManually: List<LocalDate>,
    ) {
        daySteps(overdueDays, daysToExcludeManually, daysForCalculation, count).forEach { (label, dates) ->
            row(sheet, textStyle, label, dates).getCell(0).cellStyle = headingStyle
        }
    }

    private fun stage(
        sheet: Sheet,
        textStyle: CellStyle,
        headingStyle: CellStyle,
        title: String,
        daysForCalculation: String,
        count: Int,
        overdueDays: List<LocalDate> = emptyList(),
        daysToExcludeManually: List<LocalDate> = emptyList(),
    ) {
        row(sheet, headingStyle, title)
        dayRows(sheet, textStyle, headingStyle, daysForCalculation, count, overdueDays, daysToExcludeManually)
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
        dayRows(sheet, textStyle, headingStyle, report.daysForCalculation, report.overdueDaysCount, report.overdueDays, report.daysToExcludeManually)
        row(sheet, headingStyle, "Кейс", "Возврат на доработку", "Плановая подача", "Фактическая подача", "Замечания", "Дни просрочки")
        report.cases.forEach { case ->
            row(sheet, textStyle, case.caseNumber.toString(), case.returnedForRevisionDate, case.plannedSubmissionDate,
                case.actualSubmissionDate, case.remarks, daySteps(case.overdueDays, case.daysToExcludeManually, case.daysForCalculation)
                    .joinToString("\n") { (label, dates) -> "$label: $dates" })
        }
    }
}
