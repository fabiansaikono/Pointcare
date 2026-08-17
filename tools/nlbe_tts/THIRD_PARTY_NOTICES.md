# Third-party notices for the NL-BE generation tool

The following development-only dependencies are downloaded to `.poincare_tools/` and are
not packaged in the Android application:

- Piper (`piper-tts`), GPL-3.0: https://github.com/OHF-Voice/piper1-gpl
- `nl_BE-rdh-medium` / `nl_BE-nathalie-medium` voice files from rhasspy/piper-voices.
  The voice repository is MIT; each model card contains the dataset/source licensing details.
- SoundFile, BSD-3-Clause, used only to compress generated WAV output to Ogg Vorbis.

The generated audio output and the manifest are bundled in the app. The Piper runtime,
eSpeak runtime, ONNX Runtime, and voice model remain development tools and are excluded.
