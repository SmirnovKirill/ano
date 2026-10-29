package ru.inno_sc.penaltycalculation.service.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import ru.inno_sc.penaltycalculation.service.AbstractTest
import ru.inno_sc.penaltycalculation.service.config.CommonConfig.Companion.DATE_FORMATTER
import java.time.LocalDate

class ReportRevisionPlannedDateServiceTest : AbstractTest() {
    @Autowired
    private lateinit var service: ReportRevisionPlannedDateService

    @Test
    fun `first two cases get three workdays and next three get two`() {
        for (caseNumber in 1..5) {
            val expected = if (caseNumber <= 2) date("05.03.2026") else date("04.03.2026")
            assertEquals(expected, service.getReportRevisionPlannedDate(date("02.03.2026"), caseNumber), "Кейс $caseNumber")
        }
    }

    @Test
    fun `sixth and later cases are due on return day or nearest workday`() {
        for (caseNumber in listOf(6, 7, 26)) {
            for ((returned, expected) in mapOf(
                "20.01.2026" to "20.01.2026",
                "31.01.2026" to "02.02.2026",
                "01.02.2026" to "02.02.2026",
                "03.11.2025" to "05.11.2025",
                "01.11.2025" to "01.11.2025",
                "31.12.2025" to "12.01.2026",
            )) {
                assertEquals(date(expected), service.getReportRevisionPlannedDate(date(returned), caseNumber), "Кейс $caseNumber, возврат $returned")
            }
        }
    }

    private fun date(value: String): LocalDate = LocalDate.parse(value, DATE_FORMATTER)
}
