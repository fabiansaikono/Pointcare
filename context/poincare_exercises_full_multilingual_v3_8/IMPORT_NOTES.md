# Import notes v3.8

Import v3.8 idempotently. Preserve progress by `progressKey`.

Important UI/TTS rules:

- Visible labels must use prompt text only and must not add variant labels.
- Read prompt/@ttsText when the prompt is spoken.
- For shape answer buttons, display the symbol but read `answer/speak[@locale=currentLocale]`.
- Do not read Unicode names, ASCII codes, or “filled” descriptions for shapes.
- For article exercises, show only the two provided answer options.
