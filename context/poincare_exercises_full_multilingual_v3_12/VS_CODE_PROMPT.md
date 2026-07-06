Please update the Poincaré Android importer and UI for exercise pack v3.12.

Focus on missing-letter writing exercises and listening letter/sound exercises.

1. Missing-letter rendering

For exercises with type="missing_letter_choice":

- Show the prompt as normal text.
- Do not color the question mark in the prompt.
- Show the word-to-complete on a new line below the prompt.
- Use missingLetterDisplay/@visibleLine for that second line.
- Color only the missing marker in that second line.
- The marker is missingLetterDisplay/@marker, usually "?".
- The marker color is missingLetterDisplay/@markerColor, usually #FFD700.
- Do not color every question mark on the screen.

Example:

Prompt line, normal color:
Welke letter ontbreekt?

Second line:
k ? t.

Only the ? in "k ? t." is yellow.

2. TTS for writing exercises

Do not send the visible line "k ? t." directly to TTS.

Use this fallback order:

- prompt/@ttsText
- missingLetterDisplay/@ttsFullWord
- missingLetterDisplay/speak[@locale=currentLocale]/@ttsText
- prompt visible text

The XML contains pronounceable TTS text so Android/OpenAI TTS does not try to read the question mark as punctuation.

3. TTS for listening exercises

For type="audio_choice", prefer:

- audio/@ttsText
- audio/@phoneticText
- audio/@text

If audioSources/@ttsShouldUsePhoneticText="true", never read the raw single letter or vowel group directly. Use audio/@ttsText.

This is especially important for:

- Communicatie / Luisteren
- letters like a, e, i, o, u
- sounds like aa, ee, ui, eu, oe, ie, ei, ij

4. Answer display for writing/listening

For visible answer buttons, always show:

answer/display[@locale=currentLocale]

Do not show answer/@value when a localized display exists.

If a writing exercise expects the answer "a", the button must show "a", not "een".

5. Answer TTS

If the user taps a button to hear it, use:

- answer/speak[@locale=currentLocale]/@ttsText
- answer/speak[@locale=currentLocale]/@phoneticText
- answer/display[@locale=currentLocale]

Never read answer IDs or audio IDs aloud.

6. Cache/import

When importing v3.12 over older packs:

- preserve progress for matching exercise IDs
- update prompt text
- update missingLetterDisplay metadata
- update answer display metadata
- update audio/phonetic metadata
- clear old in-memory exercise cache

Acceptance criteria:

- "Welke letter ontbreekt?" is normal text color.
- The word pattern appears on a new line.
- Only the missing marker in the word pattern is yellow.
- TTS for writing reads the intended full word / phonetic script.
- TTS for listening letters and sounds uses phonetic metadata.
- Writing answer options show letters or vowel groups, never words like "een" unless that is literally the target answer.
