package ru.inno_sc.penaltycalculation.service.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import ru.inno_sc.penaltycalculation.api.penalty.request.CalculatePenaltyRequestDto
import ru.inno_sc.penaltycalculation.api.penalty.request.ExtraBudgetReportDto
import ru.inno_sc.penaltycalculation.api.penalty.request.GrantReportDto
import ru.inno_sc.penaltycalculation.api.penalty.request.ReportCaseDto
import ru.inno_sc.penaltycalculation.api.penalty.request.TechnicalReportDto
import ru.inno_sc.penaltycalculation.service.AbstractTest
import ru.inno_sc.penaltycalculation.service.config.CommonConfig.Companion.DATE_FORMATTER
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.math.BigDecimal
import java.time.LocalDate

class PenaltyCalculationValidationTest : AbstractTest() {
    @Autowired
    private lateinit var service: PenaltyCalculationService

    @Autowired
    private lateinit var reportRevisionPlannedDateService: ReportRevisionPlannedDateService

    @Autowired
    private lateinit var mapper: JsonMapper

    private val calculationDate = LocalDate.parse("28.10.2026", DATE_FORMATTER)
    private val submitted = LocalDate.parse("05.01.2026", DATE_FORMATTER)
    private val revised = submitted.plusDays(1)
    private val case = ReportCaseDto(revised, revised)

    @Test
    fun `allows same-day events and early submission and completion`() {
        val result = service.calculate(request())

        assertEquals(calculationDate, result.calculationDate)
        assertEquals(calculationDate, LocalDate.now(clock))
        assertEquals(BigDecimal("0.00"), result.totalPenalty)
        assertEquals(listOf(1), result.revisionsPenalty.grantReport.cases.map { it.caseNumber })
        assertEquals(listOf(1), result.revisionsPenalty.extraBudgetReport.cases.map { it.caseNumber })
        assertEquals(listOf(1), result.revisionsPenalty.technicalReport.cases.map { it.caseNumber })
    }

    @Test
    fun `requires calculation date in the JSON request`() {
        for (explicitNull in listOf(false, true)) {
            val json = mapper.readTree(mapper.writeValueAsString(request())) as ObjectNode
            if (explicitNull) json.putNull("calculationDate") else json.remove("calculationDate")
            val error = assertThrows(Exception::class.java) {
                mapper.treeToValue(json, CalculatePenaltyRequestDto::class.java)
            }
            assertTrue(error.message.orEmpty().contains("calculationDate"), error.message)
        }
    }

    @Test
    fun `requires stage number in the JSON request`() {
        for (explicitNull in listOf(false, true)) {
            val json = mapper.readTree(mapper.writeValueAsString(request())) as ObjectNode
            if (explicitNull) json.putNull("stageNumber") else json.remove("stageNumber")
            val error = assertThrows(Exception::class.java) {
                mapper.treeToValue(json, CalculatePenaltyRequestDto::class.java)
            }
            assertTrue(error.message.orEmpty().contains("stageNumber"), error.message)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [-1, 0, 11, 2147483647])
    fun `rejects stage numbers outside one to ten`(stageNumber: Int) {
        rejects(request().copy(stageNumber = stageNumber), "Номер этапа")
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 10])
    fun `returns the supplied stage number without changing the calculation`(stageNumber: Int) {
        val result = service.calculate(request().copy(stageNumber = stageNumber))

        assertEquals(stageNumber, result.stageNumber)
        assertEquals(BigDecimal("0.00"), result.totalPenalty)
    }

    @Test
    fun `requires each completion date in the JSON request`() {
        for (field in listOf("plannedCompletionDate", "actualCompletionDate")) {
            val json = mapper.readTree(mapper.writeValueAsString(request())) as ObjectNode
            (json.path("technicalReport") as ObjectNode).remove(field)
            val error = assertThrows(Exception::class.java) {
                mapper.treeToValue(json, CalculatePenaltyRequestDto::class.java)
            }
            assertTrue(error.message.orEmpty().contains(field), error.message)
        }
    }

    @Test
    fun `uses the requested calculation date for the key rate and result`() {
        val request = request().copy(reportPlannedSubmissionDate = submitted.minusDays(1))

        val historical = service.calculate(request.copy(calculationDate = revised))
        val current = service.calculate(request)

        assertEquals(revised, historical.calculationDate)
        assertEquals(BigDecimal("16.00"), historical.keyRatePercent)
        assertEquals(BigDecimal("53.34"), historical.totalPenalty)
        assertEquals(calculationDate, current.calculationDate)
        assertEquals(BigDecimal("14.00"), current.keyRatePercent)
        assertEquals(BigDecimal("46.67"), current.totalPenalty)
    }

    @Test
    fun `rejects a future calculation date and report events after calculation date`() {
        rejects(request().copy(calculationDate = calculationDate.plusDays(1)), "Дата расчёта")
        rejects(request().copy(calculationDate = submitted), "будущ")
    }

    @ParameterizedTest
    @ValueSource(strings = ["grantReport", "extraBudgetReport", "technicalReport"])
    fun `requires a case for each report`(type: String) {
        rejects(withReport(type, cases = emptyList()), "хотя бы один кейс")
    }

