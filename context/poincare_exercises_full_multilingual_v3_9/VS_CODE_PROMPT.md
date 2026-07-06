Please update the Poincaré Android importer and exercise UI for content pack v3.9.

Use these files:

- `poincare-exercises-math-v3.xml`
- `poincare-exercises-language-v3.xml`
- `poincare-exercises-logic-puzzles-v3.xml`
- `poincare-exercises-world-knowledge-v3.xml`
- `poincare-exercises-geometry-v3.xml`
- `poincare-exercises-index-v3.xml`
- `manifest.json`
- `poincare-content-pack-v3-full.xsd`

Important display fixes in v3.9:

1. Do not add answer-option lists to the prompt.

The visible prompt must not show text such as:

- `Kies uit: ...`
- `Choose from: ...`
- `Choisis parmi : ...`

The XML prompts are already cleaned. The UI should only show the prompt and then show the answer options as buttons/cards.

2. Missing-letter exercises now avoid unpronounceable ellipsis prompts.

Example visible prompt:

`Welke letter ontbreekt in maan? m biep a n.`

Use `prompt/@ttsText` for text-to-speech. Do not reconstruct the spoken text from the old prompt or answer options.

3. Math prompts with large numbers use spaces for readability.

Show:

`Bereken: 132 493 + 14 421 + 4 737`

Do not collapse it to:

`Bereken: 132493 + 14421 + 4737`

Keep canonical answer values for validation, but visible answer displays may also contain spaces.

4. Math TTS must use `prompt/@ttsText`.

For subtraction, do not let Android TTS read `-` as a dash. The XML contains localized spoken text, for example:

- nl-BE: `min`
- en-US: `minus`
- fr-BE: `moins`

5. Answer display rule.

For visible answer buttons, always use:

`answer/display[@locale=currentLocale]`

Do not show `answer/@value` when a localized display exists.

This is important because some exercises use canonical values such as `correct`, `wrong1`, or stable internal keys. Those values are not UI labels.

6. Missing-letter answer options.

For missing-letter exercises, the answer buttons must show the localized displays, for example:

- `a`
- `n`
- `t`
- `h`

Never replace a letter option by a word such as `een`. If that happens, the app is reading the wrong field or mixing cached content from an older pack.

7. World knowledge duplicates.

v3.9 regenerates world knowledge questions to avoid repeated adjacent prompts such as `What do you drink when you are thirsty?`. Make sure the app imports the new XML content and does not keep showing old cached exercise rows.

Import rules:

- Import must be idempotent.
- Use `exercise/@id` as the stable ID.
- Use `exercise/@progressKey` for progress.
- If an exercise ID already exists, update the content but preserve the user progress.
- Do not skip updating prompt/answer text just because the ID already exists.
- Clear any old in-memory exercise cache after importing v3.9.

Tests to add:

- No visible prompt contains `Kies uit`, `Choose from`, or `Choisis parmi`.
- No visible prompt contains `...`, `…`, or `_` for missing-letter blanks.
- Missing-letter prompts contain a full word and a bleep/beep/bip pattern.
- Missing-letter answer options are one-character localized displays.
- Math visible prompts format large values with spaces as thousands separators.
- Math TTS uses `prompt/@ttsText`, and subtraction is read as min/minus/moins.
- Answer buttons use localized displays, not `answer/@value`.
- Reimporting v3.9 updates old content but keeps progress.
- World knowledge exercises do not repeat the same visible prompt several times in a row.

Focus only on importer, prompt display, TTS usage, localized answer display, and cache/content refresh. Do not rewrite unrelated navigation or theme code.
