# Import notes v3.6

For normal exercises, display the localized prompt and localized answer displays.

For audio exercises:

- Show a play button.
- Use `audio/@text` for TTS.
- Use `audio/@ttsLanguage` for pronunciation.
- Use `audio/@audioId` for caching/tracking.
- Never read `audioId` aloud.
- If `visibleText="false"`, do not show `audio/@text` in the prompt.

For translation exercises:

- Each translation subcategory has `sourceLocale` and `targetLocale`.
- The category screen must only show translation tiles where `sourceLocale` equals the current app language.
- For app locale `en-US`, show only:
  - `translation_en_to_nl`
  - `translation_en_to_fr`
- For app locale `nl-BE`, show only:
  - `translation_nl_to_en`
  - `translation_nl_to_fr`
- For app locale `fr-BE`, show only:
  - `translation_fr_to_nl`
  - `translation_fr_to_en`

Do not show old generic translation tiles.
