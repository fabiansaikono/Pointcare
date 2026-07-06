Please update the Poincaré importer and TTS handling for exercise pack v3.13.

This fixes the remaining issue where letter `a` is shown or read as `een` in:

- Communicatie / Luisteren
- Schrijven / Schrijven

Critical rules:

1. Do not translate answer displays.

For answer buttons, show exactly:

answer/display[@locale=currentLocale]

Do not run this value through automatic translation.
Do not map `a` to `een`.
Do not display answer/@value when a display exists.

2. For letter and sound TTS, do not read the visible button text.

For answer-button read-aloud, use:

answer/speak[@locale=currentLocale]/@ttsText

Fallback order:

- speak/@ttsText
- speak/@phoneticText
- speak textContent
- display text only as a last resort

3. For play-button audio in Communicatie/Luisteren, use:

audio/@ttsText

Do not use raw single-letter values for TTS.
Do not read `a` directly, because TTS may treat it as the article and say `een`.

4. In v3.13, audio/@text is already TTS-safe for these subcategories.
The original literal value is stored separately as:

- audio/@rawValue
- audio/@targetValue
- audio/@spokenValue where present

Use rawValue/targetValue for correctness if needed.
Use ttsText for speech.

5. Schrijven / Schrijven layout:

Show the instruction in normal font color:

Welke letter ontbreekt?

Then show the word line on a new line:

k ? t.

Only the question mark inside the word line is yellow.
The question mark in the instruction remains normal font color.

6. Clear old content cache after importing v3.13.

If the app still shows `een`, it is using cached old content, translating answer displays, or reading the wrong field.

Tests to add:

- In writing_d1_g1_seq0001, the visible correct answer is `a`, not `een`.
- In writing_d1_g1_seq0001, the TTS for the correct answer is a phonetic text such as `de klank a zoals in kat`, not raw `a`.
- In all listening exercises, no play-button TTS uses raw `a`.
- In all writing/listening exercises, no answer display contains `een` for the value `a`.
- Answer displays are never auto-translated.
- The missing-letter instruction question mark is normal color.
- Only the missing marker in the word line is yellow.
