package ru.inno_sc.penaltycalculation.service.service

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import ru.inno_sc.penaltycalculation.api.penalty.request.CalculatePenaltyRequestDto
import ru.inno_sc.penaltycalculation.service.AbstractTest
import ru.inno_sc.penaltycalculation.service.config.CommonConfig.Companion.DATE_FORMATTER
import ru.inno_sc.penaltycalculation.service.controller.ReportController
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.io.ByteArrayInputStream
import java.time.LocalDate

class PenaltyCalculationExcelServiceTest : AbstractTest() {
    @Autowired
    private lateinit var controller: ReportController

    @Autowired
    private lateinit var mapper: JsonMapper

    @Test
    fun `exports a readable workbook with exact real-case amounts and report dates`() {
        val response = controller.exportPenalty(request())

        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", response.headers.contentType.toString())
        assertEquals("Г-0029_№4_28.10.2026.xlsx", ContentDisposition.parse(response.headers.getFirst(HttpHeaders.CONTENT_DISPOSITION)!!).filename)
        XSSFWorkbook(ByteArrayInputStream(requireNotNull(response.body))).use { workbook ->
            assertEquals(listOf("Итог", "Грант", "Внебюджет", "Тех"), (0 until workbook.numberOfSheets).map { workbook.getSheetName(it) })
            val summary = workbook.getSheet("Итог")
            fun value(label: String) = summary.first { it.getCell(0)?.stringCellValue == label }.getCell(1)
            assertEquals("Г-0029", value("Договор").stringCellValue)
            assertEquals("4", value("Номер этапа").stringCellValue)
            assertEquals("28.10.2026", value("Дата расчёта").stringCellValue)
            assertEquals("50\u00a0000\u00a0000,00\u00a0₽", value("Сумма этапа").stringCellValue)
            assertEquals("14%", value("Ключевая ставка").stringCellValue)
            assertEquals("956\u00a0666,67\u00a0₽", value("Итого").stringCellValue)
            assertEquals(CellType.STRING, value("Итого").cellType)

            val grant = workbook.getSheet("Грант")
            val header = grant.first { it.getCell(0)?.stringCellValue == "Кейс" }
            val firstCase = grant.getRow(header.rowNum + 1)
            assertEquals(listOf("1", "20.01.2026", "23.01.2026", "23.01.2026", "Исправлены", "Нет просрочки"), firstCase.map { it.stringCellValue })
            assertFalse(workbook.flatMap { sheet -> sheet.flatMap { row -> row.map { it.stringCellValue } } }.any { it.startsWith("Исходные дни:") })
        }
    }

    @Test
    fun `shows exclusion steps only when dates were actually excluded`() {
        val request = request()
        for ((dates, expectedTrace) in listOf(
            listOf(LocalDate.of(2024, 1, 1)) to false,
            listOf(LocalDate.of(2025, 12, 24)) to true,
        )) {
            val response = controller.exportPenalty(request.copy(daysToExcludeManually = dates))
            XSSFWorkbook(ByteArrayInputStream(requireNotNull(response.body))).use { workbook ->
                val text = workbook.flatMap { sheet -> sheet.flatMap { row -> row.map { it.stringCellValue } } }
                val steps = text.filter { it.startsWith("Исходные дни:") }
                assertEquals(expectedTrace, steps.isNotEmpty())
                if (expectedTrace) {
                    assertEquals(1, steps.size)
                    assertTrue(steps.single().contains("Исходные дни: 24.12.2025, 25.12.2025"))
                    assertTrue(steps.single().contains("Исключены вручную: 24.12.2025"))
                    assertTrue(steps.single().contains("Для расчёта: 25.12.2025"))
                }
            }
        }
    }

    private fun request(): CalculatePenaltyRequestDto {
        val json = requireNotNull(javaClass.getResourceAsStream("/static/test-case-1.json")).use { mapper.readTree(it) } as ObjectNode
        json.put("calculationDate", LocalDate.now(clock).format(DATE_FORMATTER))
        return mapper.treeToValue(json, CalculatePenaltyRequestDto::class.java).copy(stageNumber = 4)
    }
}
