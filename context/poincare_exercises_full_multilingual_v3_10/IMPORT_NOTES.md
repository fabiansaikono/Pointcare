# Import notes for v3.10

Reading exercises changed from word-definition prompts to visual word recognition.

For `type="visual_word_choice"`:

1. Show the localized prompt, for example `Wat zie je hier?`.
2. Render the icon from `visualSources/visual[@locale=currentLocale]/@icon` as a large centered icon below the prompt.
3. Use `visual/@alt` only for accessibility/content description, not as visible label.
4. Show answer buttons using localized `answer/display[@locale=currentLocale]`.
5. Do not show the correct word inside the prompt.
6. Preserve progress by `exercise/@progressKey`.
