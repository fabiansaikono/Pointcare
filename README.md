# Poincaré puzzle UI final fixes

Extract this ZIP outside or inside your project. From your project root, copy the replacement files into `app/...` and run the helper script.

PowerShell example if you extracted the folder `final_puzzle_update` inside your project root:

```powershell
Copy-Item -Recurse -Force .\final_puzzle_update\app\* .\app\
py .\final_puzzle_update\scripts\apply_puzzle_layout_final_fixes.py
.\gradlew.bat :app:compileDebugKotlin
```

Replacement files included:

```text
app/src/main/java/be/saikono/poincare/ui/puzzle/CalculatorPuzzleScreen.kt
app/src/main/java/be/saikono/poincare/ui/puzzle/ConstellationsPuzzleScreen.kt
app/src/main/java/be/saikono/poincare/ui/puzzle/ConstellationPuzzleGenerator.kt
app/src/main/java/be/saikono/poincare/ui/puzzle/ConstellationPuzzleModels.kt
```

The helper script patches these current project files without replacing the whole file:

```text
app/src/main/java/be/saikono/poincare/ui/puzzle/SudokuScreen.kt
app/src/main/java/be/saikono/poincare/ui/puzzle/MathCrosswordScreen.kt
app/src/main/java/be/saikono/poincare/ui/puzzle/PuzzleScreen.kt
app/src/main/java/be/saikono/poincare/ui/home/SubCategorySelectionScreen.kt
```
