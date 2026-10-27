package example.calculator

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate

data class Calculation(val sum: String, val date: LocalDate)

@RestController
class CalculatorController {
    @GetMapping("/api/calculate")
    fun calculate(
        @RequestParam a: BigDecimal,
        @RequestParam b: BigDecimal,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
    ) = Calculation((a + b).toPlainString(), date)
}
