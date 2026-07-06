# Poincaré Exercise Pack v3.5

Generated on 2026-07-03T00:00:00+02:00.

## Main changes in v3.5

- Split the old **Vertalen / Translation / Traduction** tile into two tiles:
  - `translation_to_english` — Vertalen naar Engels / Translate to English / Traduire vers l’anglais
  - `translation_to_french` — Vertalen naar Frans / Translate to French / Traduire vers le français
- Translation exercises now include `audioSources` with `audioId`, `text`, `locale`, `ttsLanguage`, `sourceLocale` and `targetLocale`.
- The prompt does not reveal the source word for audio translation exercises. The app must show only the play button.
- Audio text is human-readable and does not contain underscores.
- Added main category `world_knowledge`: Wereldkennis / World knowledge / Le Monde.
- Added main category `geometry`: Meetkunde / Geometry / Géométrie.
- Moved `geometry_shapes` from Math to Geometry.
- Increased vocabulary variety for translation exercises.
- Reduced repeated early arithmetic prompts such as `0 + 2` by regenerating early arithmetic with broader combinations, story variants, and duplicate-prompt variation.
- Added a duplicate-prompt pass so exact prompt duplicates inside the same subcategory/grade are minimized.

## Counts

- 19800 exercises total
- 33 included subcategories
- 100 exercises per subcategory per grade
- 600 exercises per included subcategory
- Sudoku excluded
- Math Crossword excluded

## Importer rule

Use `answer/@value` for correctness and `answer/display[@locale=currentLocale]` for visible answer text.
For audio, use `audioSources/audio/@text` with `audioSources/audio/@ttsLanguage`.
