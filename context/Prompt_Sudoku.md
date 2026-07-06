Create a Sudoku puzzle module for the Poincaré Android app.

The feature must generate interactive Sudoku puzzles where the user solves the puzzle by dragging number tiles into empty cells. Given numbers are fixed and cannot be moved. User-filled numbers can always be moved again after they are dropped. The puzzle is completed only when every row, column and box is correct.

Use the following fixed source files:

* `SudokuScreen.kt`
* `SudokuViewModel.kt`
* `SudokuGenerator.kt`
* `SudokuModels.kt`
* `SudokuDragDrop.kt`
* `SudokuTheme.kt`
* `sudoku_strings.xml`
* `sudoku_icons.xml`

## Core concept

Generate a Sudoku puzzle where:

* The user chooses a difficulty level: 1, 2 or 3 stars.
* The puzzle grid is shown on screen.
* Some values are fixed from the start.
* Empty cells are drop targets.
* Values appear as draggable square tiles below the puzzle.
* The user drags a value tile into an empty Sudoku cell.
* A placed value can be dragged again to another cell.
* A placed value can be removed and returned to the value bank.
* A placed value can be replaced by dropping another value on the same cell.
* Fixed given values cannot be moved.
* The timer is shown in the upper right corner.
* The puzzle is complete when all rows, columns and boxes are correct.
* A rocket takes off when the puzzle is complete.
* The user can keep generating new Sudoku puzzles for as long as they want to play.

## Difficulty selection

Before starting a puzzle, the user must choose:

* 1 star: easy
* 2 stars: medium
* 3 stars: hard

Difficulty affects:

* Grid size
* Number range
* Amount of fixed values
* Amount of empty cells
* Complexity of solving logic
* Whether notes are available
* Whether immediate feedback is strong or subtle

## Recommended grid sizes

Use the following default grid sizes:

* 1 star: 4 x 4 Sudoku
* 2 stars: 6 x 6 Sudoku
* 3 stars: 9 x 9 Sudoku

This makes Sudoku approachable for younger children while still allowing a full classic Sudoku at level 3.

### 1-star Sudoku

Use a 4 x 4 grid.

Numbers:

* 1, 2, 3, 4

Boxes:

* 2 x 2 boxes

Rules:

* Each row must contain 1, 2, 3 and 4 exactly once.
* Each column must contain 1, 2, 3 and 4 exactly once.
* Each 2 x 2 box must contain 1, 2, 3 and 4 exactly once.

### 2-star Sudoku

Use a 6 x 6 grid.

Numbers:

* 1, 2, 3, 4, 5, 6

Boxes:

* 2 x 3 boxes

Rules:

* Each row must contain 1 to 6 exactly once.
* Each column must contain 1 to 6 exactly once.
* Each 2 x 3 box must contain 1 to 6 exactly once.

### 3-star Sudoku

Use a classic 9 x 9 grid.

Numbers:

* 1, 2, 3, 4, 5, 6, 7, 8, 9

Boxes:

* 3 x 3 boxes

Rules:

* Each row must contain 1 to 9 exactly once.
* Each column must contain 1 to 9 exactly once.
* Each 3 x 3 box must contain 1 to 9 exactly once.

## Value bank

The draggable value bank appears below the puzzle.

Values must be shown as square number tiles.

Recommended behaviour:

* Show one square tile per possible value.
* For 4 x 4: show `1 2 3 4`.
* For 6 x 6: show `1 2 3 4 5 6`.
* For 9 x 9: show `1 2 3 4 5 6 7 8 9`.

The value bank acts like a reusable number palette.

Dragging a value from the bank does not remove the tile from the bank.

The user can drag the same number multiple times, because Sudoku requires each number multiple times across the full puzzle.

Optionally show a small remaining count on each value tile, for example:

* `1 × 3`
* `2 × 5`

The count shows how many times that number can still be placed based on the final grid size and already fixed/placed values.

## Drag and drop rules

The user must be able to:

* Drag a value from the value bank to an empty cell.
* Drag a user-filled value from one cell to another cell.
* Move a user-filled value after it has already been dropped.
* Replace a user-filled value by dropping another value onto the same cell.
* Remove a user-filled value from the grid.
* Return a user-filled value to the value bank.
* Tap a user-filled value and clear it.
* Tap a cell and then tap a number tile as an alternative to drag and drop.

Important:

