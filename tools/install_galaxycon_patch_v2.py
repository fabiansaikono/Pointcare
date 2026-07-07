#!/usr/bin/env python3
"""More flexible Galaxycon installer for the Poincare Android project.

Run from the repository root:
    py tools/install_galaxycon_patch_v2.py

This version does not depend on exact one-line snippets. It uses flexible
anchors and leaves .bak_galaxycon backup files next to edited Kotlin files.
"""
from pathlib import Path
import re
import shutil
import sys

ROOT = Path.cwd()
SCRIPT = Path(__file__).resolve()
PATCH_ROOT = SCRIPT.parents[1]

SUBCATEGORY = ROOT / "app/src/main/java/be/saikono/poincare/ui/home/SubCategorySelectionScreen.kt"
PUZZLE_SCREEN = ROOT / "app/src/main/java/be/saikono/poincare/ui/puzzle/PuzzleScreen.kt"
PUZZLE_VM = ROOT / "app/src/main/java/be/saikono/poincare/ui/puzzle/PuzzleViewModel.kt"
PUZZLE_DIR = ROOT / "app/src/main/java/be/saikono/poincare/ui/puzzle"

NEW_FILES = [
    "GalaxyconModels.kt",
    "GalaxyconScreen.kt",
]


def fail(message: str) -> None:
    print(f"Galaxycon patch failed: {message}", file=sys.stderr)
    sys.exit(1)


def read(path: Path) -> str:
    if not path.exists():
        fail(f"Missing file: {path}")
    return path.read_text(encoding="utf-8")


def backup(path: Path) -> None:
    bak = path.with_suffix(path.suffix + ".bak_galaxycon")
    if not bak.exists():
        shutil.copy2(path, bak)


def write_if_changed(path: Path, original: str, updated: str) -> None:
    if updated != original:
        backup(path)
        path.write_text(updated, encoding="utf-8")
        print(f"Patched: {path.relative_to(ROOT)}")
    else:
        print(f"Already OK: {path.relative_to(ROOT)}")


def copy_new_files() -> None:
    PUZZLE_DIR.mkdir(parents=True, exist_ok=True)
    for name in NEW_FILES:
        target = PUZZLE_DIR / name
        if target.exists():
            print(f"Already OK: {target.relative_to(ROOT)}")
            continue
        candidates = [
            PATCH_ROOT / "app/src/main/java/be/saikono/poincare/ui/puzzle" / name,
            ROOT / "galaxycon_patch/app/src/main/java/be/saikono/poincare/ui/puzzle" / name,
            ROOT / "galaxycon_patch_v2/app/src/main/java/be/saikono/poincare/ui/puzzle" / name,
        ]
        source = next((p for p in candidates if p.exists()), None)
        if source is None:
            fail(f"Could not find source for {name}. Extract the full ZIP into the repo root first.")
        shutil.copy2(source, target)
        print(f"Copied: {target.relative_to(ROOT)}")


def patch_subcategories() -> None:
    original = read(SUBCATEGORY)
    text = original

    if 'SubCategory("galaxycon"' not in text:
        # Insert the tile immediately after Math Crosswords if that tile exists.
        pattern = re.compile(
            r'(SubCategory\(\s*"math-crosswords"\s*,\s*"[^"]+"\s*,\s*R\.drawable\.[A-Za-z0-9_]+\s*,\s*null\s*\)\s*,)',
            re.S,
        )
        text, count = pattern.subn(
            r'\1 SubCategory("galaxycon", "Galaxycon", R.drawable.ic_theme_space, null),',
            text,
            count=1,
        )
        if count == 0:
            # Fallback: put it before pattern_sequences in the Puzzle list.
            text, count = re.subn(
                r'(SubCategory\(\s*"pattern_sequences")',
                r'SubCategory("galaxycon", "Galaxycon", R.drawable.ic_theme_space, null), \1',
                text,
                count=1,
                flags=re.S,
            )
        if count == 0:
            fail('Could not insert Galaxycon in getSubCategoriesForDomain. Search for LearningDomain.PUZZLE.name and add SubCategory("galaxycon", "Galaxycon", R.drawable.ic_theme_space, null).')

    # Add localized label entries after math-crosswords entries in EN and FR maps.
    if '"galaxycon" to "Galaxycon"' not in text:
        text, count = re.subn(
            r'("math-crosswords"\s+to\s+"[^"]+"\s*,)',
            r'\1 "galaxycon" to "Galaxycon",',
            text,
            count=2,
        )
        if count == 0:
            print("Warning: could not add localized Galaxycon map labels automatically. The Dutch fallback tile name will still show Galaxycon.")

    write_if_changed(SUBCATEGORY, original, text)


