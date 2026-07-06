# Poincaré exercise content pack v3.9

This version fixes prompt visibility and TTS issues found in v3.8.

Main fixes:

- No visible prompt contains `Kies uit`, `Choose from`, or `Choisis parmi`.
- Missing-letter exercises no longer use ellipsis inside the word; they show the full word and a pronounceable bleep pattern.
- Large visible numbers use spaces as thousands separators, for example `132 493`.
- Math `ttsText` reads `-` as `min`, `minus`, or `moins` depending on locale.
- World knowledge questions were regenerated to avoid repeated questions appearing in a row.
- Answer displays remain localized; apps must show `answer/display[@locale=currentLocale]`, not `answer/@value`.
