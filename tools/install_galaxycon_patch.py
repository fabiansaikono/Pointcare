#!/usr/bin/env python3
"""Patch the existing Poincare files so Galaxycon appears in the Puzzle category.

Run from the repository root after extracting this ZIP into the repository root:

    python tools/install_galaxycon_patch.py

The script is intentionally conservative: it patches only known snippets and exits
with a clear error if the dev branch source has changed too much.
"""
from pathlib import Path
import sys

ROOT = Path.cwd()

SUBCATEGORY = ROOT / "app/src/main/java/be/saikono/poincare/ui/home/SubCategorySelectionScreen.kt"
PUZZLE_SCREEN = ROOT / "app/src/main/java/be/saikono/poincare/ui/puzzle/PuzzleScreen.kt"
PUZZLE_VM = ROOT / "app/src/main/java/be/saikono/poincare/ui/puzzle/PuzzleViewModel.kt"


def read(path: Path) -> str:
    if not path.exists():
        raise SystemExit(f"Missing file: {path}")
    return path.read_text(encoding="utf-8")


def write(path: Path, text: str) -> None:
    path.write_text(text, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        if new in text:
            print(f"Already patched: {label}")
            return text
        raise SystemExit(f"Could not find patch target: {label}")
    return text.replace(old, new, 1)


def patch_subcategories() -> None:
    text = read(SUBCATEGORY)

    text = replace_once(
        text,
        'SubCategory("math-crosswords", "Rekenkruiswoord", R.drawable.ic_topic_math_crosswords, null), SubCategory("pattern_sequences",',
        'SubCategory("math-crosswords", "Rekenkruiswoord", R.drawable.ic_topic_math_crosswords, null), SubCategory("galaxycon", "Galaxycon", R.drawable.ic_theme_space, null), SubCategory("pattern_sequences",',
        "add Galaxycon tile to Puzzle category"
    )

    text = replace_once(
        text,
        '"math-crosswords" to "Math crossword", "pattern_sequences" to "Patterns",',
        '"math-crosswords" to "Math crossword", "galaxycon" to "Galaxycon", "pattern_sequences" to "Patterns",',
        "add English Galaxycon subcategory label"
    )

    text = replace_once(
        text,
        '"math-crosswords" to "Mots croises de calcul", "pattern_sequences" to "Motifs",',
        '"math-crosswords" to "Mots croises de calcul", "galaxycon" to "Galaxycon", "pattern_sequences" to "Motifs",',
        "add French Galaxycon subcategory label"
    )

    write(SUBCATEGORY, text)


def patch_puzzle_screen() -> None:
    text = read(PUZZLE_SCREEN)

    text = replace_once(
        text,
        'onGeneratedPuzzleComplete = viewModel::onGeneratedPuzzleComplete, onNextPuzzle = viewModel::nextPuzzle',
        'onGeneratedPuzzleComplete = viewModel::onGeneratedPuzzleComplete, onGalaxyconComplete = viewModel::onGalaxyconComplete, onNextPuzzle = viewModel::nextPuzzle',
        "wire Galaxycon completion callback"
    )

    text = replace_once(
        text,
        'onGeneratedPuzzleComplete: () -> Unit, onNextPuzzle: () -> Unit',
        'onGeneratedPuzzleComplete: () -> Unit, onGalaxyconComplete: (GalaxyconRunResult) -> Unit, onNextPuzzle: () -> Unit',
        "add Galaxycon callback parameter"
    )

    text = replace_once(
        text,
        'setOf("sudoku", "math-crosswords")',
        'setOf("sudoku", "math-crosswords", GALAXYCON_SUBCATEGORY_ID)',
        "mark Galaxycon as generated puzzle"
    )

    text = replace_once(
        text,
        '} else if (subcategoryId == "math-crosswords") { MathCrosswordGame( initialStars = puzzle.data["difficultyStars"]?.toIntOrNull() ?: 1, onComplete = onGeneratedPuzzleComplete, modifier = Modifier.weight(1f) ) } else {',
        '} else if (subcategoryId == "math-crosswords") { MathCrosswordGame( initialStars = puzzle.data["difficultyStars"]?.toIntOrNull() ?: 1, onComplete = onGeneratedPuzzleComplete, modifier = Modifier.weight(1f) ) } else if (subcategoryId == GALAXYCON_SUBCATEGORY_ID) { GalaxyconGame( language = uiState.exerciseLanguage, level = puzzle.level, initialStars = puzzle.data["difficultyStars"]?.toIntOrNull() ?: 1, onComplete = onGalaxyconComplete, modifier = Modifier.weight(1f) ) } else {',
        "render Galaxycon game"
    )

    text = replace_once(
        text,
        'onQuestionClick = {}, onGeneratedPuzzleComplete = {}, onNextPuzzle = {}',
        'onQuestionClick = {}, onGeneratedPuzzleComplete = {}, onGalaxyconComplete = {}, onNextPuzzle = {}',
        "update PuzzleScreen preview"
    )

    write(PUZZLE_SCREEN, text)


def patch_puzzle_view_model() -> None:
    text = read(PUZZLE_VM)

    galaxycon_exercise = (
        '"galaxycon" -> ExerciseContent( '
        'id = "generated-galaxycon-${level.name.lowercase()}-$stars", '
        'domain = LearningDomain.PUZZLE, '
        'skill = GALAXYCON_SUBCATEGORY_ID, '
        'section = ExerciseSection.PUZZLES, '
        'level = level, '
        'inputMode = InputMode.DRAG_DROP_LETTERS, '
        'prompt = LocalizedText(nl = "Galaxycon", en = "Galaxycon", fr = "Galaxycon"), '
        'data = mapOf( '
        '"mainCategory" to "logic_puzzles", '
        '"subcategoryId" to GALAXYCON_SUBCATEGORY_ID, '
        '"grade" to (level.toGrade()?.toString() ?: ""), '
        '"difficultyStars" to stars.toString(), '
        '"topic" to "Galaxycon", '
        '"topic_en-US" to "Galaxycon", '
        '"topic_fr-BE" to "Galaxycon" '
        '), '
        'answer = "planet" '
        ') '
    )

    text = replace_once(
        text,
        'answer = "completed" ) else -> null } } fun playCurrentQuestion',
        'answer = "completed" ) ' + galaxycon_exercise + 'else -> null } } fun playCurrentQuestion',
        "add generated Galaxycon exercise"
    )

    galaxycon_complete = (
        'fun onGalaxyconComplete(result: GalaxyconRunResult) { '
        'val state = _uiState.value '
        'val exercise = state.currentExercise ?: return '
        'val duration = if (result.elapsedMs > 0L) result.elapsedMs else System.currentTimeMillis() - startTime '
        '_uiState.update { it.copy(isSolved = result.planetReached, isWrong = !result.planetReached, showFeedback = true) } '
        'if (result.planetReached) audioService.playSuccess() else audioService.playFailure() '
        'saveGalaxyconProgress(exercise, duration, result) '
        '} '
    )

    text = replace_once(
        text,
        'private fun startPuzzle(index: Int)',
        galaxycon_complete + 'private fun startPuzzle(index: Int)',
        "add Galaxycon completion handler"
    )

    save_galaxycon = (
        'private fun saveGalaxyconProgress(exercise: ExerciseContent, durationMs: Long, result: GalaxyconRunResult) { '
        'val profileId = _uiState.value.currentProfileId ?: return '
        'viewModelScope.launch { '
        'val event = ProgressEvent( '
        'id = UUID.randomUUID().toString(), '
        'childProfileId = profileId, '
        'exerciseId = exercise.id, '
        'domain = exercise.domain, '
        'skill = exercise.skill, '
        'timestamp = System.currentTimeMillis(), '
        'answerCorrect = result.planetReached, '
        'responseTimeMs = durationMs, '
        'retryCount = if (result.planetReached) 0 else 1, '
        'rewardStampsEarned = (missionPointsFor(exercise, result.planetReached) + result.stickersEarned.size).coerceAtMost(50), '
        'metadata = mapOf( '
        '"answer" to if (result.planetReached) "planet_reached" else "run_finished", '
        '"progressKey" to (exercise.data["progressKey"] ?: exercise.id), '
        '"subcategoryId" to GALAXYCON_SUBCATEGORY_ID, '
        '"grade" to (exercise.data["grade"] ?: (exercise.level.toGrade()?.toString() ?: "")), '
        '"progressState" to if (result.planetReached) "completed" else "retry_wrong", '
        '"galaxyconScorePercent" to result.displayScore, '
        '"galaxyconPoints" to result.points.toString(), '
        '"galaxyconCollisions" to result.collisions.toString(), '
        '"galaxyconWordsCompleted" to "${result.wordsCompleted}/${result.totalWords}", '
        '"galaxyconStarsCollected" to result.starsCollected.toString(), '
        '"galaxyconStickers" to result.stickersEarned.joinToString("|"), '
        '"stickerBookSubgame" to GALAXYCON_SUBCATEGORY_ID '
        ') '
        ') '
        'progressRepository.addProgress(event) '
        'syncManager.scheduleSync() '
        '} '
        '} '
    )

    text = replace_once(
        text,
        'fun nextPuzzle() {',
        save_galaxycon + 'fun nextPuzzle() {',
        "save Galaxycon sticker/progress metadata"
    )

    write(PUZZLE_VM, text)


def main() -> None:
    patch_subcategories()
    patch_puzzle_screen()
    patch_puzzle_view_model()
    print("Galaxycon patch applied. Add/keep the two new Kotlin files under ui/puzzle, then build the app in Android Studio.")


if __name__ == "__main__":
    try:
        main()
    except SystemExit:
        raise
    except Exception as exc:
        print(f"Patch failed: {exc}", file=sys.stderr)
        sys.exit(1)