* Fixed given values cannot be dragged.
* Fixed given values cannot be replaced.
* Fixed given values cannot be cleared.
* User-filled values must remain editable until puzzle completion.
* After completion, the puzzle can become locked and the user can start a new puzzle.

## Validation modes

Implement validation in a child-friendly learning mode by default.

### Default learning mode

When the user places or moves a number:

* Check whether the value matches the generated solution.
* If correct, colour the user-filled number dark green.
* If incorrect, colour the user-filled number dark red.
* Suggested correct colour: `#006400`.
* Suggested incorrect colour: `#8B0000`.

A value is correct only when:

* The value matches the solution for that exact cell.
* The row remains valid.
* The column remains valid.
* The box remains valid.

A value is incorrect when:

* The value does not match the solution for that cell.
* Or the value creates a duplicate in its row.
* Or the value creates a duplicate in its column.
* Or the value creates a duplicate in its box.

Do not colour the entire row, column or box.

Only colour the user-filled value font.

### Optional classic mode

Also prepare the code so a classic mode can be enabled later.

In classic mode:

* Do not reveal whether a value matches the hidden solution.
* Only show conflicts, such as duplicates in a row, column or box.
* Use dark red for conflicts.
* Use normal font colour for values that do not currently conflict.
* Completion still checks the full solution.

## Completion rules

The puzzle is complete only when:

* Every empty cell has been filled.
* Every row is valid.
* Every column is valid.
* Every box is valid.
* Every user-filled value is correct.
* No red values remain.

When complete:

* Stop the timer.
* Trigger a rocket take-off animation.
* Show a positive completion message.
* Lock the completed grid.
* Show a button to start a new Sudoku.
* Allow the user to keep generating new Sudoku puzzles indefinitely.

## Timer

Show the timer in the upper right corner.

The timer:

* Starts when the puzzle appears.
* Keeps counting while the puzzle is active.
* Stops when the puzzle is complete.
* Resets when a new puzzle starts.

Timer format:

* `00:00`
* `01:23`
* `12:45`
* `1:02:35` for long sessions

## UI layout

The screen should contain:

Top area:

* Sudoku title
* Difficulty selector with 1, 2 and 3 stars
* Timer in the upper right corner

Main area:

* Sudoku grid
* Fixed given numbers
* Empty drop cells
* User-filled values

Bottom area:

* Draggable value bank
* Square number tiles
* Optional remaining count per number
* New puzzle button after completion

The UI must support portrait orientation.

The grid must scale to available width.

Cells must remain square.

The value bank must remain easy to use on phones and tablets.

## Visual style

Use the existing Poincaré style:

* Space theme
* Clean
* Premium
* Not too childish
* Smooth animations
* Clear feedback
* Child-friendly touch targets

Suggested styling:

* Fixed given values: strong, calm, non-draggable style
* Empty cells: subtle drop target style
* User-filled values: square tile style
* Correct values: dark green font
* Incorrect values: dark red font
* Selected cell: soft glow
* Related row, column and box: very subtle highlight
* Box borders: slightly thicker than normal cell borders

Do not overuse colours.

The main correction feedback must be the font colour of user-filled numbers.

## Difficulty settings sheet

Use the following settings sheet as the central tuning reference.

These values must be easy to adjust later. Store them in a clear configuration object, for example `SudokuDifficultySettings`.

| Setting                        |   1 star |  2 stars |  3 stars | Notes                           |
| ------------------------------ | -------: | -------: | -------: | ------------------------------- |
| Grid size                      |    4 x 4 |    6 x 6 |    9 x 9 | Level 3 is classic Sudoku       |
| Box size                       |    2 x 2 |    2 x 3 |    3 x 3 | Rows x columns inside each box  |
| Values used                    |      1–4 |      1–6 |      1–9 | Value bank below puzzle         |
| Fixed values minimum           |        8 |       14 |       30 | More fixed values = easier      |
| Fixed values target            |       10 |       18 |       36 | Recommended default             |
| Fixed values maximum           |       12 |       22 |       42 | Avoid making puzzle too easy    |
| Empty cells minimum            |        4 |       14 |       39 | Empty cells become drop targets |
| Empty cells target             |        6 |       18 |       45 | Recommended default             |
| Empty cells maximum            |        8 |       22 |       51 | More empty cells = harder       |
| Minimum givens per row         |        1 |        1 |        2 | Prevent scary empty rows        |
| Minimum givens per column      |        1 |        1 |        2 | Helps children start            |
| Minimum givens per box         |        1 |        2 |        3 | Helps structure the puzzle      |
| Unique solution required       |      Yes |      Yes |      Yes | Mandatory                       |
| Symmetrical givens             | Optional | Optional | Optional | Nice visual polish              |
| Immediate correctness feedback |      Yes |      Yes |      Yes | Learning mode                   |
| Conflict highlighting          |      Yes |      Yes |      Yes | Duplicates become red           |
| Notes/pencil marks             |       No | Optional |      Yes | Can be added later              |
| Hints allowed                  |      Yes |      Yes |      Yes | Optional feature                |
| Max hints suggested            |        3 |        3 |        3 | Keep optional                   |
| Mistake limit                  |       No |       No |       No | Do not punish children          |
| Drag from value bank           |      Yes |      Yes |      Yes | Required                        |
| Drag placed values again       |      Yes |      Yes |      Yes | Required                        |
| Tap-to-place alternative       |      Yes |      Yes |      Yes | Accessibility                   |
| Timer visible                  |      Yes |      Yes |      Yes | Upper right corner              |
| Rocket animation               |      Yes |      Yes |      Yes | On completion                   |
| New puzzle after completion    |      Yes |      Yes |      Yes | Infinite replay                 |

