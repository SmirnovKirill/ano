package ru.inno_sc.penaltycalculation.service.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.convention.TestBean
import ru.inno_sc.penaltycalculation.api.penalty.request.CalculatePenaltyRequestDto
import ru.inno_sc.penaltycalculation.service.Application
import ru.inno_sc.penaltycalculation.service.config.CommonConfig.Companion.DATE_FORMATTER
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

@SpringBootTest(classes = [Application::class])
class RealCasesPenalyServiceTest {
    @TestBean(methodName = "fixedClock", enforceOverride = true)
    private lateinit var clock: Clock

    @Autowired
    private lateinit var service: PenaltyCalculationService

    @Autowired
    private lateinit var mapper: JsonMapper

    @Test
    fun `ROGA I KORYTA from test-case-1 json`() {
        val request = readRequest("test-case-1.json")
        val result = service.calculate(request)

        assertEquals("Г-0029", request.contractNumber)
        assertEquals(BigDecimal("50000000"), request.stageAmount)
        assertEquals(BigDecimal("14.00"), result.keyRatePercent)

        // 1. Первичная подача отчётности.
        assertEquals(dates("24.12.2025", "25.12.2025"), result.initialSubmissionPenalty.daysForCalculation)
        assertEquals(BigDecimal("46666.67"), result.initialSubmissionPenalty.penalty)

        // 2. Доработка: грант, внебюджет, технический отчёт, объединённые дни.
        assertEquals(listOf(date("12.03.2026"), date("18.03.2026")), result.revisionsPenalty.grantReport.daysForCalculation)
        assertEquals(listOf(date("12.03.2026"), date("18.03.2026"), date("19.03.2026")), result.revisionsPenalty.extraBudgetReport.daysForCalculation)
        val technicalOverdueDays = dates("30.01.2026", "16.02.2026") + date("12.03.2026")
        assertFalse(request.technicalReport.cases[1].remarksCorrected)
        assertEquals(technicalOverdueDays, result.revisionsPenalty.technicalReport.daysForCalculation)
        assertEquals(listOf(1, 2, 2, 3, 4, 5, 6), result.revisionsPenalty.technicalReport.cases.map { it.caseNumber })
        assertEquals(technicalOverdueDays + dates("18.03.2026", "19.03.2026"), result.revisionsPenalty.daysForCalculation)
        assertEquals(BigDecimal("490000.00"), result.revisionsPenalty.totalPenalty)

        // 3. Завершение работ: фактический день не включается.
        assertEquals(dates("23.12.2025", "09.01.2026"), result.stageCompletionPenalty.overdueDays)
        assertEquals(BigDecimal("420000.00"), result.stageCompletionPenalty.penalty)

        assertEquals(BigDecimal("956666.67"), result.totalPenalty)
    }

    @Test
    fun `INTRA stage 3 from test-case-2 json`() {
        val request = readRequest("test-case-2.json")
        val result = service.calculate(request)

        assertEquals("Г-0014", request.contractNumber)
        assertEquals(BigDecimal("37793453.05"), request.stageAmount)
        assertEquals(BigDecimal("16.50"), result.keyRatePercent)

        // 1. Первичная подача отчётности.
        assertEquals(emptyList<LocalDate>(), result.initialSubmissionPenalty.daysForCalculation)
        assertEquals(BigDecimal("0.00"), result.initialSubmissionPenalty.penalty)

        // 2. Доработка: грант, внебюджет, технический отчёт, объединённые дни.
        val grantOverdueDays = dates("28.10.2025", "30.10.2025") + dates("06.11.2025", "18.11.2025")
        assertEquals(grantOverdueDays, result.revisionsPenalty.grantReport.daysForCalculation)
        assertEquals(grantOverdueDays, result.revisionsPenalty.extraBudgetReport.daysForCalculation)
        assertEquals(dates("21.10.2025", "18.11.2025"), result.revisionsPenalty.technicalReport.daysForCalculation)
        assertEquals(dates("21.10.2025", "18.11.2025"), result.revisionsPenalty.daysForCalculation)
        assertEquals(BigDecimal("602805.58"), result.revisionsPenalty.totalPenalty)

        // 3. Завершение работ: фактический день не включается.
        assertEquals(date("25.09.2025"), request.technicalReport.plannedCompletionDate)
        assertEquals(date("13.10.2025"), request.technicalReport.actualCompletionDate)
        assertEquals(dates("26.09.2025", "12.10.2025"), result.stageCompletionPenalty.overdueDays)
        assertEquals(BigDecimal("353368.79"), result.stageCompletionPenalty.penalty)

        assertEquals(BigDecimal("956174.37"), result.totalPenalty)
    }

    @Test
    fun `RUMO stage 1 from test-case-3 json`() {
        val request = readRequest("test-case-3.json")
        val result = service.calculate(request)

        assertEquals("Г-0042", request.contractNumber)
        assertEquals(BigDecimal("145000000"), request.stageAmount)
        assertEquals(BigDecimal("21.00"), result.keyRatePercent)

        // 1. Первичная подача отчётности.
        assertEquals(emptyList<LocalDate>(), result.initialSubmissionPenalty.daysForCalculation)
        assertEquals(BigDecimal("0.00"), result.initialSubmissionPenalty.penalty)

        // 2. Доработка: грант, внебюджет, технический отчёт, объединённые дни.
        val grantOverdueDays = dates("09.10.2024", "10.10.2024") + date("16.01.2025") + dates("12.02.2025", "21.02.2025")
        assertEquals(grantOverdueDays, result.revisionsPenalty.grantReport.daysForCalculation)
        assertEquals(listOf(date("09.10.2024")) + dates("12.02.2025", "21.02.2025"), result.revisionsPenalty.extraBudgetReport.daysForCalculation)
        assertEquals(listOf(date("10.10.2024")), result.revisionsPenalty.technicalReport.daysForCalculation)
        assertEquals(grantOverdueDays, result.revisionsPenalty.daysForCalculation)
        assertEquals(BigDecimal("1319500.00"), result.revisionsPenalty.totalPenalty)

        // 3. Работы завершены в плановую дату.
        assertEquals(emptyList<LocalDate>(), result.stageCompletionPenalty.overdueDays)
        assertEquals(BigDecimal("0.00"), result.stageCompletionPenalty.penalty)

        assertEquals(BigDecimal("1319500.00"), result.totalPenalty)
    }

    private fun readRequest(fileName: String): CalculatePenaltyRequestDto {
        val json = requireNotNull(javaClass.getResourceAsStream("/static/$fileName")).use { mapper.readTree(it) } as ObjectNode
        return mapper.treeToValue(json, CalculatePenaltyRequestDto::class.java)
    }

    private fun date(value: String): LocalDate = LocalDate.parse(value, DATE_FORMATTER)

    private fun dates(from: String, through: String): List<LocalDate> = date(from).datesUntil(date(through).plusDays(1)).toList()

    companion object {
        @JvmStatic
        fun fixedClock(): Clock = Clock.fixed(LocalDate.parse("29.10.2026", DATE_FORMATTER).atTime(9, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    }
}
