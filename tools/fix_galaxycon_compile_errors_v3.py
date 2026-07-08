#!/usr/bin/env python3
"""Fix Galaxycon v2 compile errors in PuzzleViewModel.kt and PuzzleScreen.kt.

Run from the Pointcare repository root:
    py tools/fix_galaxycon_compile_errors_v3.py

This script is intentionally small and only fixes the malformed one-line
Galaxycon snippets created by the v2 installer.
"""
from pathlib import Path
import shutil
import sys

ROOT = Path.cwd()
PUZZLE_VM = ROOT / "app/src/main/java/be/saikono/poincare/ui/puzzle/PuzzleViewModel.kt"
PUZZLE_SCREEN = ROOT / "app/src/main/java/be/saikono/poincare/ui/puzzle/PuzzleScreen.kt"


def fail(message: str) -> None:
    print(f"Galaxycon v3 fix failed: {message}", file=sys.stderr)
    sys.exit(1)


def read(path: Path) -> str:
    if not path.exists():
        fail(f"Missing file: {path}")
    return path.read_text(encoding="utf-8")


def backup(path: Path) -> None:
    bak = path.with_suffix(path.suffix + ".bak_galaxycon_v3")
    if not bak.exists():
        shutil.copy2(path, bak)


def write_if_changed(path: Path, original: str, updated: str) -> None:
    if updated != original:
        backup(path)
        path.write_text(updated, encoding="utf-8")
        print(f"Patched: {path.relative_to(ROOT)}")
    else:
        print(f"Already OK: {path.relative_to(ROOT)}")


GALAXYCON_BRANCH = '''"galaxycon" -> ExerciseContent(
            id = "generated-galaxycon-${level.name.lowercase()}-$stars",
            domain = LearningDomain.PUZZLE,
            skill = GALAXYCON_SUBCATEGORY_ID,
            section = ExerciseSection.PUZZLES,
            level = level,
            inputMode = InputMode.DRAG_DROP_LETTERS,
            prompt = LocalizedText(nl = "Galaxycon", en = "Galaxycon", fr = "Galaxycon"),
            data = mapOf(
                "mainCategory" to "logic_puzzles",
                "subcategoryId" to GALAXYCON_SUBCATEGORY_ID,
                "grade" to (level.toGrade()?.toString() ?: ""),
                "difficultyStars" to stars.toString(),
                "topic" to "Galaxycon",
                "topic_en-US" to "Galaxycon",
                "topic_fr-BE" to "Galaxycon"
            ),
            answer = "planet"
        )

        '''

ON_GALAXYCON_COMPLETE = '''fun onGalaxyconComplete(result: GalaxyconRunResult) {
        val state = _uiState.value
        val exercise = state.currentExercise ?: return
        val duration = if (result.elapsedMs > 0L) {
            result.elapsedMs
        } else {
            System.currentTimeMillis() - startTime
        }

        _uiState.update {
            it.copy(
                isSolved = result.planetReached,
                isWrong = !result.planetReached,
                showFeedback = true
            )
        }
        if (result.planetReached) audioService.playSuccess() else audioService.playFailure()
        saveGalaxyconProgress(exercise, duration, result)
    }

    '''

SAVE_GALAXYCON_PROGRESS = '''private fun saveGalaxyconProgress(
        exercise: ExerciseContent,
        durationMs: Long,
        result: GalaxyconRunResult
    ) {
        val profileId = _uiState.value.currentProfileId ?: return
        viewModelScope.launch {
            val event = ProgressEvent(
                id = UUID.randomUUID().toString(),
                childProfileId = profileId,
                exerciseId = exercise.id,
                domain = exercise.domain,
                skill = exercise.skill,
                timestamp = System.currentTimeMillis(),
                answerCorrect = result.planetReached,
                responseTimeMs = durationMs,
                retryCount = if (result.planetReached) 0 else 1,
                rewardStampsEarned = (missionPointsFor(exercise, result.planetReached) + result.stickersEarned.size).coerceAtMost(50),
                metadata = mapOf(
                    "answer" to if (result.planetReached) "planet_reached" else "run_finished",
                    "progressKey" to (exercise.data["progressKey"] ?: exercise.id),
                    "subcategoryId" to GALAXYCON_SUBCATEGORY_ID,
                    "grade" to (exercise.data["grade"] ?: (exercise.level.toGrade()?.toString() ?: "")),
                    "progressState" to if (result.planetReached) "completed" else "retry_wrong",
                    "galaxyconScorePercent" to result.displayScore,
                    "galaxyconPoints" to result.points.toString(),
                    "galaxyconCollisions" to result.collisions.toString(),
                    "galaxyconWordsCompleted" to "${result.wordsCompleted}/${result.totalWords}",
                    "galaxyconStarsCollected" to result.starsCollected.toString(),
                    "galaxyconStickers" to result.stickersEarned.joinToString("|"),
                    "stickerBookSubgame" to GALAXYCON_SUBCATEGORY_ID
                )
            )
            progressRepository.addProgress(event)
            syncManager.scheduleSync()
        }
    }

    '''


