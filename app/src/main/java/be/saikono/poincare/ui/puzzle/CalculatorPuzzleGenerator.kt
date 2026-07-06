package be.saikono.poincare.ui.puzzle

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import kotlin.math.abs
import kotlin.random.Random

object CalculatorPuzzleGenerator {
    private const val EPSILON = 0.000001

    fun randomChallenge(
        mathGrade: Int,
        difficultyStars: Int = 1,
        random: Random = Random.Default
    ): CalculatorPuzzleChallenge {
        val grade = mathGrade.coerceIn(1, 6)
        val stars = difficultyStars.coerceIn(1, 3)
        val expression = randomExpression(grade, stars, random)
        val result = evaluateExpression(expression) ?: 0.0
        return challengeFrom(expression, result, grade, stars)
    }

    fun fromExpression(
        expression: String,
        mathGrade: Int,
        difficultyStars: Int = 1
    ): CalculatorPuzzleChallenge? {
        val normalized = normalizeExpression(expression)
        if (normalized.isBlank()) return null
        val result = evaluateExpression(normalized) ?: return null
        return challengeFrom(
            expression = prettyExpression(normalized),
            result = result,
            mathGrade = mathGrade.coerceIn(1, 6),
            difficultyStars = difficultyStars.coerceIn(1, 3)
        )
    }

    fun evaluateExpression(expression: String): Double? {
        val normalized = normalizeExpression(expression)
        if (normalized.isBlank()) return null
        val result = ExpressionParser(normalized).parse() ?: return null
        return if (result.isFinite()) normalizeResult(result) else null
    }

    fun canonicalExpression(expression: String): String = normalizeExpression(expression)

    fun resultsEquivalent(left: Double, right: Double): Boolean = abs(left - right) < EPSILON

