Please update the Poincaré Android content import to use exercise pack v3.4.

Use these exact files:

- `poincare-exercises-math-v3.xml`
- `poincare-exercises-language-v3.xml`
- `poincare-exercises-logic-puzzles-v3.xml`
- `poincare-exercises-world-themes-v3.xml`
- `poincare-exercises-index-v3.xml`
- `poincare-content-pack-v3-full.xsd`
- `manifest.json`

Content rules:

1. There are 19.200 XML exercises total.
2. There are 32 included subcategories.
3. Every included subcategory has 600 exercises.
4. Every included subcategory has 100 exercises per grade, for grades 1 to 6.
5. Difficulty levels are mixed inside every subcategory/grade: 34 one-star, 33 two-star, 33 three-star.
6. Sudoku and Math Crossword are not in XML. They are generated in-app.
7. Use `exercise/@id` as the stable unique identifier.
8. Use `exercise/@progressKey` for progress tracking. It currently equals the id.
9. Do not regenerate IDs at runtime.
10. Do not reset progress when importing this pack.
11. If an existing exercise id is found, update content but keep progress.

Localization rules:

1. Never show `answer/@value` directly when localized displays exist.
2. Show `answer/display[@locale=currentLocale]`.
3. Fallback order: current locale, `nl-BE`, `en-US`, first available display, then `answer/@value`.
4. Use the same localization rule for `solution/display`.

Listening/audio rules:

1. Some language exercises have `type="audio_choice"`.
2. These exercises include optional `<audioSources visibleText="false" playButtonOnly="true" ttsRequired="true">`.
3. The prompt intentionally does not show the target word, letter or sound.
4. The UI must render a play button for `audio_choice`.
5. The play button should use the current locale audio source: `audioSources/audio[@locale=currentLocale]/@text`.
6. If current locale audio is missing, fall back to `nl-BE`, then `en-US`.
7. For nl-BE, the audio exercises include letter recognition, small-word recognition, vowel/sound recognition such as `aa`, `ee`, `ui`, `eu`, and doffe/unstressed vowel words such as `de`, `je`, `me`, `we`, `ze`, `een`.
8. Do not show the audio text in the instruction. Only the play button should expose it through audio/TTS.

Progress rules:

1. Progress states are `not_started`, `completed`, `retry_wrong`.
2. When answered correctly, store `completed`.
3. When answered incorrectly, store `retry_wrong`.
4. When a `retry_wrong` exercise is answered correctly later, change it to `completed`.
5. Show progress counts per subcategory and grade using `poincare-exercises-index-v3.xml`.

Add tests:

- total count is 19.200
- no exercise has subCategory `sudoku`
- no exercise has subCategory `math_crossword`
- all IDs are unique
- all IDs match `{subCategory}_d{difficulty}_g{grade}_seq{sequence4}`
- every included subcategory has 600 exercises
- every included subcategory has 100 exercises per grade
- every subcategory/grade has 34 one-star, 33 two-star and 33 three-star exercises
- every prompt has nl-BE, en-US and fr-BE
- every answer has nl-BE, en-US and fr-BE displays
- every exercise has exactly one correct answer
- no answer values are duplicated inside one exercise
- no localized answer display is duplicated inside one exercise for the same locale
- shape-choice answers show symbols, not text labels
- audio_choice prompts do not contain the target audio text
- audio_choice exercises include audioSources for nl-BE, en-US and fr-BE

Focus on importer, localization, audio_choice support, progress tracking and count display. Do not rewrite unrelated UI/theme code.
