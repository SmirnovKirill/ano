package ru.inno_sc.penaltycalculation.service.controller

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.inno_sc.penaltycalculation.api.Paths
import ru.inno_sc.penaltycalculation.api.reportrevision.ReportRevisionPlannedDateResponseDto
import ru.inno_sc.penaltycalculation.api.penalty.request.CalculatePenaltyRequestDto
import ru.inno_sc.penaltycalculation.api.penalty.response.PenaltyCalculationResultDto
import ru.inno_sc.penaltycalculation.service.converter.toDto
import ru.inno_sc.penaltycalculation.service.config.CommonConfig.Companion.DATE_PATTERN
import ru.inno_sc.penaltycalculation.service.service.ReportRevisionPlannedDateService
import ru.inno_sc.penaltycalculation.service.service.PenaltyCalculationService
import java.time.LocalDate

@RestController
class ReportController(
    private val reportRevisionPlannedDateService: ReportRevisionPlannedDateService,
    private val penaltyCalculationService: PenaltyCalculationService,
) {
    @PostMapping(Paths.CALCULATE_PENALTY)
    fun calculatePenalty(@RequestBody reports: CalculatePenaltyRequestDto): PenaltyCalculationResultDto = penaltyCalculationService.calculate(reports).toDto()

    @GetMapping(Paths.REPORT_REVISION_PLANNED_DATE)
    fun reportRevisionPlannedDate(
        @RequestParam("caseNumber") caseNumber: Int,
        @RequestParam("returnedForRevisionDate") @DateTimeFormat(pattern = DATE_PATTERN) returnedForRevisionDate: LocalDate,
    ): ReportRevisionPlannedDateResponseDto = ReportRevisionPlannedDateResponseDto(reportRevisionPlannedDateService.getReportRevisionPlannedDate(returnedForRevisionDate, caseNumber))
}
