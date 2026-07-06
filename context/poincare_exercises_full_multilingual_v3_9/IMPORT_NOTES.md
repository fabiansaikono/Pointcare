# Import notes v3.9

Import v3.9 over older content idempotently. Preserve progress for matching `exercise/@id` and `progressKey`.

UI rules:

- Visible prompts must use `prompt.text`.
- TTS must use `prompt/@ttsText` when present.
- Answer buttons must use localized `answer/display[@locale=currentLocale]`.
- Do not display `answer/@value` when a localized display exists.
- For audio exercises, show the play button and read `audio/@text`; never read `audioId`.
