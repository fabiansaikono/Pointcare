## Difficulty settings sheet

Use the following settings sheet as the central tuning reference for the math crossword generator.

These values must be easy to adjust later. Do not hardcode them deeply inside generation logic. Store them in a clear configuration object, for example `MathCrosswordDifficultySettings`.

| Setting                            |           1 star |           2 stars |         3 stars | Notes                                          |
| ---------------------------------- | ---------------: | ----------------: | --------------: | ---------------------------------------------- |
| Grid size                          |          17 x 17 |           17 x 17 |         17 x 17 | Always fixed                                   |
| Minimum horizontal equations       |                4 |                 6 |               8 | Can increase if layout allows                  |
| Maximum horizontal equations       |                6 |                 8 |              12 | Avoid overcrowding on small screens            |
| Minimum vertical equations         |                3 |                 5 |               7 | Must include crossings                         |
| Maximum vertical equations         |                5 |                 7 |              10 | More verticals = harder                        |
| Target total equations             |             7–10 |             11–15 |           15–22 | Horizontal + vertical                          |
| Minimum crossings                  |                3 |                 6 |              10 | A crossing is a shared number cell             |
| Target crossings                   |                5 |                 9 |              15 | More crossings make puzzle more crossword-like |
| Maximum crossings                  |                8 |                14 |              24 | Avoid over-constrained puzzles                 |
| Addition/subtraction max value     |               20 |               100 |             500 | Applies to values used in addition/subtraction |
| Addition allowed                   |              Yes |               Yes |             Yes | Always allowed                                 |
| Subtraction allowed                |              Yes |               Yes |             Yes | Avoid negative results by default              |
| Negative answers allowed           |               No |                No |              No | Keep off unless enabled later                  |
| Multiplication allowed             |      Rare/simple |               Yes |             Yes | Level 1 only simple operands                   |
| Division allowed                   |      Rare/simple | Simple exact only | Yes, exact only | Division must never produce decimals           |
| Multiplication/division operands   | 1, 2, 5, 10 only |              1–10 |            1–20 | Applies to operands, not necessarily result    |
| Maximum multiplication result      |              100 |               100 |             400 | Can be tuned                                   |
| Maximum division start value       |              100 |               100 |             400 | Dividend may be larger than operand limit      |
| Minimum equation length            |          5 cells |           5 cells |         5 cells | Example: `a + b = c`                           |
| Maximum equation length            |          5 cells |           7 cells |         9 cells | Example 7 cells: `a + b - c = d`               |
| Preferred equation length          |          5 cells |         5–7 cells |       7–9 cells | Longer means harder                            |
| Multi-step equations allowed       |               No |         Sometimes |             Yes | Example: `a + b - c = d`                       |
| Minimum fixed baseline numbers     |              45% |               30% |             18% | Percentage of number cells                     |
| Target fixed baseline numbers      |              55% |               38% |             25% | More fixed numbers = easier                    |
| Maximum fixed baseline numbers     |              65% |               45% |             32% | Do not make the puzzle too empty               |
| Minimum draggable numbers          |                8 |                14 |              22 | Depends on generated puzzle                    |
| Target draggable numbers           |               12 |                20 |              32 | Values appear below the puzzle                 |
| Maximum draggable numbers          |               18 |                28 |              45 | Too many tiles can overwhelm children          |
| Duplicate draggable values allowed |              Yes |               Yes |             Yes | But each duplicate must be a separate tile     |
| Maximum duplicate count per value  |                2 |                 3 |               4 | Avoid too many identical tiles                 |
| Show immediate line feedback       |              Yes |               Yes |             Yes | Validate when a line is complete               |
| Correct font colour                |        `#006400` |         `#006400` |       `#006400` | Dark green                                     |
| Incorrect font colour              |        `#8B0000` |         `#8B0000` |       `#8B0000` | Dark red                                       |
| Timer visible                      |              Yes |               Yes |             Yes | Upper right corner                             |
| Rocket animation on completion     |              Yes |               Yes |             Yes | Trigger only when everything is green          |
| New puzzle after completion        |              Yes |               Yes |             Yes | User can keep playing indefinitely             |

## Recommended generator defaults

Use these values as the first implementation defaults.

