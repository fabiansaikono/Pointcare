package be.saikono.poincare.ui.math

import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.SkillLevel
import be.saikono.poincare.model.effectiveGeneratedDifficulty
import kotlin.math.abs
import kotlin.random.Random

enum class WrittenOperation(
    val subcategoryId: String,
    val symbol: String,
    val topicNl: String,
    val topicFr: String,
    val topicEn: String
) {
    ADDITION("addition", "+", "Optellen", "Addition", "Addition"),
    SUBTRACTION("subtraction", "−", "Aftrekken", "Soustraction", "Subtraction"),
    MULTIPLICATION("multiplication", "×", "Vermenigvuldigen", "Multiplication", "Multiplication"),
    DIVISION("division", ":", "Delen", "Division", "Division");

    companion object {
        fun fromSubcategoryId(id: String?): WrittenOperation? = entries.firstOrNull { it.subcategoryId == id }
        fun fromExercise(exercise: ExerciseContent): WrittenOperation? =
            exercise.data["writtenOperation"]?.let { stored -> entries.firstOrNull { it.name == stored } }
    }
}

data class WrittenCalculation(
    val missionId: String,
    val operation: WrittenOperation,
    val first: Int,
    val second: Int,
    val result: Int,
    val remainder: Int,
    val grade: Int,
    val difficultyStars: Int,
    val hasRemainderField: Boolean,
    val generationDifficulty: Int = difficultyStars
) {
    val columnCount: Int = when (operation) {
        WrittenOperation.DIVISION -> first.digitCount()
        else -> maxOf(first.digitCount(), second.digitCount(), result.digitCount())
    }.coerceAtLeast(1)

    val resultCellIds: List<String> = (0 until if (operation == WrittenOperation.DIVISION) result.digitCount() else columnCount).map { "result_$it" }
    val resultSignCellId: String = "result_sign"
    val remainderCellIds: List<String> = if (hasRemainderField) {
        (0 until maxOf(1, remainder.digitCount())).map { "remainder_$it" }
    } else {
        emptyList()
    }

    val supportCellIds: List<String> = when (operation) {
        WrittenOperation.ADDITION,
        WrittenOperation.SUBTRACTION,
        WrittenOperation.MULTIPLICATION -> (0 until columnCount).map { "support_$it" }
        WrittenOperation.DIVISION -> {
            val rows = divisionScratchRows
            buildList {
                repeat(rows) { row ->
                    repeat(columnCount) { col -> add("scratch_${row}_$col") }
                }
            }
        }
    }

    val partialProductRows: Int = if (operation == WrittenOperation.MULTIPLICATION && second.digitCount() > 1) {
        second.digitCount()
    } else {
        0
    }

    val partialProductCellIds: List<String> = buildList {
        repeat(partialProductRows) { row ->
            repeat(columnCount) { col -> add("partial_${row}_$col") }
        }
    }

    val divisionScratchRows: Int
        get() = when (generationDifficulty) {
            1 -> 2
            2 -> 3
            3 -> 4
            else -> 5
        }

    val editableCellIds: Set<String> = buildSet {
        addAll(resultCellIds)
        if (operation != WrittenOperation.DIVISION) add(resultSignCellId)
        addAll(remainderCellIds)
        addAll(supportCellIds)
        addAll(partialProductCellIds)
    }

    fun acceptsSymbol(cellId: String, symbol: String): Boolean = when {
        cellId == resultSignCellId && operation != WrittenOperation.DIVISION -> symbol == "−" || symbol == "-"
        symbol == "−" || symbol == "-" -> false
        else -> symbol.length == 1 && symbol[0].isDigit() && cellId in editableCellIds
    }

    fun maxLengthFor(cellId: String): Int = when {
        cellId == resultSignCellId -> 1
        cellId.startsWith("support_") && operation == WrittenOperation.SUBTRACTION -> 2
        cellId.startsWith("support_") && operation == WrittenOperation.MULTIPLICATION -> 2
        else -> 1
    }



    fun expectedHelperValue(cellId: String): String? {
        if (cellId.startsWith("partial_") && operation == WrittenOperation.MULTIPLICATION) {
            val parts = cellId.removePrefix("partial_").split("_")
            val row = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val col = parts.getOrNull(1)?.toIntOrNull() ?: return null
            val multiplierDigit = second.toString().reversed().getOrNull(row)?.digitToIntOrNull() ?: return null
            val value = first * multiplierDigit * tenPower(row)
            return fixedDigits(value).getOrNull(col).orEmpty()
        }
        if (!cellId.startsWith("support_")) return null
        val col = cellId.removePrefix("support_").toIntOrNull() ?: return null
        val fromRight = columnCount - 1 - col
        return when (operation) {
            WrittenOperation.ADDITION -> {
                var carry = 0
                var pos = 0
                while (pos < fromRight) {
                    val sum = digitAt(first, pos) + digitAt(second, pos) + carry
                    carry = if (sum >= 10) 1 else 0
                    pos++
                }
                if (fromRight == 0) "" else carry.takeIf { it > 0 }?.toString().orEmpty()
            }
            WrittenOperation.SUBTRACTION -> {
                var borrowed = 0
                var pos = 0
                while (pos <= fromRight) {
                    val top = digitAt(first, pos) - borrowed
                    val bottom = digitAt(second, pos)
                    if (pos == fromRight) {
                        return if (top < bottom) (digitAt(first, pos) + 10 - borrowed).toString()
                        else if (borrowed > 0) top.toString() else ""
                    }
                    borrowed = if (top < bottom) 1 else 0
                    pos++
                }
                ""
            }
            WrittenOperation.MULTIPLICATION -> {
                if (second.digitCount() != 1) return null
                var carry = 0
                var pos = 0
                while (pos < fromRight) {
                    val product = digitAt(first, pos) * second + carry
                    carry = product / 10
                    pos++
                }
                if (fromRight == 0) "" else carry.takeIf { it > 0 }?.toString().orEmpty()
            }
            WrittenOperation.DIVISION -> null
        }
    }

    fun isHelperEntryCorrect(cellId: String, entered: String): Boolean? {
        if (entered.isBlank()) return null
        val expected = expectedHelperValue(cellId) ?: return null
        return entered == expected
    }

    private fun digitAt(value: Int, fromRight: Int): Int =
        abs(value).toString().reversed().getOrNull(fromRight)?.digitToIntOrNull() ?: 0

    private fun tenPower(power: Int): Int {
        var result = 1
        repeat(power) { result *= 10 }
        return result
    }

    fun composeVisibleAnswer(entries: Map<String, String>): String {
        return if (operation == WrittenOperation.DIVISION) {
            val quotient = resultCellIds.joinToString("") { entries[it].orEmpty() }.trimLeadingZeroesKeepingOne()
            if (hasRemainderField) {
                val rest = remainderCellIds.joinToString("") { entries[it].orEmpty() }.trimLeadingZeroesKeepingOne()
                when {
                    quotient.isBlank() && rest.isBlank() -> ""
                    rest.isBlank() -> quotient
                    else -> "$quotient r $rest"
                }
            } else {
                quotient
            }
        } else {
            val digits = resultCellIds.joinToString("") { entries[it].orEmpty() }.trimLeadingZeroesKeepingOne()
            if (digits.isBlank()) "" else (if (result < 0) "−" else "") + digits
        }
    }

    fun isAnswerComplete(entries: Map<String, String>): Boolean {
        val expectedResult = expectedResultDigits()
        val resultComplete = expectedResult.indices.all { index ->
            expectedResult[index].isBlank() || entries[resultCellIds[index]].orEmpty().isNotBlank()
        }
        if (!resultComplete) return false
        if (hasRemainderField) {
            val expectedRemainder = expectedRemainderDigits()
            return expectedRemainder.indices.all { index ->
                entries[remainderCellIds[index]].orEmpty().isNotBlank()
            }
        }
        return true
    }

    fun isAnswerCorrect(entries: Map<String, String>): Boolean {
        val expectedResult = expectedResultDigits()
        val digitsCorrect = expectedResult.indices.all { index ->
            val entered = entries[resultCellIds[index]].orEmpty()
            val expected = expectedResult[index]
            if (expected.isBlank()) entered.isBlank() else entered == expected
        }
        if (!digitsCorrect) return false

        if (hasRemainderField) {
            val expectedRemainder = expectedRemainderDigits()
            return expectedRemainder.indices.all { index ->
                entries[remainderCellIds[index]].orEmpty() == expectedRemainder[index]
            }
        }
        return true
    }

    fun expectedResultDigits(): List<String> = abs(result).toString()
        .padStart(resultCellIds.size, ' ')
        .map { if (it == ' ') "" else it.toString() }

    fun expectedRemainderDigits(): List<String> = remainder.toString()
        .padStart(remainderCellIds.size, '0')
        .map(Char::toString)

    fun fixedDigits(value: Int): List<String> = abs(value).toString().padStart(columnCount, ' ')
        .map { if (it == ' ') "" else it.toString() }

    companion object {
        fun fromExercise(exercise: ExerciseContent): WrittenCalculation? {
            val operation = WrittenOperation.fromExercise(exercise) ?: return null
            return WrittenCalculation(
                missionId = exercise.id,
                operation = operation,
                first = exercise.data["first"]?.toIntOrNull() ?: return null,
                second = exercise.data["second"]?.toIntOrNull() ?: return null,
                result = exercise.data["result"]?.toIntOrNull() ?: return null,
                remainder = exercise.data["remainder"]?.toIntOrNull() ?: 0,
                grade = exercise.data["grade"]?.toIntOrNull()?.coerceIn(1, 6) ?: 1,
                difficultyStars = exercise.data["difficultyStars"]?.toIntOrNull()?.coerceIn(1, 3) ?: 1,
                hasRemainderField = exercise.data["hasRemainderField"].toBoolean(),
                generationDifficulty = exercise.data["generationDifficulty"]?.toIntOrNull()?.coerceIn(1, 4)
                    ?: exercise.data["difficultyStars"]?.toIntOrNull()?.coerceIn(1, 3)
                    ?: 1
            )
        }
    }
}

