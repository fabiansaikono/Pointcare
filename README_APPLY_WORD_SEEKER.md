# Word seeker patch for Poincaré

This patch adds **Word seeker** as a generated subgame in the existing **Puzzles** category.

## What it adds

- New generated puzzle subcategory: `word-seeker`
- New screen: `WordSeekerScreen.kt`
- Square word-search grid
- Portrait layout: words in PlanetGold below the grid
- Landscape layout: words in PlanetGold to the right of the grid
- Found words are greyed out and crossed through
- Selection by pressing a letter and dragging a straight line over the word
- A word is only accepted when the path starts at the first letter and ends at the last letter
- Uses the existing generated-puzzle completion flow: `onGeneratedPuzzleComplete()`
- Uses the existing Poincaré look and feel: `DeepSpaceBlue`, `PlanetGold`, `PlanetBlue`, `NebulaPurple`, `StarWhite`

## Files changed by the script

The script creates:

- `app/src/main/java/be/saikono/poincare/ui/puzzle/WordSeekerScreen.kt`
- `app/src/main/res/drawable/ic_topic_word_seeker.xml`

The script patches:

- `app/src/main/java/be/saikono/poincare/model/ContentModels.kt`
- `app/src/main/java/be/saikono/poincare/model/Models.kt`
- `app/src/main/java/be/saikono/poincare/ui/puzzle/PuzzleScreen.kt`
- `app/src/main/java/be/saikono/poincare/ui/puzzle/PuzzleViewModel.kt`
- `app/src/main/java/be/saikono/poincare/ui/home/SubCategorySelectionScreen.kt`

Backups are created next to changed files with `.bak_word_seeker_YYYYMMDD_HHMMSS`.

## Apply from PowerShell

Extract this zip into:

```powershell
C:\AndroidApps\Poincare
```

Then run:

```powershell
cd C:\AndroidApps\Poincare
py .\apply_word_seeker_patch.py
.\gradlew.bat assembleDebug
```

## After build

Open the app and go to:

```text
Puzzles → Word seeker
```