def patch_puzzle_view_model() -> None:
    original = read(PUZZLE_VM)
    text = original

    # Reformat the Galaxycon when-branch. v2 left it as:
    # ... answer = "planet" ) "word-seeker" -> ...
    branch_start = text.find('"galaxycon" -> ExerciseContent(')
    if branch_start >= 0:
        word_seeker_start = text.find('"word-seeker" -> ExerciseContent(', branch_start)
        else_start = text.find('else -> null', branch_start)
        if word_seeker_start >= 0:
            text = text[:branch_start] + GALAXYCON_BRANCH + text[word_seeker_start:]
        elif else_start >= 0:
            text = text[:branch_start] + GALAXYCON_BRANCH + text[else_start:]
        else:
            fail('Could not find the end of the Galaxycon generated-puzzle branch.')
    else:
        fail('Could not find the Galaxycon generated-puzzle branch in PuzzleViewModel.kt.')

    # Reformat the one-line completion handler. Without newlines Kotlin sees one long statement.
    handler_start = text.find('fun onGalaxyconComplete(result: GalaxyconRunResult)')
    if handler_start >= 0:
        start_puzzle_start = text.find('private fun startPuzzle', handler_start)
        if start_puzzle_start < 0:
            fail('Could not find private fun startPuzzle after onGalaxyconComplete.')
        text = text[:handler_start] + ON_GALAXYCON_COMPLETE + text[start_puzzle_start:]
    else:
        fail('Could not find onGalaxyconComplete in PuzzleViewModel.kt.')

    # Reformat the one-line progress saver.
    save_start = text.find('private fun saveGalaxyconProgress(')
    if save_start >= 0:
        next_puzzle_start = text.find('fun nextPuzzle', save_start)
        if next_puzzle_start < 0:
            fail('Could not find fun nextPuzzle after saveGalaxyconProgress.')
        text = text[:save_start] + SAVE_GALAXYCON_PROGRESS + text[next_puzzle_start:]
    else:
        fail('Could not find saveGalaxyconProgress in PuzzleViewModel.kt.')

    write_if_changed(PUZZLE_VM, original, text)


def patch_puzzle_screen() -> None:
    original = read(PUZZLE_SCREEN)
    text = original

    # The preview's PuzzleContent call missed the new Galaxycon callback parameter.
    marker = 'onGeneratedPuzzleComplete = {},'
    preview_start = text.find('fun PuzzleScreenPreview()')
    if preview_start >= 0 and 'onGalaxyconComplete = {}' not in text[preview_start:]:
        marker_pos = text.find(marker, preview_start)
        if marker_pos < 0:
            fail('Could not find onGeneratedPuzzleComplete = {} in PuzzleScreen preview.')
        insert_pos = marker_pos + len(marker)
        text = text[:insert_pos] + '\n        onGalaxyconComplete = {},' + text[insert_pos:]

    # Do not show the generic Next Mission button over the Galaxycon result card.
    text = text.replace(
        'val isCalculatorPuzzle = subcategoryId == "calculator-puzzle"\n\n if ((uiState.isSolved || uiState.isFinished) && !isCalculatorPuzzle)',
        'val isCalculatorPuzzle = subcategoryId == "calculator-puzzle"\n val isGalaxyconPuzzle = subcategoryId == GALAXYCON_SUBCATEGORY_ID\n\n if ((uiState.isSolved || uiState.isFinished) && !isCalculatorPuzzle && !isGalaxyconPuzzle)'
    )
    text = text.replace(
        'val isCalculatorPuzzle = subcategoryId == "calculator-puzzle"\r\n\r\n if ((uiState.isSolved || uiState.isFinished) && !isCalculatorPuzzle)',
        'val isCalculatorPuzzle = subcategoryId == "calculator-puzzle"\r\n val isGalaxyconPuzzle = subcategoryId == GALAXYCON_SUBCATEGORY_ID\r\n\r\n if ((uiState.isSolved || uiState.isFinished) && !isCalculatorPuzzle && !isGalaxyconPuzzle)'
    )

    write_if_changed(PUZZLE_SCREEN, original, text)


def main() -> None:
    patch_puzzle_view_model()
    patch_puzzle_screen()
    print('Galaxycon v3 compile fix applied. Now Sync Gradle and Build > Make Project.')


if __name__ == '__main__':
    main()