```kotlin
data class MathCrosswordDifficultySettings(
    val difficulty: MathCrosswordDifficulty,
    val gridSize: Int = 17,

    val minHorizontalEquations: Int,
    val maxHorizontalEquations: Int,
    val minVerticalEquations: Int,
    val maxVerticalEquations: Int,

    val minCrossings: Int,
    val targetCrossings: Int,
    val maxCrossings: Int,

    val addSubMaxValue: Int,
    val multiplicationDivisionOperands: List<Int>,
    val maxMultiplicationResult: Int,
    val maxDivisionStartValue: Int,

    val allowAddition: Boolean = true,
    val allowSubtraction: Boolean = true,
    val allowMultiplication: Boolean,
    val allowDivision: Boolean,
    val allowNegativeResults: Boolean = false,

    val minEquationLengthCells: Int,
    val maxEquationLengthCells: Int,
    val allowMultiStepEquations: Boolean,

    val minFixedNumberRatio: Float,
    val targetFixedNumberRatio: Float,
    val maxFixedNumberRatio: Float,

    val minDraggableNumbers: Int,
    val targetDraggableNumbers: Int,
    val maxDraggableNumbers: Int,

    val allowDuplicateValues: Boolean = true,
    val maxDuplicateCountPerValue: Int,

    val correctFontColor: String = "#006400",
    val incorrectFontColor: String = "#8B0000"
)
```

```kotlin
val levelOneSettings = MathCrosswordDifficultySettings(
    difficulty = MathCrosswordDifficulty.ONE_STAR,

    minHorizontalEquations = 4,
    maxHorizontalEquations = 6,
    minVerticalEquations = 3,
    maxVerticalEquations = 5,

    minCrossings = 3,
    targetCrossings = 5,
    maxCrossings = 8,

    addSubMaxValue = 20,
    multiplicationDivisionOperands = listOf(1, 2, 5, 10),
    maxMultiplicationResult = 100,
    maxDivisionStartValue = 100,

    allowMultiplication = true,
    allowDivision = true,

    minEquationLengthCells = 5,
    maxEquationLengthCells = 5,
    allowMultiStepEquations = false,

    minFixedNumberRatio = 0.45f,
    targetFixedNumberRatio = 0.55f,
    maxFixedNumberRatio = 0.65f,

    minDraggableNumbers = 8,
    targetDraggableNumbers = 12,
    maxDraggableNumbers = 18,

    maxDuplicateCountPerValue = 2
)

val levelTwoSettings = MathCrosswordDifficultySettings(
    difficulty = MathCrosswordDifficulty.TWO_STARS,

    minHorizontalEquations = 6,
    maxHorizontalEquations = 8,
    minVerticalEquations = 5,
    maxVerticalEquations = 7,

    minCrossings = 6,
    targetCrossings = 9,
    maxCrossings = 14,

    addSubMaxValue = 100,
    multiplicationDivisionOperands = (1..10).toList(),
    maxMultiplicationResult = 100,
    maxDivisionStartValue = 100,

    allowMultiplication = true,
    allowDivision = true,

    minEquationLengthCells = 5,
    maxEquationLengthCells = 7,
    allowMultiStepEquations = true,

    minFixedNumberRatio = 0.30f,
    targetFixedNumberRatio = 0.38f,
    maxFixedNumberRatio = 0.45f,

    minDraggableNumbers = 14,
    targetDraggableNumbers = 20,
    maxDraggableNumbers = 28,

    maxDuplicateCountPerValue = 3
)

val levelThreeSettings = MathCrosswordDifficultySettings(
    difficulty = MathCrosswordDifficulty.THREE_STARS,

    minHorizontalEquations = 8,
    maxHorizontalEquations = 12,
    minVerticalEquations = 7,
    maxVerticalEquations = 10,

    minCrossings = 10,
    targetCrossings = 15,
    maxCrossings = 24,

    addSubMaxValue = 500,
    multiplicationDivisionOperands = (1..20).toList(),
    maxMultiplicationResult = 400,
    maxDivisionStartValue = 400,

    allowMultiplication = true,
    allowDivision = true,

    minEquationLengthCells = 5,
    maxEquationLengthCells = 9,
    allowMultiStepEquations = true,

    minFixedNumberRatio = 0.18f,
    targetFixedNumberRatio = 0.25f,
    maxFixedNumberRatio = 0.32f,

    minDraggableNumbers = 22,
    targetDraggableNumbers = 32,
    maxDraggableNumbers = 45,

    maxDuplicateCountPerValue = 4
)
```

## Fine-tuning guidance

Use these rules when adjusting the difficulty later:

* To make a level easier, increase the fixed number ratio.
* To make a level harder, increase crossings.
* To make a level faster to solve, reduce draggable numbers.
* To make a level more crossword-like, increase vertical equations and crossings.
* To make a level less frustrating, reduce duplicate draggable values.
* To make a level more educational, increase equation variety.
* To make a level calmer for younger children, keep equations short and use more fixed baseline numbers.
* To make level 3 more challenging, use more multi-step equations instead of only increasing number size.

Do not increase all difficulty dimensions at once. Tune one dimension at a time:

1. Number range
2. Number of draggable values
3. Number of fixed baseline values
4. Number of crossings
5. Equation length
6. Operation mix
7. Duplicate value frequency

The generator should expose these settings in one place so the Poincaré team can quickly adjust the puzzle behaviour without rewriting the generator.