## Recommended generator defaults

Use this configuration model.

```kotlin
data class SudokuDifficultySettings(
    val difficulty: SudokuDifficulty,

    val gridSize: Int,
    val boxRows: Int,
    val boxColumns: Int,
    val values: IntRange,

    val minFixedValues: Int,
    val targetFixedValues: Int,
    val maxFixedValues: Int,

    val minEmptyCells: Int,
    val targetEmptyCells: Int,
    val maxEmptyCells: Int,

    val minGivensPerRow: Int,
    val minGivensPerColumn: Int,
    val minGivensPerBox: Int,

    val requireUniqueSolution: Boolean = true,
    val useSymmetricalGivens: Boolean = false,

    val immediateCorrectnessFeedback: Boolean = true,
    val showConflictHighlighting: Boolean = true,
    val allowNotes: Boolean,
    val allowHints: Boolean = true,
    val maxHints: Int = 3,

    val allowDragFromValueBank: Boolean = true,
    val allowDraggingPlacedValues: Boolean = true,
    val allowTapToPlace: Boolean = true,

    val correctFontColor: String = "#006400",
    val incorrectFontColor: String = "#8B0000"
)
```

```kotlin
val levelOneSudokuSettings = SudokuDifficultySettings(
    difficulty = SudokuDifficulty.ONE_STAR,

    gridSize = 4,
    boxRows = 2,
    boxColumns = 2,
    values = 1..4,

    minFixedValues = 8,
    targetFixedValues = 10,
    maxFixedValues = 12,

    minEmptyCells = 4,
    targetEmptyCells = 6,
    maxEmptyCells = 8,

    minGivensPerRow = 1,
    minGivensPerColumn = 1,
    minGivensPerBox = 1,

    allowNotes = false
)

val levelTwoSudokuSettings = SudokuDifficultySettings(
    difficulty = SudokuDifficulty.TWO_STARS,

    gridSize = 6,
    boxRows = 2,
    boxColumns = 3,
    values = 1..6,

    minFixedValues = 14,
    targetFixedValues = 18,
    maxFixedValues = 22,

    minEmptyCells = 14,
    targetEmptyCells = 18,
    maxEmptyCells = 22,

    minGivensPerRow = 1,
    minGivensPerColumn = 1,
    minGivensPerBox = 2,

    allowNotes = true
)

val levelThreeSudokuSettings = SudokuDifficultySettings(
    difficulty = SudokuDifficulty.THREE_STARS,

    gridSize = 9,
    boxRows = 3,
    boxColumns = 3,
    values = 1..9,

    minFixedValues = 30,
    targetFixedValues = 36,
    maxFixedValues = 42,

    minEmptyCells = 39,
    targetEmptyCells = 45,
    maxEmptyCells = 51,

    minGivensPerRow = 2,
    minGivensPerColumn = 2,
    minGivensPerBox = 3,

    allowNotes = true
)
```

## Data model

Create clean data models in `SudokuModels.kt`.

At minimum include:

* `SudokuPuzzle`
* `SudokuCell`
* `SudokuDifficulty`
* `SudokuDifficultySettings`
* `SudokuGridSize`
* `SudokuBoxDefinition`
* `SudokuValueTile`
* `SudokuValidationState`
* `SudokuCellValidationState`
* `SudokuMove`
* `SudokuTimerState`

