# Poincaré exercise pack v3.4

This pack regenerates the exercises with a stronger grade progression.

- 19.200 exercises total
- 32 included subcategories
- 100 exercises per subcategory per grade
- 600 exercises per included subcategory
- Grades 1–6
- Difficulty levels mixed per subcategory/grade: 34×1 star, 33×2 stars, 33×3 stars
- Sudoku excluded because it is generated in-app
- Math Crossword excluded because it is generated in-app
- Stable IDs and progress keys preserved in the same pattern
- Localized answer displays for nl-BE, en-US and fr-BE
- Shape answers use shape symbols, not text labels

## Grade progression

Grade 1 is intentionally much easier: counting, small additions, recognition, letters, sounds and short words.
Grade 6 is intentionally more advanced: larger numbers, fractions/percentages, multi-step reasoning, vocabulary, grammar and abstract concepts.

## Listening exercises

Listening exercises use `type="audio_choice"` and include optional `<audioSources>`.
The prompt never shows the target word, letter or sound. The UI must show a play button and then let the child choose the letter, sound, word or phrase they hear.

Examples included for nl-BE:

- letters: `a`, `b`, `m`, `s`
- sounds: `aa`, `ee`, `ui`, `eu`, `oe`, `ie`, `ei`, `ij`
- doffe/unstressed vowel words: `de`, `je`, `me`, `we`, `ze`, `een`
- small words: `maan`, `raam`, `neus`, `huis`, `muis`, `boek`