object WrittenCalculationGenerator {
    private val supportedSubcategories = WrittenOperation.entries.map { it.subcategoryId }.toSet()

    fun supports(subcategoryId: String?): Boolean = subcategoryId in supportedSubcategories

    private const val DIFFICULTY_BUCKET_COUNT = 300
    private const val DIFFICULTY_SEED_SALT = 0x5A17C9E3

    /**
     * Generates an open-ended mission sequence with a stable random difficulty draw
     * for every exercise. The draw is deterministic for the operation, grade and
     * mission number, so an unfinished mission does not change after restarting.
     *
     * Difficulty chances by completed mission number:
     * - 1..5: 100% one star
     * - 6..25: 50% one star, 50% two stars
     * - 26..99: one third per star level
     * - 100+: 20% one star, 30% two stars and 50% three stars
     *
     * Mission 25 belongs to the 6..25 block; the next block therefore starts at 26.
     */
    fun generate(
        operation: WrittenOperation,
        level: SkillLevel,
        startMissionNumber: Int,
        count: Int,
        hardModeEnabled: Boolean = false
    ): List<ExerciseContent> {
        val grade = level.toGrade() ?: return emptyList()
        if (count <= 0) return emptyList()
        val start = startMissionNumber.coerceAtLeast(1)
        return (start until start + count).map { missionNumber ->
            val missionSeed = stableSeed(operation, grade, missionNumber)
            val difficultyBucket = Random(missionSeed xor DIFFICULTY_SEED_SALT)
                .nextInt(DIFFICULTY_BUCKET_COUNT)
            val displayedStars = difficultyFor(missionNumber, difficultyBucket)
            createMission(
                operation = operation,
                level = level,
                grade = grade,
                displayedStars = displayedStars,
                generationDifficulty = effectiveGeneratedDifficulty(displayedStars, hardModeEnabled),
                index = missionNumber
            )
        }
    }

