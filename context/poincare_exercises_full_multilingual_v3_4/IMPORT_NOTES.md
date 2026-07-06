# Import notes v3.4

Use the XML files as the source of truth.

Important changes:

1. Grade 1 content is easier than v3.3.
2. Grade 6 content is harder than v3.3.
3. Listening exercises now use `type="audio_choice"`.
4. Listening prompts do not show the target. Use `<audioSources>` for the play button/TTS text.
5. `answer/@value` remains canonical correctness data. Do not display it when localized displays exist.
6. Use `answer/display[@locale=currentLocale]` for visible answer labels.
7. Keep existing progress when re-importing stable exercise IDs.