def patch_puzzle_screen() -> None:
    original = read(PUZZLE_SCREEN)
    text = original

    if 'onGalaxyconComplete = viewModel::onGalaxyconComplete' not in text:
        text, count = re.subn(
            r'(onGeneratedPuzzleComplete\s*=\s*viewModel::onGeneratedPuzzleComplete\s*,)',
            r'\1 onGalaxyconComplete = viewModel::onGalaxyconComplete,',
            text,
            count=1,
            flags=re.S,
        )
        if count == 0:
            fail('Could not wire Galaxycon completion callback in PuzzleScreen.')

    if 'onGalaxyconComplete: (GalaxyconRunResult) -> Unit' not in text:
        text, count = re.subn(
            r'(onGeneratedPuzzleComplete\s*:\s*\(\)\s*->\s*Unit\s*,)',
            r'\1 onGalaxyconComplete: (GalaxyconRunResult) -> Unit,',
            text,
            count=1,
            flags=re.S,
        )
        if count == 0:
            fail('Could not add Galaxycon callback parameter to PuzzleContent.')

    # Include Galaxycon in the generated-puzzle set.
    def set_repl(match: re.Match) -> str:
        inside = match.group(1)
        if 'galaxycon' in inside or 'GALAXYCON_SUBCATEGORY_ID' in inside:
            return match.group(0)
        return f'setOf({inside}, GALAXYCON_SUBCATEGORY_ID)'

    text, set_count = re.subn(
        r'setOf\(([^)]*?"sudoku"[^)]*?"math-crosswords"[^)]*?)\)',
        set_repl,
        text,
        count=1,
        flags=re.S,
    )
    if set_count == 0 and 'GALAXYCON_SUBCATEGORY_ID' not in text:
        fail('Could not add Galaxycon to generated puzzle set.')

    if 'GalaxyconGame(' not in text:
        math_branch = text.find('subcategoryId == "math-crosswords"')
        if math_branch < 0:
            fail('Could not find Math Crosswords branch in PuzzleScreen.')
        else_pos = text.find('} else {', math_branch)
        if else_pos < 0:
            fail('Could not find insertion point after Math Crosswords branch.')
        galaxycon_branch = (
            '} else if (subcategoryId == GALAXYCON_SUBCATEGORY_ID) { '
            'GalaxyconGame( '
            'language = uiState.exerciseLanguage, '
            'level = puzzle.level, '
            'initialStars = puzzle.data["difficultyStars"]?.toIntOrNull() ?: 1, '
            'onComplete = onGalaxyconComplete, '
            'modifier = Modifier.weight(1f) '
            ') '
            '} else {'
        )
        text = text[:else_pos] + galaxycon_branch + text[else_pos + len('} else {'):]

    # Update preview / other direct PuzzleContent calls if present.
    text = re.sub(
        r'(onGeneratedPuzzleComplete\s*=\s*\{\}\s*,)\s*(onNextPuzzle\s*=)',
        r'\1 onGalaxyconComplete = {}, \2',
        text,
        flags=re.S,
    )

    write_if_changed(PUZZLE_SCREEN, original, text)


def patch_puzzle_view_model() -> None:
    original = read(PUZZLE_VM)
    text = original

    if 'generated-galaxycon' not in text:
        exercise_branch = (
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
        gen_idx = text.find('private fun generatedPuzzleExercise')
        if gen_idx < 0:
            fail('Could not find generatedPuzzleExercise in PuzzleViewModel.')
        else_idx = text.find('else -> null', gen_idx)
        if else_idx < 0:
            fail('Could not find else -> null in generatedPuzzleExercise.')
        text = text[:else_idx] + exercise_branch + text[else_idx:]

    if 'fun onGalaxyconComplete(result: GalaxyconRunResult)' not in text:
        completion_handler = (
            'fun onGalaxyconComplete(result: GalaxyconRunResult) { '
            'val state = _uiState.value '
            'val exercise = state.currentExercise ?: return '
            'val duration = if (result.elapsedMs > 0L) result.elapsedMs else System.currentTimeMillis() - startTime '
            '_uiState.update { it.copy(isSolved = result.planetReached, isWrong = !result.planetReached, showFeedback = true) } '
            'if (result.planetReached) audioService.playSuccess() else audioService.playFailure() '
            'saveGalaxyconProgress(exercise, duration, result) '
            '} '
        )
        start_idx = text.find('private fun startPuzzle')
        if start_idx < 0:
            fail('Could not find private fun startPuzzle in PuzzleViewModel.')
        text = text[:start_idx] + completion_handler + text[start_idx:]

    if 'private fun saveGalaxyconProgress' not in text:
        save_handler = (
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
        next_idx = text.find('fun nextPuzzle')
        if next_idx < 0:
            fail('Could not find fun nextPuzzle in PuzzleViewModel.')
        text = text[:next_idx] + save_handler + text[next_idx:]

    write_if_changed(PUZZLE_VM, original, text)


def main() -> None:
    copy_new_files()
    patch_subcategories()
    patch_puzzle_screen()
    patch_puzzle_view_model()
    print("Galaxycon v2 patch applied. Now sync/build in Android Studio.")


if __name__ == "__main__":
    main()
