# Poincaré Exercise Pack v3.13

This version specifically fixes the remaining `a` / `een` issue in:

- Communicatie / Luisteren
- Schrijven / Schrijven

Visible answer displays remain literal letters/sounds, for example `a`, `aa`, `ui`, `eu`.

For TTS, the app must not read the visible display or `answer/@value` directly. It must use:

- `answer/speak[@locale=currentLocale]/@ttsText`
- or `answer/speak[@locale=currentLocale]` text content
- and for play buttons `audio/@ttsText`

For maximum safety, in these two subcategories `audio/@text` has also been changed to a phonetic TTS-safe phrase, while the original value is preserved in `audio/@rawValue` and `audio/@targetValue`.

Validation status: passed
