Please update the Poincaré Android importer and exercise UI for content pack v3.10.

Main change: reading exercises are now visual word recognition exercises.

Problem fixed:

The old reading prompts looked like this:

```text
Lees het woord: stoel. Wat betekent het?
```

This reveals the answer and then also uses `stoel` as the correct answer. That is not a useful exercise.

New behaviour:

For exercises with:

```xml
type="visual_word_choice"
subCategory="reading"
```

show the localized prompt only, for example:

```text
Wat zie je hier?
```

Then show the icon from `visualSources` directly below the prompt.

Example XML:

```xml
<exercise type="visual_word_choice" subCategory="reading">
  <prompts>
    <prompt locale="nl-BE" ttsText="Wat zie je hier?">Wat zie je hier?</prompt>
    <prompt locale="en-US" ttsText="What do you see here?">What do you see here?</prompt>
    <prompt locale="fr-BE" ttsText="Que vois-tu ici ?">Que vois-tu ici ?</prompt>
  </prompts>
  <visualSources visibleText="false" kind="object_icon" iconType="emoji" displayStyle="large_center_icon" targetValue="chair">
    <visual locale="nl-BE" icon="🪑" alt="stoel" labelVisible="false" />
    <visual locale="en-US" icon="🪑" alt="chair" labelVisible="false" />
    <visual locale="fr-BE" icon="🪑" alt="chaise" labelVisible="false" />
  </visualSources>
</exercise>
```

UI rules:

1. Show prompt text from `prompt[@locale=currentLocale]`.
2. Render a large centered icon from `visualSources/visual[@locale=currentLocale]/@icon`.
3. Do not show the target word in the prompt.
4. Do not show `visual/@alt` as visible text. Use it only as content description/accessibility label.
5. Show answer buttons using `answer/display[@locale=currentLocale]`.
6. Correctness still uses `answer/@correct` or canonical `answer/@value`.
7. If the locale-specific visual is missing, fall back to `nl-BE`, then `en-US`, then the first available visual.
8. Keep progress by `exercise/@progressKey`; importing v3.10 must update content but preserve progress.

Add tests:

- Reading prompts do not contain `Lees het woord`, `Read the word`, or `Lis le mot`.
- Reading prompts do not contain the correct answer display.
- Every reading exercise has exactly one `visualSources` block.
- Every reading exercise has a visual with `icon` and localized `alt`.
- `visual/@alt` is not displayed visibly in the exercise UI.
- Answer buttons still show localized answer display text.
- Existing progress is preserved when importing v3.10 over older packs.

Do not rewrite unrelated categories, navigation, theme, Sudoku, or Math Crossword generators. Focus on importer support for `visualSources`, rendering of `visual_word_choice`, and preserving progress.