    fun formatNumber(value: Double): String {
        if (!value.isFinite()) return ""
        val rounded = BigDecimal.valueOf(value)
            .setScale(6, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
        return if (rounded == "-0") "0" else rounded
    }

    private fun challengeFrom(
        expression: String,
        result: Double,
        mathGrade: Int,
        difficultyStars: Int
    ): CalculatorPuzzleChallenge {
        return CalculatorPuzzleChallenge(
            id = UUID.randomUUID().toString(),
            expression = prettyExpression(expression),
            result = normalizeResult(result),
            alternatives = sameResultAlternatives(normalizeResult(result), expression),
            mathGrade = mathGrade,
            difficultyStars = difficultyStars
        )
    }

    private fun randomExpression(grade: Int, stars: Int, random: Random): String {
        fun number(max: Int, min: Int = 1): Int = random.nextInt(min, max + 1)
        fun addition(max: Int): String {
            val a = number(max)
            val b = number(max)
            return "$a + $b"
        }
        fun subtraction(max: Int): String {
            val a = number(max)
            val b = number(a)
            return "$a - $b"
        }
        fun multiplication(max: Int): String {
            val a = number(max)
            val b = number(max.coerceAtMost(12))
            return "$a × $b"
        }
        fun division(max: Int): String {
            val divisor = number(max.coerceAtMost(12))
            val quotient = number(max)
            return "${divisor * quotient} ÷ $divisor"
        }

        return when (grade) {
            1 -> when (stars) {
                1 -> addition(10)
                2 -> if (random.nextBoolean()) addition(20) else subtraction(20)
                else -> "${number(10)} + ${number(10)} - ${number(5)}"
            }

            2 -> when (stars) {
                1 -> if (random.nextBoolean()) addition(50) else subtraction(50)
                2 -> if (random.nextBoolean()) addition(100) else subtraction(100)
                else -> "${number(50)} + ${number(50)} - ${number(25)}"
            }

            3 -> when (stars) {
                1 -> multiplication(10)
                2 -> if (random.nextBoolean()) multiplication(12) else division(12)
                else -> "${number(10)} × ${number(10)} + ${number(20)}"
            }

            4 -> when (stars) {
                1 -> if (random.nextBoolean()) multiplication(12) else division(20)
                2 -> "${number(12)} × ${number(12)} - ${number(40)}"
                else -> "${number(40)} + ${number(12)} × ${number(12)}"
            }

            5 -> when (stars) {
                1 -> "${number(100)} + ${number(12)} × ${number(12)}"
                2 -> "${number(20)} × ${number(20)} - ${number(100)}"
                else -> "${number(50)} × ${number(12)} ÷ ${listOf(2, 3, 4).random(random)} + ${number(100)}"
            }

            else -> when (stars) {
                1 -> "${number(150)} + ${number(20)} × ${number(12)}"
                2 -> "${number(50)} × ${number(20)} - ${number(250)}"
                else -> {
                    val divisor = listOf(2, 3, 4, 5).random(random)
                    val a = number(20) * divisor
                    "$a × ${number(20)} ÷ $divisor + ${number(200)}"
                }
            }
        }
    }

    private fun sameResultAlternatives(result: Double, originalExpression: String): List<String> {
        val cleanOriginal = prettyExpression(originalExpression)
        val resultText = formatNumber(result)
        val offset = abs(result).toInt().coerceAtLeast(5)
        val candidates = buildList {
            add("$resultText + 4 - 4")
            add("${formatNumber(result + offset)} - $offset")
            add("${formatNumber(result - 7)} + 7")
            add("$resultText × 1")
            add("$resultText + 10 ÷ 10 - 1")
            if (resultsEquivalent(result % 2.0, 0.0)) add("${formatNumber(result / 2.0)} × 2")
            if (resultsEquivalent(result % 3.0, 0.0)) add("${formatNumber(result / 3.0)} × 3")
        }
            .map(::prettyExpression)
            .filter { it != cleanOriginal }
            .distinct()

        return candidates.take(3).ifEmpty { listOf("$resultText + 0", "$resultText × 1", "$resultText - 0") }
    }

    private fun normalizeExpression(expression: String): String {
        return expression
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace(",", ".")
            .replace(" ", "")
            .trim()
    }

    private fun prettyExpression(expression: String): String {
        return formatExpressionNumbers(expression)
            .replace("*", " × ")
            .replace("/", " ÷ ")
            .replace("+", " + ")
            .replace("-", " - ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .replace("( - ", "(-")
    }

    private fun formatExpressionNumbers(expression: String): String {
        return Regex("\\d+(?:\\.\\d+)?").replace(expression) { match ->
            val value = match.value.toDoubleOrNull()
            if (value == null) match.value else formatNumber(value)
        }
    }

    private fun normalizeResult(value: Double): Double {
        return BigDecimal.valueOf(value)
            .setScale(6, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toDouble()
    }

    private class ExpressionParser(private val input: String) {
        private var index = 0

        fun parse(): Double? {
            val value = parseExpression() ?: return null
            skipWhitespace()
            return if (index == input.length) value else null
        }

        private fun parseExpression(): Double? {
            var value = parseTerm() ?: return null
            while (true) {
                skipWhitespace()
                value = when (peek()) {
                    '+' -> {
                        index++
                        value + (parseTerm() ?: return null)
                    }
                    '-' -> {
                        index++
                        value - (parseTerm() ?: return null)
                    }
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double? {
            var value = parseFactor() ?: return null
            while (true) {
                skipWhitespace()
                value = when (peek()) {
                    '*' -> {
                        index++
                        value * (parseFactor() ?: return null)
                    }
                    '/' -> {
                        index++
                        val rhs = parseFactor() ?: return null
                        if (abs(rhs) < EPSILON) return null
                        value / rhs
                    }
                    else -> return value
                }
            }
        }

        private fun parseFactor(): Double? {
            skipWhitespace()
            var sign = 1.0
            if (peek() == '-') {
                sign = -1.0
                index++
            }

            if (peek() == '(') {
                index++
                val value = parseExpression() ?: return null
                skipWhitespace()
                if (peek() != ')') return null
                index++
                return sign * value
            }

            val start = index
            var dotCount = 0
            while (true) {
                val next = peek()
                when {
                    next?.isDigit() == true -> index++
                    next == '.' && dotCount == 0 -> {
                        dotCount++
                        index++
                    }
                    else -> break
                }
            }

            if (start == index) return null
            val token = input.substring(start, index)
            if (token == ".") return null
            val number = token.toDoubleOrNull() ?: return null
            return sign * number
        }

        private fun skipWhitespace() {
            while (peek()?.isWhitespace() == true) index++
        }

        private fun peek(): Char? = input.getOrNull(index)
    }
}
