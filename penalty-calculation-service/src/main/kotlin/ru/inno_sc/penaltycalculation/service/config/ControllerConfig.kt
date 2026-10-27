package ru.inno_sc.penaltycalculation.service.config

import ru.inno_sc.penaltycalculation.service.controller.ReportExceptionHandler
import ru.inno_sc.penaltycalculation.service.controller.ReportController
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(ReportExceptionHandler::class, ReportController::class)
class ControllerConfig