    internal fun difficultyFor(missionNumber: Int, chanceBucket: Int): Int {
        val index = missionNumber.coerceAtLeast(1)
        val bucket = Math.floorMod(chanceBucket, DIFFICULTY_BUCKET_COUNT)
        return when {
            index <= 5 -> 1
            index <= 25 -> if (bucket < 150) 1 else 2
            index <= 99 -> when {
                bucket < 100 -> 1
                bucket < 200 -> 2
                else -> 3
            }
            else -> when {
                bucket < 60 -> 1
                bucket < 150 -> 2
                else -> 3
            }
        }
    }

    private fun createMission(
        operation: WrittenOperation,
        level: SkillLevel,
        grade: Int,
        displayedStars: Int,
        generationDifficulty: Int,
        index: Int
    ): ExerciseContent {
        val random = Random(stableSeed(operation, grade, index))
        val numbers = when (operation) {
            WrittenOperation.ADDITION -> additionNumbers(grade, generationDifficulty, random)
            WrittenOperation.SUBTRACTION -> subtractionNumbers(grade, generationDifficulty, index, random)
            WrittenOperation.MULTIPLICATION -> multiplicationNumbers(grade, generationDifficulty, random)
            WrittenOperation.DIVISION -> divisionNumbers(grade, generationDifficulty, index, random)
        }
        val id = "generated-${operation.subcategoryId}-g$grade-${index.toString().padStart(6, '0')}"
        val prompt = promptFor(operation, numbers)
        val answer = when (operation) {
            WrittenOperation.DIVISION -> if (numbers.remainder > 0) "${numbers.result} r ${numbers.remainder}" else numbers.result.toString()
            else -> numbers.result.toString()
        }
        return ExerciseContent(
            id = id,
            domain = LearningDomain.MATH,
            skill = operation.subcategoryId,
            section = ExerciseSection.EXERCISES,
            level = level,
            inputMode = InputMode.DRAG_DROP_DIGITS,
            prompt = prompt,
            speechText = prompt,
            answer = answer,
            data = mapOf(
                "generatedWrittenCalculation" to "true",
                "writtenOperation" to operation.name,
                "first" to numbers.first.toString(),
                "second" to numbers.second.toString(),
                "result" to numbers.result.toString(),
                "remainder" to numbers.remainder.toString(),
                "hasRemainderField" to numbers.hasRemainderField.toString(),
                "difficultyStars" to displayedStars.toString(),
                "generationDifficulty" to generationDifficulty.toString(),
                "missionNumber" to index.toString(),
                "grade" to grade.toString(),
                "subcategoryId" to operation.subcategoryId,
                "progressKey" to id,
                "topic_nl-BE" to operation.topicNl,
                "topic_fr-BE" to operation.topicFr,
                "topic_en-US" to operation.topicEn
            )
        )
    }