The puzzle model must store:

* Difficulty level
* Grid size
* Box size
* Full solved grid
* Visible fixed givens
* Empty cells
* Current user-filled values
* Value bank values
* Selected cell
* Validation states
* Timer state
* Completion state
* Optional hint count
* Optional notes

## Generator

Create the generator in `SudokuGenerator.kt`.

The generator must:

* Generate a fully solved valid Sudoku grid.
* Remove values according to difficulty settings.
* Guarantee a unique solution.
* Respect minimum givens per row.
* Respect minimum givens per column.
* Respect minimum givens per box.
* Generate a new puzzle each time the user starts another round.
* Support deterministic seed generation for testing.
* Use random generation during normal play.

Generation approach:

1. Generate a complete solved grid.
2. Shuffle rows, columns, boxes and values where valid.
3. Remove values according to difficulty.
4. After each removal, verify the puzzle still has a unique solution.
5. Stop when the target empty-cell count is reached.
6. Return the puzzle with fixed givens and hidden solution.

## Validation engine

The validation logic must be independent from the UI.

The validation engine must:

* Validate a placed value.
* Validate row rules.
* Validate column rules.
* Validate box rules.
* Detect duplicates.
* Compare to the solution in learning mode.
* Update the cell validation state.
* Detect puzzle completion.
* Never mark the puzzle complete until all cells are correct.

Cell states:

* `FIXED_GIVEN`
* `EMPTY`
* `USER_VALUE_DEFAULT`
* `USER_VALUE_CORRECT`
* `USER_VALUE_INCORRECT`
* `USER_VALUE_CONFLICT`
* `SELECTED`
* `RELATED_TO_SELECTED`

## Accessibility

Add content descriptions for:

* Difficulty stars
* Timer
* Fixed values
* Empty cells
* User-filled values
* Correct values
* Incorrect values
* Draggable number tiles
* Selected cell
* Rocket animation
* New puzzle button

Drag and drop must work with touch.

Tap-to-place should be supported as an accessible alternative.

## Translations

Add translatable strings in `sudoku_strings.xml`.

Include Dutch Belgian `nl-BE`, English US `en-US` and French Belgian `fr-BE`.

Required strings:

English US:

* Sudoku
* Choose difficulty
* 1 star
* 2 stars
* 3 stars
* Time
* Correct
* Try again
* Puzzle complete
* New puzzle
* Drag the numbers into the squares
* Move this number
* Clear cell
* Great job
* Rocket launch
* Hint
* Notes

Dutch Belgian:

* Sudoku
* Kies de moeilijkheid
* 1 ster
* 2 sterren
* 3 sterren
* Tijd
* Juist
* Probeer opnieuw
* Sudoku opgelost
* Nieuwe sudoku
* Sleep de cijfers naar de vakjes
* Verplaats dit cijfer
* Maak vakje leeg
* Goed gedaan
* Raket lanceren
* Tip
* Notities

French Belgian:

* Sudoku
* Choisis la difficulté
* 1 étoile
* 2 étoiles
* 3 étoiles
* Temps
* Correct
* Réessaie
* Sudoku terminé
* Nouveau sudoku
* Fais glisser les chiffres dans les cases
* Déplace ce chiffre
* Vider la case
* Bravo
* Lancement de la fusée
* Indice
* Notes

## Acceptance criteria

The implementation is correct when:

* The user can select 1, 2 or 3 stars.
* Level 1 generates a valid 4 x 4 Sudoku.
* Level 2 generates a valid 6 x 6 Sudoku.
* Level 3 generates a valid 9 x 9 Sudoku.
* The generated puzzle has a unique solution.
* Fixed values are visible and cannot be moved.
* Empty cells accept dragged values.
* Values appear as draggable square tiles below the puzzle.
* Values can be dragged from the value bank into the grid.
* Dropped values can be dragged again after placement.
* Dropped values can be moved to another cell.
* Dropped values can be cleared or replaced.
* Fixed values cannot be moved, cleared or replaced.
* Correct user-filled values turn dark green in learning mode.
* Incorrect or conflicting user-filled values turn dark red.
* The puzzle is completed only when all rows, columns and boxes are correct.
* The timer appears in the upper right corner.
* The timer stops when the puzzle is complete.
* A rocket take-off animation plays when the puzzle is complete.
* The user can start a new generated puzzle.
* The feature can generate puzzles repeatedly for as long as the user wants to play.
* All visible text uses translatable strings.
* The code is split over the fixed source files listed above.
