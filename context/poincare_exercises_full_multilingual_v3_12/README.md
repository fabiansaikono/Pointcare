# Poincaré Exercise Pack v3.12

This version updates the v3.11 exercise content with a focused fix for writing/listening TTS and missing-letter rendering.

## Main fixes

- In `Schrijven / Writing`, the question `Welke letter ontbreekt?` is plain prompt text. Its question mark must not be colored yellow.
- The word to complete is provided separately in `missingLetterDisplay/@visibleLine`, for example `k ? t.`.
- Only the missing marker in that separate word line should be yellow, using `missingLetterDisplay/@markerColor="#FFD700"`.
- Writing prompts include `prompt/@ttsText` with a pronounceable full-word script.
- Writing answer options include `answer/speak` metadata for letters and Dutch vowel groups such as `a`, `aa`, `ee`, `ui`, `eu`.
- Listening letter/sound exercises include explicit `audio/@phoneticText` and `audio/@ttsText`.
- Importers must show localized `answer/display[@locale=currentLocale]`, never stale/canonical values such as `een` when the intended answer is `a`.

Total exercise count remains 21.600.
