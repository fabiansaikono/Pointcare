from pathlib import Path

ROOT = Path.cwd()

def path(rel: str) -> Path:
    return ROOT / rel

def read(rel: str) -> str:
    p = path(rel)
    if not p.exists():
        raise FileNotFoundError(f"Missing file: {p}")
    return p.read_text(encoding="utf-8")

def write(rel: str, text: str):
    p = path(rel)
    p.write_text(text, encoding="utf-8")
    print(f"Updated: {p}")

def replace_once(text: str, old: str, new: str, label: str) -> tuple[str, bool]:
    if old in text:
        return text.replace(old, new, 1), True
    print(f"Skipped: {label} (pattern not found; maybe already applied)")
    return text, False

def replace_between(text: str, start_marker: str, end_marker: str, replacement: str, label: str) -> tuple[str, bool]:
    start = text.find(start_marker)
    if start < 0:
        print(f"Skipped: {label} start marker not found")
        return text, False
    end = text.find(end_marker, start)
    if end < 0:
        print(f"Skipped: {label} end marker not found")
        return text, False
    return text[:start] + replacement + text[end:], True

# 1) Sudoku landscape/portrait layout + equal bank columns.
def patch_sudoku():
    rel = "app/src/main/java/be/saikono/poincare/ui/puzzle/SudokuScreen.kt"
    s = read(rel)

    # Replace the landscape/portrait layout block used by the generated Sudoku screen.
    old = '''if (isLandscape) {
            Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) { grid(Modifier.fillMaxSize()) }
                bank(Modifier.widthIn(max = 156.dp).fillMaxHeight())
            }
        } else {
            grid(Modifier.fillMaxWidth().aspectRatio(1f))
            bank(Modifier.fillMaxWidth())
        }'''
    new = '''if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    grid(Modifier.fillMaxSize())
                }
                Box(
                    modifier = Modifier
                        .width(178.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    bank(Modifier.wrapContentWidth())
                }
            }
        } else {
            grid(Modifier.fillMaxWidth().aspectRatio(1f))
            Spacer(modifier = Modifier.height(18.dp))
            bank(Modifier.fillMaxWidth())
        }'''
    s, changed_layout = replace_once(s, old, new, "Sudoku main landscape/portrait block")
    if not changed_layout:
        old_previous_helper = 'if (isLandscape) {\n            Row(\n                modifier = Modifier\n                    .fillMaxWidth()\n                    .weight(1f),\n                horizontalArrangement = Arrangement.spacedBy(8.dp),\n                verticalAlignment = Alignment.CenterVertically\n            ) {\n                Box(\n                    modifier = Modifier\n                        .weight(1f)\n                        .fillMaxHeight(),\n                    contentAlignment = Alignment.Center\n                ) {\n                    grid(Modifier.fillMaxSize())\n                }\n                Box(\n                    modifier = Modifier\n                        .widthIn(max = 140.dp)\n                        .fillMaxHeight()\n                        .padding(end = 10.dp),\n                    contentAlignment = Alignment.CenterStart\n                ) {\n                    bank(Modifier.fillMaxHeight())\n                }\n            }\n        } else {\n            grid(Modifier.fillMaxWidth().aspectRatio(1f))\n            Spacer(modifier = Modifier.height(18.dp))\n            bank(Modifier.fillMaxWidth())\n        }'
        s, changed_layout = replace_once(s, old_previous_helper, new, "Sudoku layout from previous helper")

    start_marker = "@Composable\nprivate fun NumberBank("
    end_marker = "@Composable\nprivate fun DraggableSudokuValue("
    replacement = '''@Composable
private fun NumberBank(values: List<Int>, maxValue: Int, modifier: Modifier = Modifier, onValueDropped: (Int, Offset) -> Unit) {
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val columns = if (isLandscape) 3 else 5
    val rows = values.chunked(columns)
    val verticalGap = if (isLandscape) 8.dp else 10.dp

    Column(
        verticalArrangement = Arrangement.spacedBy(verticalGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.wrapContentWidth(align = Alignment.CenterHorizontally)
    ) {
        rows.forEach { rowValues ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowValues.forEach { value ->
                    DraggableSudokuValue(value = value, enabled = value <= maxValue, onDropped = onValueDropped)
                }
                repeat(columns - rowValues.size) {
                    Spacer(modifier = Modifier.size(50.dp))
                }
            }
        }
    }
}

'''
    s, changed_bank = replace_between(s, start_marker, end_marker, replacement, "Sudoku NumberBank")

    if changed_layout or changed_bank:
        write(rel, s)
        print("Applied: Sudoku layout and equal drag/drop columns")

# 2) Math crossword: move landscape grid/bank slightly higher.
def patch_math_crossword():
    rel = "app/src/main/java/be/saikono/poincare/ui/puzzle/MathCrosswordScreen.kt"
    s = read(rel)
    old = '''if (isLandscape) {
            Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) { grid(Modifier.fillMaxSize()) }
                bank(Modifier.widthIn(max = 164.dp).fillMaxHeight())
            }
        } else {'''
    new = '''if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 2.dp, bottom = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) { grid(Modifier.fillMaxSize()) }
                Box(
                    modifier = Modifier
                        .widthIn(max = 164.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) { bank(Modifier.fillMaxWidth()) }
            }
        } else {'''
    s, changed = replace_once(s, old, new, "Math crossword landscape placement")
    if changed:
        write(rel, s)
        print("Applied: Math crossword landscape moved higher")