    @ParameterizedTest
    @ValueSource(strings = ["grantReport", "extraBudgetReport", "technicalReport"])
    fun `rejects reversed report events`(type: String) {
        rejects(withReport(type, cases = listOf(case.copy(returnedForRevisionDate = submitted.minusDays(1)))), "возврат на доработку не может предшествовать")
        rejects(withReport(type, cases = listOf(case.copy(actualSubmissionDate = submitted))), "подача доработанного отчета не может предшествовать")
        rejects(withReport(type, cases = listOf(case, case.copy(returnedForRevisionDate = submitted))), "возврат на доработку не может предшествовать")
        rejects(withReport(type, approved = submitted), "согласование не может предшествовать")
    }

    @ParameterizedTest
    @ValueSource(strings = ["grantReport", "extraBudgetReport", "technicalReport"])
    fun `requires the final revision to correct remarks`(type: String) {
        rejects(withReport(type, cases = listOf(case.copy(remarksCorrected = false))), "не исправлены")
    }

    @Test
    fun `allows an unresolved revision followed by a correction on the same day`() {
        val result = service.calculate(withReport(
            "technicalReport",
            cases = listOf(case.copy(remarksCorrected = false), case),
        ))
        val technicalCases = result.revisionsPenalty.technicalReport.cases

        assertEquals(listOf(1, 1), technicalCases.map { it.caseNumber })
        assertEquals(technicalCases.first().plannedSubmissionDate, technicalCases.last().plannedSubmissionDate)
    }

    @Test
    fun `rejects future submission and completion dates`() {
        val request = request()
        val future = calculationDate.plusDays(1)
        rejects(request.copy(reportPlannedSubmissionDate = future), "будущ")
        rejects(request.copy(reportActualSubmissionDate = future), "будущ")
        rejects(request.copy(technicalReport = request.technicalReport.copy(plannedCompletionDate = future)), "будущ")
        rejects(request.copy(technicalReport = request.technicalReport.copy(actualCompletionDate = future)), "будущ")
    }

    @ParameterizedTest
    @ValueSource(strings = ["grantReport", "extraBudgetReport", "technicalReport"])
    fun `rejects future dates in each report`(type: String) {
        val future = calculationDate.plusDays(1)
        rejects(withReport(type, cases = listOf(case.copy(returnedForRevisionDate = future))), "будущ")
        rejects(withReport(type, cases = listOf(case.copy(actualSubmissionDate = future))), "будущ")
        rejects(withReport(type, approved = future), "будущ")
    }

    @Test
    fun `calculates completion independently of technical report approval`() {
        val request = request()
        val result = service.calculate(request.copy(technicalReport = request.technicalReport.copy(actualCompletionDate = revised.plusDays(1))))

        assertEquals(listOf(revised), result.stageCompletionPenalty.overdueDays)
        assertEquals(BigDecimal("46.67"), result.totalPenalty)
    }

    @Test
    fun `requires a contract and a nonnegative amount`() {
        rejects(request().copy(contractNumber = " "), "номер договора")
        rejects(request().copy(stageAmount = BigDecimal("-0.01")), "отрицательной")
    }

    @ParameterizedTest
    @ValueSource(strings = ["Г-0001", "Г-0009", "Г-0010", "Г-0079", "Г-0080"])
    fun `accepts contract numbers from 01 through 80`(number: String) {
        assertEquals(number, service.calculate(request().copy(contractNumber = number)).contractNumber)
    }

    @ParameterizedTest
    @ValueSource(strings = ["Г-0000", "Г-0081", "Г-0099", "Г-001", "Г-00001", "Г-0101", "G-0001", "Г0001", " Г-0001"])
    fun `rejects contract numbers outside the fixed format and range`(number: String) {
        rejects(request().copy(contractNumber = number), "номер договора")
    }

    @Test
    fun `validates the report revision planned-date request`() {
        reportRevisionPlannedDateService.getReportRevisionPlannedDate(calculationDate, 1)
        for (number in listOf(0, -1)) {
            invalid("положительным") {
                reportRevisionPlannedDateService.getReportRevisionPlannedDate(revised, number)
            }
        }
        invalid("будущ") {
            reportRevisionPlannedDateService.getReportRevisionPlannedDate(calculationDate.plusDays(1), 1)
        }
    }

    private fun rejects(request: CalculatePenaltyRequestDto, message: String) {
        invalid(message) { service.calculate(request) }
    }

    private fun invalid(message: String, action: () -> Unit) {
        val error = assertThrows(IllegalArgumentException::class.java) { action() }
        assertTrue(error.message.orEmpty().contains(message), error.message)
    }

    private fun request() = CalculatePenaltyRequestDto(
        stageNumber = 1,
        calculationDate = calculationDate,
        contractNumber = "Г-0001",
        stageAmount = BigDecimal("100000"),
        reportPlannedSubmissionDate = revised,
        reportActualSubmissionDate = submitted,
        grantReport = GrantReportDto(listOf(case), revised),
        extraBudgetReport = ExtraBudgetReportDto(listOf(case), revised),
        technicalReport = TechnicalReportDto(listOf(case), revised, submitted, submitted.minusDays(1)),
    )

    private fun withReport(
        type: String,
        cases: List<ReportCaseDto> = listOf(case),
        approved: LocalDate = revised,
    ): CalculatePenaltyRequestDto {
        val request = request()
        return when (type) {
            "grantReport" -> request.copy(grantReport = GrantReportDto(cases, approved))
            "extraBudgetReport" -> request.copy(extraBudgetReport = ExtraBudgetReportDto(cases, approved))
            "technicalReport" -> request.copy(technicalReport = request.technicalReport.copy(cases = cases, reportApprovalDate = approved))
            else -> error("Unknown report: $type")
        }
    }
}
