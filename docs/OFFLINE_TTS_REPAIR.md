# Offline TTS repair

This migration replaces the missing `NeuralSpeechAssetStore` and the generated
word-fragment pipeline with complete-utterance synthesis on the device.

## Runtime behavior

- `nl-BE` is synthesized by the bundled Nathalie medium model.
- The complete prompt or label is passed to one model invocation and one
  `AudioTrack`; words, numbers, and operators are never scheduled as separate
  players.
- New content needs no audio generation or manifest entry.
- `fr-BE` and `en-US` continue to use Android system TTS until bundled voices
  for those locales pass the same device and language acceptance tests.
- If the local Dutch model cannot initialize, Android system TTS is used as a
  last-resort accessibility fallback instead of producing silence.
- Existing authored `audio/<exercise-id>.mp3` recordings remain supported.
  They are not part of the removed generated-fragment system.

## Model location

The installer places the converted model under:

`app/src/main/assets/tts/vits-piper-nl_BE-nathalie-medium/`

The installed application requires no network connection. On first use,
eSpeak-NG language data is copied atomically from APK assets to the app's
private no-backup directory because the native phonemizer requires filesystem
paths. No audio is downloaded or generated during this copy.

## Release license gate

Do not publish this configuration until its distribution obligations have been
reviewed for the app's intended license.

- sherpa-onnx is Apache-2.0.
- The Nathalie voice repository is marked MIT and its dataset model card says
  CC0.
- sherpa-onnx 1.13.x currently embeds eSpeak-NG/Piper phonemization. Upstream
  has identified eSpeak-NG's GPL license as a compatibility constraint and is
  planning a breaking 2.0 runtime that accepts a lexicon or externally supplied
  tokens.

Production choices are therefore:

1. complete the applicable GPL source/license obligations;
2. migrate this adapter to sherpa-onnx 2.x plus a distributable Dutch
   lexicon/tokenizer when released; or
3. replace the model adapter after evaluating a character-level model such as
   Supertonic 3, whose model is OpenRAIL-M and whose Dutch accent and phonics
   quality still require Belgian-Dutch acceptance testing.

References:

- https://github.com/k2-fsa/sherpa-onnx/issues/3731
- https://github.com/k2-fsa/sherpa-onnx
- https://huggingface.co/rhasspy/piper-voices/tree/main/nl/nl_BE/nathalie/medium
- https://huggingface.co/csukuangfj/vits-piper-nl_BE-nathalie-medium/blob/main/MODEL_CARD

## Required acceptance test

Run on the lowest-end supported physical Android device, first online and then
in airplane mode:

| Case | Expected result |
| --- | --- |
| `de auto` | One sequential, intelligible utterance |
| `watermeloen` | Complete word, no clipped ending |
| `jeuk` | Natural word pronunciation |
| `Welke letter ontbreekt? Het woord is jeuk.` | One natural prompt |
| `Bereken 5 + 4 - 1 = 8` | Operators spoken in order, no overlap |
| `eu`, `au`, `ou`, `oo` | Complete Flemish sounds in their carrier phrases |
| Newly entered sentence absent from project XML | Synthesizes without regeneration |
| Repeated rapid play/stop/play | Old request stops; only newest request is heard |
| Leave Reading and open Math | TTS still works; one ViewModel does not shut down shared audio |

Record cold-start time, warm synthesis time, peak memory, model initialization
errors, clipping, and pronunciation observations. Do not delete legacy generated
assets in the same release; remove them only after this matrix passes and APK
contents confirm that no runtime code references their manifest.