# 3) PuzzleScreen: treat Constellations as a generated puzzle and hide generic bottom button
# for generated screens that provide their own controls.
def patch_puzzle_screen():
    rel = "app/src/main/java/be/saikono/poincare/ui/puzzle/PuzzleScreen.kt"
    s = read(rel)

    s, changed_set = replace_once(
        s,
        'val isGeneratedPuzzle = puzzle?.data?.get("subcategoryId") in setOf("sudoku", "math-crosswords", "calculator-puzzle")',
        'val isGeneratedPuzzle = puzzle?.data?.get("subcategoryId") in setOf("sudoku", "math-crosswords", "calculator-puzzle", "constellations")',
        "generated puzzle set includes constellations"
    )

    # Insert Constellations route if it is not already present.
    if 'subcategoryId == "constellations"' not in s:
        marker = '''                } else if (subcategoryId == "calculator-puzzle") {
                    CalculatorPuzzleGame(
                        mathGrade = puzzle.data["mathGrade"]?.toIntOrNull()
                            ?: puzzle.data["grade"]?.toIntOrNull()
                            ?: 1,
                        difficultyStars = puzzle.data["difficultyStars"]?.toIntOrNull() ?: 1,
                        exerciseLanguage = uiState.exerciseLanguage,
                        onAttemptSubmitted = onCalculatorPuzzleAttempt,
                        modifier = Modifier.weight(1f)
                    )
                } else {'''
        replacement = '''                } else if (subcategoryId == "calculator-puzzle") {
                    CalculatorPuzzleGame(
                        mathGrade = puzzle.data["mathGrade"]?.toIntOrNull()
                            ?: puzzle.data["grade"]?.toIntOrNull()
                            ?: 1,
                        difficultyStars = puzzle.data["difficultyStars"]?.toIntOrNull() ?: 1,
                        exerciseLanguage = uiState.exerciseLanguage,
                        onAttemptSubmitted = onCalculatorPuzzleAttempt,
                        modifier = Modifier.weight(1f)
                    )
                } else if (subcategoryId == "constellations") {
                    ConstellationsPuzzleGame(
                        grade = puzzle.data["mathGrade"]?.toIntOrNull()
                            ?: puzzle.data["grade"]?.toIntOrNull()
                            ?: 1,
                        difficultyStars = puzzle.data["difficultyStars"]?.toIntOrNull() ?: 1,
                        exerciseLanguage = uiState.exerciseLanguage,
                        onComplete = onGeneratedPuzzleComplete,
                        modifier = Modifier.weight(1f)
                    )
                } else {'''
        s, changed_route = replace_once(s, marker, replacement, "Constellations route")
    else:
        changed_route = False
        print("Already applied: Constellations route")

    old_if = 'if (uiState.isSolved || uiState.isFinished) {'
    new_if = 'if ((uiState.isSolved || uiState.isFinished) && subcategoryId !in setOf("calculator-puzzle", "constellations")) {'
    if new_if not in s:
        s, changed_button = replace_once(s, old_if, new_if, "hide generic next button for own-control puzzles")
    else:
        changed_button = False
        print("Already applied: generic next button hidden for own-control puzzles")

    if changed_set or changed_route or changed_button:
        write(rel, s)
        print("Applied: PuzzleScreen generated/constellations controls")

# 4) Puzzle menu: ensure only generated puzzle apps are listed under puzzle.
def patch_puzzle_menu():
    rel = "app/src/main/java/be/saikono/poincare/ui/home/SubCategorySelectionScreen.kt"
    s = read(rel)
    if 'SubCategory("constellations"' not in s:
        target = 'SubCategory("calculator-puzzle", "Calculatorpuzzel", R.drawable.ic_topic_mixed_practice, null),'
        insert = target + '\n                SubCategory("constellations", "Constellations", R.drawable.ic_theme_space, null),'
        s, changed_const = replace_once(s, target, insert, "add Constellations puzzle tile")
    else:
        changed_const = False
        print("Already applied: Constellations tile")

    # If an older puzzle list still includes generic generated/unassigned/non-folder puzzle categories,
    # keep only the puzzle screens that actually exist in ui/puzzle.
    old = '''SubCategory("sudoku", "Sudoku", R.drawable.ic_topic_mixed_practice, null),
                SubCategory("math-crosswords", "Rekenkruiswoord", R.drawable.ic_topic_mixed_practice, null),
                SubCategory("calculator-puzzle", "Calculatorpuzzel", R.drawable.ic_topic_mixed_practice, null),
                SubCategory("constellations", "Constellations", R.drawable.ic_theme_space, null),'''
    if old in s:
        changed_menu = False
        print("Already applied: generated-only puzzle menu")
    else:
        changed_menu = False

    if changed_const or changed_menu:
        write(rel, s)
        print("Applied: puzzle menu generated entries")

if __name__ == "__main__":
    patch_sudoku()
    patch_math_crossword()
    patch_puzzle_screen()
    patch_puzzle_menu()
    print("Done. Now run: .\\gradlew.bat :app:compileDebugKotlin")