    private data class GeneratedNumbers(
        val first: Int,
        val second: Int,
        val result: Int,
        val remainder: Int = 0,
        val hasRemainderField: Boolean = false
    )

    private fun additionNumbers(grade: Int, stars: Int, random: Random): GeneratedNumbers {
        val limit = gradeLimit(grade)
        val minimum = when (grade) {
            1 -> 0
            2 -> 5
            else -> maxOf(10, limit / 20)
        }
        repeat(500) {
            val a = random.nextInt(minimum, limit.coerceAtLeast(minimum + 2))
            val b = random.nextInt(minimum, (limit - a + 1).coerceAtLeast(minimum + 1))
            val carries = countCarries(a, b)
            val wanted = when (stars) {
                1 -> carries == 0
                2 -> carries >= 1
                3 -> carries >= if (grade >= 3) 2 else 1
                else -> carries >= when {
                    grade >= 5 -> 4
                    grade >= 3 -> 3
                    grade == 2 -> 2
                    else -> 1
                }
            }
            if (wanted && a + b <= limit) return GeneratedNumbers(a, b, a + b)
        }
        val a = limit / 2 + 1
        val b = (limit - a).coerceAtLeast(1)
        return GeneratedNumbers(a, b, a + b)
    }

    private fun subtractionNumbers(grade: Int, stars: Int, index: Int, random: Random): GeneratedNumbers {
        val limit = gradeLimit(grade)
        val allowNegative = grade >= 5 && stars >= 3 && index % (if (stars >= 4) 3 else 5) == 0
        repeat(500) {
            var a = random.nextInt(if (grade == 1) 1 else limit / 10, limit + 1)
            var b = random.nextInt(0, limit + 1)
            if (!allowNegative && b > a) {
                val swap = a
                a = b
                b = swap
            }
            if (allowNegative && a > b) {
                val swap = a
                a = b
                b = swap
            }
            val borrowBaseTop = maxOf(a, b)
            val borrowBaseBottom = minOf(a, b)
            val borrows = countBorrows(borrowBaseTop, borrowBaseBottom)
            val wanted = when (stars) {
                1 -> borrows == 0
                2 -> borrows >= 1
                3 -> borrows >= if (grade >= 3) 2 else 1
                else -> borrows >= when {
                    grade >= 5 -> 4
                    grade >= 3 -> 3
                    grade == 2 -> 2
                    else -> 1
                }
            }
            if (wanted && a != b) return GeneratedNumbers(a, b, a - b)
        }
        return if (allowNegative) {
            GeneratedNumbers(limit / 3, limit / 3 + 9, -9)
        } else {
            GeneratedNumbers(limit - 1, maxOf(1, limit / 3), limit - 1 - maxOf(1, limit / 3))
        }
    }

