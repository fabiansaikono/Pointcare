# Import notes v3.12

When importing over v3.11, keep progress for matching IDs but update all prompt, answer, audio and missing-letter metadata.

Important UI change for missing-letter exercises:

1. Render the prompt normally: `Welke letter ontbreekt?`.
2. Render `missingLetterDisplay/@visibleLine` on the next line.
3. Color only the marker defined by `missingLetterDisplay/@marker` with `missingLetterDisplay/@markerColor`.
4. Do not color the question mark in the prompt.
5. Use `prompt/@ttsText` or `missingLetterDisplay/@ttsFullWord` for TTS, not the visible line.

Listening and writing letter/sound exercises now contain phonetic metadata:

- `audio/@ttsText`
- `audio/@phoneticText`
- `answer/speak/@ttsText`
- `answer/speak/@phoneticText`

Use these for TTS before falling back to raw visible text.
