# Poincaré exercise content pack v3.10

This version fixes the reading exercises.

Main fixes:

- Reading exercises no longer reveal the answer in the prompt.
- Prompts now ask a neutral question such as `Wat zie je hier?`.
- Reading exercises now include a `visualSources` block with a large object/concept icon.
- The app should display the icon directly below the prompt.
- Answer options remain localized word buttons.
- Reading exercise type is now `visual_word_choice`.
- Existing exercise IDs and progress keys are preserved.

Important rendering rule: for `visual_word_choice`, show the prompt, then the icon from `visualSources`, then the answer buttons. Do not show `visual/@alt` as visible text unless required for accessibility.
