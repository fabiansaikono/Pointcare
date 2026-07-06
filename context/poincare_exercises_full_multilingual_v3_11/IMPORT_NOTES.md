# Import notes v3.11

- Import over older packs idempotently. Preserve progress for matching `exercise/@id` and `progressKey`, but update prompt/answer content.
- Clear old in-memory caches after import, otherwise old writing answer labels such as `een` may still appear.
- Do not display the removed `vocabulary` subcategory.
- For `visual_word_choice`, show prompt → icon → answer buttons. Use `visual/@alt` only for accessibility.
- For `missing_letter_choice`, show prompt text and render the `?` marker in yellow using `exercise/@missingMarkerColor` or `missingLetterDisplay/@markerColor`.
- For all exercises, visible answers must use localized `answer/display`, not `answer/@value` when displays exist.
