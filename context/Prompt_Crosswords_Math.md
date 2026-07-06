Create a Math Crossword puzzle module for the Poincaré Android app.

The feature must generate interactive math crossword puzzles in a 17 x 17 logical grid. The user solves the puzzle by dragging number tiles into empty number cells. Operators and equal signs have fixed positions and cannot be moved. Some numbers are fixed from the start to give the user a baseline. The puzzle is completed only when every horizontal and vertical equation line is correct.

Use the following fixed source files:

* `MathCrosswordScreen.kt`
* `MathCrosswordViewModel.kt`
* `MathCrosswordGenerator.kt`
* `MathCrosswordModels.kt`
* `MathCrosswordDragDrop.kt`
* `MathCrosswordTheme.kt`
* `math_crossword_strings.xml`
* `math_crossword_icons.xml`

## Core concept

Generate a math crossword where:

* The logical grid is always 17 x 17.
* Empty unused cells are not rendered.
* Only cells that contain a number, operator, equal sign or drop target are visible.
* Operators and equal signs have fixed positions.
* Numbers are shown in square tiles.
* Missing numbers appear as draggable square tiles below the puzzle.
* The user drags values into empty square cells.
* There is no fixed starting position for the draggable values.
* A few fixed numbers are shown from the start as baseline clues.
* The puzzle is complete only when every visible value needed for every equation is correct.
* A rocket takes off when the puzzle is complete.
* A timer is shown in the upper right corner.

## Difficulty selection

Before starting a puzzle, the user must choose a difficulty level:

* 1 star: easy
* 2 stars: medium
* 3 stars: hard

The difficulty must influence:

* Number range
* Operation mix
* Puzzle density
* Amount of fixed baseline numbers
* Amount of draggable values
* Amount of crossing equations
* Equation length

## Number limits by difficulty

The generator must strictly respect these limits.

### Addition and subtraction

For additions and subtractions, the numbers used in the equation may go up to:

* 1 star: maximum 20
* 2 stars: maximum 100
* 3 stars: maximum 500

Examples:

* Level 1: `8 + 7 = 15`
* Level 2: `45 + 22 = 67`
* Level 3: `340 - 125 = 215`

Avoid negative results for 1-star puzzles.

Negative results should generally be avoided for children unless explicitly enabled later.

### Multiplication and division

For multiplications and divisions, the numbers used as exercise operands must be limited as follows:

* 1 star: only use 1, 2, 5 or 10
* 2 stars: use numbers up to 10
* 3 stars: use numbers up to 20

The result may be larger than the operand limit.

Examples:

* Level 1: `5 * 10 = 50`
* Level 2: `8 * 9 = 72`
* Level 3: `17 * 12 = 204`

For division:

* Only generate exact divisions.
* Never generate decimal answers.
* Never generate a division by zero.
* Keep the visible division understandable for children.

Examples:

* Level 1: `10 / 2 = 5`
* Level 2: `72 / 9 = 8`
* Level 3: `240 / 12 = 20`

## Operation rules by difficulty

1 star:

* Prefer addition and subtraction.
* Multiplication and division may appear only when very simple.
* If multiplication or division is used, operands must be only 1, 2, 5 or 10.
* Keep equations short.
* Provide many fixed baseline numbers.

2 stars:

* Use addition, subtraction and multiplication.
* Division may be introduced when exact and simple.
* Addition/subtraction values must stay at or below 100.
* Multiplication/division operands must stay at or below 10.
* Include more crossing equations.
* Provide a moderate amount of fixed baseline numbers.

3 stars:

* Use addition, subtraction, multiplication and division.
* Addition/subtraction values must stay at or below 500.
* Multiplication/division operands must stay at or below 20.
* Division must always produce whole-number answers.
* Include dense crossing equations.
* Provide only a few fixed baseline numbers.
* Avoid impossible or ambiguous puzzles.

## Grid rules

The puzzle frame is always 17 x 17 logical cells.

However:

* Do not render empty cells.
* Do not show borders for unused cells.
* Do not show placeholders for unused cells.
* Only puzzle cells are visible.

Visible cells can be:

* Fixed number cell
* Empty number drop cell
* Operator cell
* Equal sign cell

Operators and equal signs are fixed.

Supported signs:

* `+`
* `-`
* `*`
* `/`
* `=`

The signs must never be draggable.

## Equation rules

The generator must create valid horizontal and vertical equations.

Each equation line must contain:

* Number cells
* Operator cells
* Exactly one equal sign
* A valid mathematical result

Examples:

* `4 + 5 = 9`
* `12 - 7 = 5`
* `3 * 6 = 18`
* `24 / 4 = 6`
* `8 + 7 - 3 = 12`

The generator must support crossing equations.

A crossing cell is a number cell shared by both a horizontal and a vertical equation.

The value in a crossing cell must satisfy every equation line that uses it.

Never generate contradictions.

Never generate a puzzle where a cell has more than one valid solution.

Every draggable number tile must have one correct destination.

Duplicate values are allowed, but each duplicate must be represented as a separate draggable tile.

## Fixed baseline numbers

Each generated puzzle must include some fixed number cells.

Fixed numbers:

* Are already visible when the puzzle starts.
* Cannot be moved.
* Help the child start solving.
* Should remain visually distinguishable from draggable values.

Difficulty controls how many baseline numbers appear:

* 1 star: many fixed numbers
* 2 stars: some fixed numbers
* 3 stars: only a few fixed numbers

Operators and equal signs are always fixed.

## Drag and drop rules

All missing values appear as draggable square number tiles below the puzzle.

The user must drag the tiles into empty number cells.

Rules:

* A tile can be placed in any empty number cell.
* A tile can be moved again after placement.
* A tile can be removed and returned to the value bank.
* A tile can be replaced by dragging another tile onto the same cell.
* A tile can only be used once.
* Duplicate numbers must exist as separate draggable tiles.
* The value bank should shuffle tile order at the start.
* The value bank appears below the puzzle.
* The value bank should wrap nicely across rows on phones and tablets.

## Validation rules

Validation happens per complete equation line.

An equation line is complete when every number cell in that line contains a value.

When an equation line is complete and correct:

* Colour all user-filled values in that line dark green.
* Use dark green font.
* Suggested colour: `#006400`.

When an equation line is complete and wrong:

* Colour all user-filled values in that line dark red.
* Use dark red font.
* Suggested colour: `#8B0000`.

When a user-filled value belongs to multiple lines:

* If at least one completed connected line is wrong, show the value in dark red.
* If all completed connected lines are correct, show the value in dark green.
* If one connected line is incomplete and another completed line is correct, show dark green.
* If no connected line is complete yet, use the default font colour.

Do not colour the whole row or column.

Only colour the value text.

Fixed baseline numbers may stay in their fixed style, or they may also turn green when their equation is correct, but they must remain visually distinguishable from draggable values.

## Completion rules

The puzzle is complete only when:

* Every required number cell is filled.
* Every horizontal equation line is correct.
* Every vertical equation line is correct.
* Every user-filled value is green.

When complete:

* Stop the timer.
* Trigger a rocket take-off animation.
* Show a positive completion message.
* Offer a new puzzle button.
* Allow the user to keep generating new puzzles for as long as they want to play.

## Timer

Show a timer in the upper right corner.

The timer:

* Starts when the puzzle appears.
* Keeps counting while the puzzle is active.
* Stops when the puzzle is complete.
* Resets when a new puzzle starts.

Format:

* `00:00`
* `01:23`
* `12:45`
* `1:02:35` for very long sessions

## UI layout

The screen should contain:

Top area:

* Difficulty selector with 1, 2 and 3 stars
* Timer in the upper right corner

Main area:

* 17 x 17 logical puzzle grid
* Only visible puzzle cells are rendered
* Empty unused grid positions are not shown
* Puzzle scales to available width
* Cells remain square

Bottom area:

* Draggable value bank
* Square number tiles
* Tiles wrap across rows
* Dragging must be comfortable for children

## Visual style

Use the Poincaré style:

* Space theme
* Premium
* Clean
* Not too childish
* High contrast
* Smooth animations
* Child-friendly interactions

Suggested styling:

* Number cells: rounded square tile
* Fixed numbers: stronger background or small lock indicator
* Empty drop cells: subtle dashed outline or soft glow
* Operators and equal signs: fixed centered text
* Correct values: dark green font
* Incorrect values: dark red font

## Data model

Create clean data models in `MathCrosswordModels.kt`.

At minimum include:

* `MathCrosswordPuzzle`
* `MathCrosswordCell`
* `MathCrosswordCellType`
* `MathCrosswordEquationLine`
* `MathCrosswordDifficulty`
* `DraggableNumberTile`
* `PuzzleValidationState`
* `CellValidationState`

The model must store:

* Grid size: always 17
* Visible cells
* Cell coordinates
* Fixed operators
* Fixed equal signs
* Fixed baseline numbers
* Empty number cells
* Draggable values
* Horizontal equation lines
* Vertical equation lines
* Correct solution values
* Current user-filled values
* Difficulty level
* Timer state
* Completion state

## Generator

Create the generator in `MathCrosswordGenerator.kt`.

The generator must:

* Generate a valid 17 x 17 logical puzzle.
* Place horizontal and vertical equations.
* Fix operator and equal-sign positions.
* Generate mathematically correct equations.
* Ensure crossing values are valid.
* Choose which numbers are fixed and which are draggable.
* Shuffle draggable values.
* Guarantee solvability.
* Avoid ambiguous puzzles.
* Respect all number limits per difficulty.
* Avoid impossible divisions.
* Support deterministic seed generation for testing.
* Use random generation during normal play.

## Validation engine

The validation logic must be independent from the UI.

The validation engine must:

* Check each equation line once all cells in that line are filled.
* Evaluate mathematical correctness.
* Update cell validation states.
* Detect puzzle completion.
* Support cells that belong to multiple equation lines.
* Never mark the puzzle complete until all required values are correct.

## Accessibility

Add content descriptions for:

* Difficulty stars
* Timer
* Puzzle cells
* Fixed numbers
* Draggable number tiles
* Correct values
* Incorrect values
* Completion rocket animation

## Translations

Add translatable strings in `math_crossword_strings.xml`.

Include Dutch Belgian `nl-BE`, English US `en-US` and French Belgian `fr-BE`.

Required strings:

* Math crossword
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
* Rocket launch
* Great job
* Keep playing

## Acceptance criteria

The implementation is correct when:

* The user can select 1, 2 or 3 stars before playing.
* A 17 x 17 logical puzzle is generated.
* Empty unused cells are not rendered.
* Operators and equal signs are fixed.
* Some baseline numbers are fixed.
* Missing numbers appear as draggable square tiles below the puzzle.
* The user can drag values into empty puzzle cells.
* Addition and subtraction respect the difficulty number limits.
* Multiplication and division respect the difficulty operand limits.
* Division always produces whole-number answers.
* Correct completed equation lines turn the relevant value fonts dark green.
* Incorrect completed equation lines turn the relevant value fonts dark red.
* The puzzle is only completed when all values are correct and green.
* The timer appears in the upper right corner.
* The timer stops when the puzzle is complete.
* A rocket take-off animation plays when the puzzle is complete.
* The user can start a new generated puzzle.
* The feature can generate puzzles repeatedly for as long as the user wants to play.
* All visible text uses translatable strings.
* The code is split over the fixed source files listed above.