    private fun multiplicationNumbers(grade: Int, stars: Int, random: Random): GeneratedNumbers {
        return when (grade) {
            1 -> {
                val b = when (stars) {
                    1 -> random.nextInt(1, 4)
                    2 -> random.nextInt(2, 6)
                    3 -> random.nextInt(2, 11)
                    else -> random.nextInt(6, 13)
                }
                val a = random.nextInt(if (stars >= 4) 5 else 1, if (stars == 1) 6 else 13)
                GeneratedNumbers(a, b, a * b)
            }
            2 -> {
                val a = random.nextInt(if (stars >= 4) 6 else 1, if (stars >= 4) 21 else 11)
                val b = random.nextInt(if (stars >= 4) 6 else 1, if (stars >= 4) 13 else 11)
                GeneratedNumbers(a, b, a * b)
            }
            3 -> {
                val a = when (stars) {
                    1 -> random.nextInt(10, 100)
                    2, 3 -> random.nextInt(100, 1_000)
                    else -> random.nextInt(500, 5_000)
                }
                val b = if (stars >= 4) random.nextInt(10, 100) else random.nextInt(2, 10)
                GeneratedNumbers(a, b, a * b)
            }
            4 -> {
                val a = random.nextInt(if (stars == 1) 100 else 500, if (stars >= 4) 50_000 else 10_000)
                val b = when {
                    stars >= 4 -> random.nextInt(25, 250)
                    stars == 3 -> random.nextInt(10, 100)
                    else -> random.nextInt(2, 10)
                }
                GeneratedNumbers(a, b, a * b)
            }
            5 -> {
                val a = random.nextInt(1_000, if (stars >= 4) 250_000 else 100_000)
                val b = when {
                    stars >= 4 -> random.nextInt(100, 500)
                    stars == 1 -> random.nextInt(2, 10)
                    else -> random.nextInt(10, if (stars == 2) 50 else 100)
                }
                GeneratedNumbers(a, b, a * b)
            }
            else -> {
                val a = random.nextInt(10_000, if (stars >= 4) 1_500_000 else 1_000_000)
                val b = when {
                    stars >= 4 -> random.nextInt(250, 1_000)
                    stars == 1 -> random.nextInt(2, 10)
                    else -> random.nextInt(10, if (stars == 2) 100 else 1_000)
                }
                // Keep the generated result inside Int while retaining level-four complexity.
                val safeA = a.coerceAtMost(Int.MAX_VALUE / b)
                GeneratedNumbers(safeA, b, safeA * b)
            }
        }
    }

