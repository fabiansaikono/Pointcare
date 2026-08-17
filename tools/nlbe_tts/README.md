# Poincaré Belgian-Dutch neural speech

This folder generates deterministic `nl-BE` speech assets on the development computer.
The Android application does not contain Piper, eSpeak, ONNX Runtime, or a voice model.

From the project root:

```powershell
py .\tools\nlbe_tts\generate_nl_be_audio.py --bootstrap --scope language --voice rdh
```

Useful alternatives:

```powershell
# Only explicit ttsText/speak/audio fields and critical phonics
py .\tools\nlbe_tts\generate_nl_be_audio.py --bootstrap --scope critical

# Include all supported content text (can substantially increase APK size)
py .\tools\nlbe_tts\generate_nl_be_audio.py --bootstrap --scope full --max-items 0

# Regenerate with the other Belgian voice
py .\tools\nlbe_tts\generate_nl_be_audio.py --bootstrap --voice nathalie --force

# Validate manifest coverage and files
py .\tools\nlbe_tts\generate_nl_be_audio.py --validate-only
```

The default `language` scope prioritizes all explicit speech metadata, all language prompts,
and short language answer options. Literal graphemes use `pronunciation_overrides.json`.
Review `build/nlbe_tts_preview/index.html` after generation.
