# Poincaré exercise pack v3.8

This version corrects visible prompt quality and TTS metadata.

## Main fixes

- No visible variant numbers or duplicate suffixes in exercise labels.
- No underscores in visible prompts.
- Article grammar exercises now ask natural questions such as “Wat is het bepaald lidwoord van tafel?” and use two options such as “de tafel” / “het tafel”.
- Math prompts include `ttsText` so `-` / `−` is read as minus/min/moins instead of dash.
- Shape answer buttons include `answer/speak` metadata and should read only the shape name.
- World knowledge questions were regenerated to avoid rows of repeated prompts like “What do you drink when you are thirsty?”.
- Time, Measurement, and Shapes and geometry remain under Geometry.

## Counts

- Total exercises: 22200
- 100 exercises per subcategory per grade
- Sudoku and Math Crossword are excluded because they are generated in-app.