    private fun divisionNumbers(grade: Int, stars: Int, index: Int, random: Random): GeneratedNumbers {
        val divisor = when {
            grade <= 2 && stars < 4 -> random.nextInt(2, 11)
            grade <= 2 -> random.nextInt(6, 20)
            grade == 3 && stars < 4 -> random.nextInt(2, 10)
            grade == 3 -> random.nextInt(10, 50)
            grade == 4 && stars < 3 -> random.nextInt(2, 10)
            grade == 4 && stars < 4 -> random.nextInt(10, 26)
            grade == 4 -> random.nextInt(25, 100)
            grade == 5 && stars == 1 -> random.nextInt(2, 10)
            grade == 5 && stars < 4 -> random.nextInt(10, 100)
            grade == 5 -> random.nextInt(100, 500)
            grade == 6 && stars == 1 -> random.nextInt(2, 10)
            grade == 6 && stars == 2 -> random.nextInt(10, 100)
            stars >= 4 -> random.nextInt(250, 1_000)
            else -> random.nextInt(10, 500)
        }
        val quotientRange = when (grade) {
            1 -> if (stars >= 4) 5..20 else 1..5
            2 -> if (stars >= 4) 10..50 else 1..10
            3 -> if (stars == 1) 10..99 else if (stars >= 4) 100..999 else 20..250
            4 -> if (stars == 1) 20..250 else if (stars >= 4) 500..4_999 else 100..999
            5 -> if (stars == 1) 100..999 else if (stars >= 4) 2_000..19_999 else 500..9_999
            else -> if (stars == 1) 500..9_999 else if (stars >= 4) 10_000..199_999 else 1_000..99_999
        }
        val maxSafeQuotient = (Int.MAX_VALUE / divisor).coerceAtLeast(quotientRange.first)
        val rangeEnd = quotientRange.last.coerceAtMost(maxSafeQuotient)
        val quotient = random.nextInt(quotientRange.first, rangeEnd + 1)
        val allowRemainder = grade >= 3 && stars >= 3
        val remainder = if (allowRemainder) {
            if (stars >= 4 || index % 3 != 0) random.nextInt(1, divisor) else 0
        } else {
            0
        }
        val dividend = quotient * divisor + remainder
        return GeneratedNumbers(
            first = dividend,
            second = divisor,
            result = quotient,
            remainder = remainder,
            hasRemainderField = allowRemainder
        )
    }

