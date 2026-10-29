package ru.inno_sc.penaltycalculation.service.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Disabled
import org.springframework.beans.factory.annotation.Autowired
import ru.inno_sc.penaltycalculation.api.penalty.request.CalculatePenaltyRequestDto
import ru.inno_sc.penaltycalculation.service.AbstractTest
import ru.inno_sc.penaltycalculation.service.config.CommonConfig.Companion.DATE_FORMATTER
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.math.BigDecimal
import java.time.LocalDate

class RealCasesPenalyServiceTest : AbstractTest() {
    @Autowired
    private lateinit var service: PenaltyCalculationService

    @Autowired
    private lateinit var mapper: JsonMapper

    @Test
    fun `ROGA I KORYTA from test-case-1 json`() {
        // РОГА И КОРЫТА.xlsx: сумма — «Расчет вручную»!I1, даты работ — D12.
        // Тех!I11: комментарий «ОТЧЕТ НЕ БЫЛ ИСПРАВЛЕН»; строка 12 продолжает кейс 2.
        val request = readRequest("test-case-1.json")
        val result = service.calculate(request)

        assertEquals("Г-0029", request.contractNumber)
        assertEquals(BigDecimal("50000000"), request.stageAmount)
        assertEquals(listOf(4, 4, 7), listOf(request.grantReport.cases.size, request.extraBudgetReport.cases.size, request.technicalReport.cases.size))
        assertFalse(request.technicalReport.cases[1].remarksCorrected)
        assertEquals(BigDecimal("14.00"), result.keyRatePercent)
        assertEquals(2, result.initialSubmissionPenalty.daysForCalculation.size)
        assertEquals(21, result.revisionsPenalty.daysForCalculation.size)
        assertEquals(listOf(2, 3, 19), listOf(result.revisionsPenalty.grantReport, result.revisionsPenalty.extraBudgetReport, result.revisionsPenalty.technicalReport).map { it.daysForCalculation.size })
        assertEquals(listOf(1, 2, 2, 3, 4, 5, 6), result.revisionsPenalty.technicalReport.cases.map { it.caseNumber })
        assertEquals(18, result.stageCompletionPenalty.overdueDays.size)
        assertEquals(date("23.12.2025"), result.stageCompletionPenalty.overdueDays.first())
        assertEquals(date("09.01.2026"), result.stageCompletionPenalty.overdueDays.last())
        assertEquals(BigDecimal("46666.67"), result.initialSubmissionPenalty.penalty)
        assertEquals(BigDecimal("490000.00"), result.revisionsPenalty.totalPenalty)
        assertEquals(BigDecimal("420000.00"), result.stageCompletionPenalty.penalty)
        assertEquals(BigDecimal("956666.67"), result.totalPenalty)
    }

    @Test
    fun `INTRA stage 3 from test-case-2 json`() {
        // ООО Сервисная компания ИНТРА_расчет просрочки (3 этап) (есть неустойка типа 3).xlsx.
        // Сроки работ — «ВАЖНО ПРО НЕУСТОЙКУ 3»!A1; сумму 37 793 453,05 ₽ сообщил пользователь.
        val request = readRequest("test-case-2.json")
        val result = service.calculate(request)

        assertEquals("Г-0014", request.contractNumber)
        assertEquals(BigDecimal("37793453.05"), request.stageAmount)
        assertEquals(listOf(4, 4, 3), listOf(request.grantReport.cases.size, request.extraBudgetReport.cases.size, request.technicalReport.cases.size))
        assertEquals(date("25.09.2025"), request.technicalReport.plannedCompletionDate)
        assertEquals(date("13.10.2025"), request.technicalReport.actualCompletionDate)
        assertEquals(BigDecimal("14.00"), result.keyRatePercent)
        assertEquals(0, result.initialSubmissionPenalty.daysForCalculation.size)
        assertEquals(29, result.revisionsPenalty.daysForCalculation.size)
        assertEquals(listOf(16, 16, 29), listOf(result.revisionsPenalty.grantReport, result.revisionsPenalty.extraBudgetReport, result.revisionsPenalty.technicalReport).map { it.daysForCalculation.size })
        assertEquals(17, result.stageCompletionPenalty.overdueDays.size)
        assertEquals(date("26.09.2025"), result.stageCompletionPenalty.overdueDays.first())
        assertEquals(date("12.10.2025"), result.stageCompletionPenalty.overdueDays.last())
        assertEquals(BigDecimal("0.00"), result.initialSubmissionPenalty.penalty)
        assertEquals(BigDecimal("511471.40"), result.revisionsPenalty.totalPenalty)
        assertEquals(BigDecimal("299828.07"), result.stageCompletionPenalty.penalty)
        assertEquals(BigDecimal("811299.47"), result.totalPenalty)
    }

    @Test
    @Disabled("TODO: в Excel РУМО отсутствуют плановая и фактическая даты завершения работ; все даты обязательны.")
    fun `RUMO stage 1 from test-case-3 json`() {
        // РУМО расчет (неустойка типа 2).xlsx; сумму 145 000 000 ₽ пользователь указал отдельно.
        // Дат выполнения работ в файле нет. Вычисляемые плановые сроки из Excel не подменяют исходные даты.
        val request = readRequest("test-case-3.json")
        val result = service.calculate(request)
        val revisionDays = listOf(date("09.10.2024"), date("10.10.2024"), date("16.01.2025")) +
            date("12.02.2025").datesUntil(date("22.02.2025")).toList()

        assertEquals("Г-0042", request.contractNumber)
        assertEquals(BigDecimal("145000000"), request.stageAmount)
        assertEquals(listOf(7, 6, 9), listOf(request.grantReport.cases.size, request.extraBudgetReport.cases.size, request.technicalReport.cases.size))
        assertEquals(BigDecimal("14.00"), result.keyRatePercent)
        assertEquals(0, result.initialSubmissionPenalty.daysForCalculation.size)
        assertEquals(revisionDays, result.revisionsPenalty.daysForCalculation)
        assertEquals(listOf(13, 11, 1), listOf(result.revisionsPenalty.grantReport, result.revisionsPenalty.extraBudgetReport, result.revisionsPenalty.technicalReport).map { it.daysForCalculation.size })
        assertEquals(BigDecimal("0.00"), result.initialSubmissionPenalty.penalty)
        assertEquals(BigDecimal("879666.67"), result.revisionsPenalty.totalPenalty)
    }

    private fun readRequest(fileName: String): CalculatePenaltyRequestDto {
        val json = requireNotNull(javaClass.getResourceAsStream("/static/$fileName")).use { mapper.readTree(it) } as ObjectNode
        // Дата расчёта — параметр запуска: как на фронте, по умолчанию сегодня. В исходных документах её нет.
        if (!json.has("calculationDate")) json.put("calculationDate", LocalDate.now(clock).format(DATE_FORMATTER))
        return mapper.treeToValue(json, CalculatePenaltyRequestDto::class.java)
    }

    private fun date(value: String): LocalDate = LocalDate.parse(value, DATE_FORMATTER)
}
