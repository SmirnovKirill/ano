package example.calculator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class CalculatorControllerTest {
    private val controller = CalculatorController()

    @ParameterizedTest
    @CsvSource("2,3,5", "-2,3,1", "0,0,0", "0.1,0.2,0.3", "9007199254740993,1,9007199254740994")
    fun `adds numbers exactly and preserves date`(a: String, b: String, expected: String) {
        val date = LocalDate.of(2024, 2, 29)
        val result = controller.calculate(a.toBigDecimal(), b.toBigDecimal(), date)

        assertEquals(expected, result.sum)
        assertEquals(date, result.date)
    }
}