    private fun promptFor(operation: WrittenOperation, n: GeneratedNumbers): LocalizedText {
        val supportNl = when (operation) {
            WrittenOperation.ADDITION -> "Gebruik de onthoudvakjes wanneer dat helpt."
            WrittenOperation.SUBTRACTION -> "Gebruik de leenvakjes wanneer dat helpt."
            WrittenOperation.MULTIPLICATION -> "Gebruik de onthoud- en deelproductvakjes wanneer dat helpt."
            WrittenOperation.DIVISION -> "Gebruik het staartdelingsrooster wanneer dat helpt."
        }
        val supportFr = when (operation) {
            WrittenOperation.ADDITION -> "Utilise les cases de retenue si cela t'aide."
            WrittenOperation.SUBTRACTION -> "Utilise les cases d'emprunt si cela t'aide."
            WrittenOperation.MULTIPLICATION -> "Utilise les retenues et les produits partiels si cela t'aide."
            WrittenOperation.DIVISION -> "Utilise la grille de division écrite si cela t'aide."
        }
        val supportEn = when (operation) {
            WrittenOperation.ADDITION -> "Use the carry cells when they help."
            WrittenOperation.SUBTRACTION -> "Use the borrowing cells when they help."
            WrittenOperation.MULTIPLICATION -> "Use the carry and partial-product cells when they help."
            WrittenOperation.DIVISION -> "Use the long-division grid when it helps."
        }
        val expressionNl = when (operation) {
            WrittenOperation.ADDITION -> "Bereken ${n.first} + ${n.second}."
            WrittenOperation.SUBTRACTION -> if (n.result < 0) "Bereken ${n.first} − ${n.second}. Bereken eerst het verschil en plaats een minteken voor het resultaat." else "Bereken ${n.first} − ${n.second}."
            WrittenOperation.MULTIPLICATION -> "Bereken ${n.first} × ${n.second}."
            WrittenOperation.DIVISION -> if (n.hasRemainderField) "Bereken ${n.first} : ${n.second} en vul ook de rest in." else "Bereken ${n.first} : ${n.second}."
        }
        val expressionFr = when (operation) {
            WrittenOperation.ADDITION -> "Calcule ${n.first} + ${n.second}."
            WrittenOperation.SUBTRACTION -> if (n.result < 0) "Calcule ${n.first} − ${n.second}. Calcule d’abord la différence et place un signe moins devant le résultat." else "Calcule ${n.first} − ${n.second}."
            WrittenOperation.MULTIPLICATION -> "Calcule ${n.first} × ${n.second}."
            WrittenOperation.DIVISION -> if (n.hasRemainderField) "Calcule ${n.first} : ${n.second} et indique aussi le reste." else "Calcule ${n.first} : ${n.second}."
        }
        val expressionEn = when (operation) {
            WrittenOperation.ADDITION -> "Calculate ${n.first} + ${n.second}."
            WrittenOperation.SUBTRACTION -> if (n.result < 0) "Calculate ${n.first} − ${n.second}. First calculate the difference and put a minus sign before the result." else "Calculate ${n.first} − ${n.second}."
            WrittenOperation.MULTIPLICATION -> "Calculate ${n.first} × ${n.second}."
            WrittenOperation.DIVISION -> if (n.hasRemainderField) "Calculate ${n.first} ÷ ${n.second} and enter the remainder too." else "Calculate ${n.first} ÷ ${n.second}."
        }
        return LocalizedText(
            nl = "$expressionNl $supportNl",
            fr = "$expressionFr $supportFr",
            en = "$expressionEn $supportEn"
        )
    }

    private fun gradeLimit(grade: Int): Int = when (grade) {
        1 -> 20
        2 -> 100
        3 -> 1_000
        4 -> 10_000
        5 -> 100_000
        else -> 1_000_000
    }

    private fun stableSeed(operation: WrittenOperation, grade: Int, index: Int): Int =
        31 * (31 * operation.name.hashCode() + grade) + index

    private fun countCarries(first: Int, second: Int): Int {
        var a = abs(first)
        var b = abs(second)
        var carry = 0
        var count = 0
        while (a > 0 || b > 0) {
            val sum = a % 10 + b % 10 + carry
            carry = if (sum >= 10) 1 else 0
            if (carry == 1) count += 1
            a /= 10
            b /= 10
        }
        return count
    }

    private fun countBorrows(top: Int, bottom: Int): Int {
        var a = abs(top)
        var b = abs(bottom)
        var borrowed = 0
        var count = 0
        while (a > 0 || b > 0) {
            val topDigit = a % 10 - borrowed
            val bottomDigit = b % 10
            if (topDigit < bottomDigit) {
                borrowed = 1
                count += 1
            } else {
                borrowed = 0
            }
            a /= 10
            b /= 10
        }
        return count
    }
}

private fun SkillLevel.toGrade(): Int? = if (this == SkillLevel.NOT_APPLICABLE) null else ordinal + 1
private fun Int.digitCount(): Int = abs(this).toString().length
private fun String.trimLeadingZeroesKeepingOne(): String {
    if (isBlank()) return ""
    val trimmed = trimStart('0')
    return if (trimmed.isEmpty()) "0" else trimmed
}
