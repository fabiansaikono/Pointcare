# Poincaré exercise content pack v3.6

This pack contains 22,200 exercises.

Changes in v3.6:

- Removed visible theme/category labels from exercise prompts. Prompts no longer say `Theme`, `Thema` or `Thème`.
- Replaced the old translation subcategories with six locale-aware translation directions:
  - `translation_nl_to_en`
  - `translation_nl_to_fr`
  - `translation_en_to_nl`
  - `translation_en_to_fr`
  - `translation_fr_to_nl`
  - `translation_fr_to_en`
- The app must show only translation subcategories where `sourceLocale` equals the current app language.
- English users therefore get English-to-Dutch and English-to-French exercises, not Dutch-to-French exercises.
- Translation and listening exercises include audio metadata: `audioId`, `text`, `locale`, and `ttsLanguage`.
- `audio/@text` is human-readable and contains no underscores.
- Translation vocabulary was expanded to reduce repetition.
- Sudoku and Math Crossword remain excluded because those are generated in-app.

Use `poincare-exercises-index-v3.xml` for category, subcategory and grade counts.
