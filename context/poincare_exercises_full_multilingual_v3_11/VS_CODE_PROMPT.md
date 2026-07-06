Please update the Poincaré Android importer and exercise UI for content pack v3.11.

Use these files:

- poincare-exercises-math-v3.xml
- poincare-exercises-language-v3.xml
- poincare-exercises-logic-puzzles-v3.xml
- poincare-exercises-world-knowledge-v3.xml
- poincare-exercises-geometry-v3.xml
- poincare-exercises-index-v3.xml
- manifest.json
- poincare-content-pack-v3-full.xsd

Important changes:

1. Remove the old vocabulary tile.

Do not show the language subcategory `vocabulary` / `Woordenschat`. It has been removed from v3.11 because those exercises are covered by World knowledge. Build category tiles from `poincare-exercises-index-v3.xml`, not from hardcoded old subcategory lists.

2. Reading exercises.

Reading exercises use:

```xml
type="visual_word_choice"
```

Show this layout:

- localized prompt, for example `Wat zie je hier?`
- large centered icon from `visualSources/visual[@locale=currentLocale]/@icon`
- answer buttons using localized `answer/display`

Do not show `visual/@alt` as visible text. Use it only for accessibility/contentDescription. The target word must not be visible in the prompt or under the icon.

3. Writing exercises.

Writing exercises use:

```xml
type="missing_letter_choice"
missingMarker="?"
missingMarkerColor="#FFD700"
sourceLocale="nl-BE"
```

Example visible prompt:

```text
Welke letter ontbreekt? k ? t.
```

Render the `?` marker in yellow. Do not show ellipses and do not add `Kies uit`.

For TTS, use `prompt/@ttsText`, not the visible prompt. The TTS text reads the full intended word or letter sequence.

4. Answer rendering.

For all answer buttons, always display:

```kotlin
answer.displayForLocale(currentLocale)
```

Do not display `answer/@value` when localized displays exist.

In writing exercises, `answer/@value` is now the actual letter or letter group, such as `a`, `aa`, `ui`, or `eu`. It is never `correct`, `wrong1`, or a word like `een`.

5. Reimport behaviour.

When importing v3.11 over an older pack:

- preserve progress for matching IDs
- update prompt, answer and visual content
- clear old in-memory exercise cache
- do not skip content updates just because the exercise ID already exists

Add tests:

- `vocabulary` is not imported and not shown as a tile
- reading exercises have `visualSources` and do not reveal target word in the prompt
- reading icons are shown from `visual/@icon`
- writing prompts do not contain `_`, `...`, `…`, or `Kies uit`
- writing prompts include a `?` marker
- writing UI renders the `?` marker in yellow
- writing TTS uses `prompt/@ttsText`
- writing answer displays are one letter or one Dutch vowel group, never `een` unless the exercise is not writing
- answer buttons use localized displays, not raw values
- reimporting v3.11 updates stale content while preserving progress

Focus only on importer, category mapping, reading visual display, writing missing-letter display, answer rendering, and cache invalidation. Do not rewrite unrelated theme or navigation code.
