#!/usr/bin/env bash
#
# Pointcare offline nl-BE pronunciation follow-up
# Requires the complete-utterance offline TTS repair and v1.1 pronunciation
# correction to be installed.
#
set -Eeuo pipefail

readonly SCRIPT_VERSION="1.2.2"
readonly MODEL_RELATIVE_DIR="app/src/main/assets/tts/vits-piper-nl_BE-nathalie-medium"
readonly NORMALIZER_RELATIVE_PATH="app/src/main/java/be/saikono/poincare/data/audio/SpeechTextNormalizer.kt"
readonly AUDIO_SERVICE_RELATIVE_PATH="app/src/main/java/be/saikono/poincare/data/audio/AudioService.kt"
readonly TEST_RELATIVE_PATH="app/src/test/java/be/saikono/poincare/data/audio/SpeechTextNormalizerTest.kt"

REPO_ARG="."
SKIP_BUILD=0
TEMP_ROOT=""
MODIFIER_FILE=""
BACKUP_ROOT=""
LIVE_APPLIED=0
SUCCESS=0

usage() {
  cat <<'USAGE'
Usage:
  repair_pointcare_offline_tts.sh [--repo PATH] [--skip-build]

Adds the following nl-BE speech corrections to the existing offline repair:
  * ?/8 and similar prompts become "Hoeveel achtste", etc.
  * measurement abbreviations are expanded only after quantities/placeholders;
  * vowel-sound exercise labels receive a 200 ms pre-speech delay.

Normal behavior stages the live app, runs focused tests, assembles a complete
debug APK, and only then updates the live source. Use --skip-build only when
the same update has already passed those checks in an identical working tree.
USAGE
}

log() {
  printf '[Pointcare TTS pronunciation] %s\n' "$*"
}

die() {
  printf '[Pointcare TTS pronunciation] ERROR: %s\n' "$*" >&2
  exit 1
}

restore_live_files() {
  local relative_path
  [[ -n $BACKUP_ROOT && -d $BACKUP_ROOT ]] || return 0
  for relative_path in \
    "$NORMALIZER_RELATIVE_PATH" \
    "$AUDIO_SERVICE_RELATIVE_PATH" \
    "$TEST_RELATIVE_PATH"; do
    if [[ -f "$BACKUP_ROOT/$relative_path" ]]; then
      mkdir -p "$(dirname "$REPO_ROOT/$relative_path")"
      cp "$BACKUP_ROOT/$relative_path" "$REPO_ROOT/$relative_path"
    fi
  done
}

cleanup() {
  local status=$?
  trap - EXIT INT TERM

  if [[ $SUCCESS -ne 1 && $LIVE_APPLIED -eq 1 ]]; then
    if restore_live_files; then
      printf '[Pointcare TTS pronunciation] Live source restored after the failed update.\n' >&2
    else
      printf '[Pointcare TTS pronunciation] WARNING: automatic source restoration failed; inspect the three TTS files.\n' >&2
    fi
  fi

  if [[ -n $TEMP_ROOT ]]; then
    rm -rf -- "$TEMP_ROOT"
  fi
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "Required command not found: $1"
}

run_modifier() {
  local repo=$1
  python3 "$MODIFIER_FILE" "$repo"
}

run_gradle_checks() {
  local repo=$1
  (
    cd "$repo"
    if [[ -f ./gradlew ]]; then
      bash ./gradlew \
        --no-daemon \
        --stacktrace \
        :app:testDebugUnitTest \
        --tests 'be.saikono.poincare.data.audio.SpeechTextNormalizerTest'
      bash ./gradlew \
        --no-daemon \
        --stacktrace \
        :app:assembleDebug
    elif [[ -f ./gradlew.bat ]] && command -v cmd.exe >/dev/null 2>&1; then
      cmd.exe /d /c gradlew.bat \
        --no-daemon \
        --stacktrace \
        :app:testDebugUnitTest \
        --tests be.saikono.poincare.data.audio.SpeechTextNormalizerTest
      cmd.exe /d /c gradlew.bat \
        --no-daemon \
        --stacktrace \
        :app:assembleDebug
    else
      die "No usable Gradle wrapper found."
    fi
  )
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo)
      [[ $# -ge 2 ]] || die "--repo requires a path."
      REPO_ARG=$2
      shift 2
      ;;
    --skip-build)
      SKIP_BUILD=1
      shift
      ;;
    -h|--help)
      usage
      SUCCESS=1
      exit 0
      ;;
    *)
      die "Unknown option: $1 (use --help)"
      ;;
  esac
done

require_command git
require_command tar
require_command grep
require_command mktemp
require_command python3

[[ -d $REPO_ARG ]] || die "Repository path does not exist: $REPO_ARG"
REPO_ROOT=$(cd "$REPO_ARG" && pwd -P)
[[ -f "$REPO_ROOT/settings.gradle.kts" && -f "$REPO_ROOT/app/build.gradle.kts" ]] ||
  die "This does not look like the Pointcare Android repository: $REPO_ROOT"
git -C "$REPO_ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1 ||
  die "Repository is not a Git worktree: $REPO_ROOT"

for required_path in \
  "$NORMALIZER_RELATIVE_PATH" \
  "$AUDIO_SERVICE_RELATIVE_PATH" \
  "$TEST_RELATIVE_PATH"; do
  [[ -f "$REPO_ROOT/$required_path" ]] || die "Base offline-TTS repair is missing: $required_path"
done
[[ -f "$REPO_ROOT/app/src/main/java/be/saikono/poincare/data/audio/OfflineDutchTtsEngine.kt" ]] ||
  die "Base offline-TTS engine is missing."
[[ -s "$REPO_ROOT/$MODEL_RELATIVE_DIR/nl_BE-nathalie-medium.onnx" ]] ||
  die "Bundled Nathalie model is missing."
grep -q 'compactFraction' "$REPO_ROOT/$NORMALIZER_RELATIVE_PATH" ||
  die "Pronunciation correction 1.1 is missing; run the previous updater first."
grep -q 'bananen schil' "$REPO_ROOT/$NORMALIZER_RELATIVE_PATH" ||
  die "Bananenschil correction is missing; run the previous updater first."

if grep -q 'missingNumeratorFraction' "$REPO_ROOT/$NORMALIZER_RELATIVE_PATH" &&
   grep -q 'dutchUnitAfterQuantity' "$REPO_ROOT/$NORMALIZER_RELATIVE_PATH" &&
   grep -q 'DUTCH_VOWEL_LABEL_DELAY_MS' "$REPO_ROOT/$NORMALIZER_RELATIVE_PATH" &&
   grep -q 'vowelLabelDelayMillis' "$REPO_ROOT/$AUDIO_SERVICE_RELATIVE_PATH"; then
  log "Pronunciation correction $SCRIPT_VERSION is already installed"
  SUCCESS=1
  exit 0
fi

TEMP_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/pointcare-tts-pronunciation.XXXXXX")
readonly TEMP_ROOT
readonly STAGED_REPO="$TEMP_ROOT/repo"
MODIFIER_FILE="$TEMP_ROOT/apply_nl_be_pronunciation_update.py"
BACKUP_ROOT="$TEMP_ROOT/live-backup"
mkdir -p "$STAGED_REPO" "$BACKUP_ROOT"

cat >"$MODIFIER_FILE" <<'PYTHON_MODIFIER'
from __future__ import annotations

import re
import sys
from pathlib import Path


repo = Path(sys.argv[1])
normalizer_path = repo / "app/src/main/java/be/saikono/poincare/data/audio/SpeechTextNormalizer.kt"
audio_service_path = repo / "app/src/main/java/be/saikono/poincare/data/audio/AudioService.kt"
test_path = repo / "app/src/test/java/be/saikono/poincare/data/audio/SpeechTextNormalizerTest.kt"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write(path: Path, value: str) -> None:
    with path.open("w", encoding="utf-8", newline="\n") as output:
        output.write(value)


def fail(message: str) -> None:
    raise SystemExit(f"Pronunciation source update failed: {message}")


normalizer = read(normalizer_path)
if "compactFraction" not in normalizer or "bananen schil" not in normalizer:
    fail("the installed 1.1 normalizer markers were not found")

if "missingNumeratorFraction" not in normalizer:
    declaration_anchor = re.search(
        r"(?m)^(\s*private val compactFraction\s*=\s*Regex\([^\n]+\)\s*\n)",
        normalizer,
    )
    if declaration_anchor is None:
        fail("compactFraction declaration anchor was not found")

    declarations = r'''    private val missingNumeratorFraction =
        Regex("(?<![\\d/?])\\?\\s*/\\s*(\\d{1,3})(?![\\d/])")
    private val dutchUnitAfterQuantity = Regex(
        """(?i)([0-9]+(?:[.,][0-9]+)?|[?_]+)\s*(km²|hm²|dam²|dm²|cm²|mm²|m²|km³|hm³|dam³|dm³|cm³|mm³|m³|km|hm|dam|dm|cm|mm|kg|hg|dag|dg|cg|mg|gr|g|kl|hl|dal|dl|cl|ml|l|min|sec|s|u|h|°c|%)(?=\z|[\s.,;:!?=])"""
    )

    private val dutchUnitWords = mapOf(
        "km" to "kilometer", "hm" to "hectometer", "dam" to "decameter",
        "m" to "meter", "dm" to "decimeter", "cm" to "centimeter", "mm" to "millimeter",
        "kg" to "kilogram", "hg" to "hectogram", "dag" to "decagram",
        "g" to "gram", "gr" to "gram", "dg" to "decigram", "cg" to "centigram", "mg" to "milligram",
        "kl" to "kiloliter", "hl" to "hectoliter", "dal" to "decaliter",
        "l" to "liter", "dl" to "deciliter", "cl" to "centiliter", "ml" to "milliliter",
        "u" to "uur", "h" to "uur", "°c" to "graden Celsius", "%" to "procent",
        "km²" to "vierkante kilometer", "hm²" to "vierkante hectometer",
        "dam²" to "vierkante decameter", "m²" to "vierkante meter",
        "dm²" to "vierkante decimeter", "cm²" to "vierkante centimeter", "mm²" to "vierkante millimeter",
        "km³" to "kubieke kilometer", "hm³" to "kubieke hectometer",
        "dam³" to "kubieke decameter", "m³" to "kubieke meter",
        "dm³" to "kubieke decimeter", "cm³" to "kubieke centimeter", "mm³" to "kubieke millimeter"
    )

    private val dutchVowelSounds = setOf(
        "a", "e", "i", "o", "u", "y", "aa", "ee", "ie", "oo", "uu", "oe", "eu", "ui",
        "ei", "ij", "au", "ou", "ai", "oi", "aai", "ooi", "oei", "eeuw", "ieuw"
    )
    private const val DUTCH_VOWEL_LABEL_DELAY_MS = 200L
'''
    normalizer = (
        normalizer[: declaration_anchor.end()]
        + declarations
        + normalizer[declaration_anchor.end() :]
    )

if "expandDutchUnits(rawText)" not in normalizer:
    normalize_signature = re.search(
        r"(?m)^([ \t]*)fun\s+normalize\(\s*text\s*:\s*String\s*,\s*locale\s*:\s*String\s*\)\s*:\s*String\s*\{[ \t]*$",
        normalizer,
    )
    if normalize_signature is None:
        fail("normalize(text, locale) function signature was not found")
    signature_text = normalize_signature.group(0)
    renamed_signature = re.sub(
        r"\btext\s*:\s*String",
        "rawText: String",
        signature_text,
        count=1,
    )
    indent = normalize_signature.group(1) + "    "
    input_expansion = (
        "\n"
        + indent
        + 'val text = if (normalizeLocale(locale) == "nl-BE") '
        + "expandDutchUnits(rawText) else rawText"
    )
    normalizer = (
        normalizer[: normalize_signature.start()]
        + renamed_signature
        + input_expansion
        + normalizer[normalize_signature.end() :]
    )

if "value = missingNumeratorFraction.replace" not in normalizer:
    fraction_anchor = (
        '        if (normalizedLocale == "nl-BE") {\n'
        "            value = compactFraction.replace(value) { match ->"
    )
    if fraction_anchor not in normalizer:
        fail("Dutch compact-fraction block anchor was not found")
    missing_fraction_block = (
        '        if (normalizedLocale == "nl-BE") {\n'
        "            value = missingNumeratorFraction.replace(value) { match ->\n"
        "                val denominator = match.groupValues[1].toIntOrNull()\n"
        "                val denominatorWord = denominator?.let(dutchDenominators::get)\n"
        "                    ?: return@replace match.value\n"
        '                "Hoeveel $denominatorWord"\n'
        "            }\n"
        "            value = compactFraction.replace(value) { match ->"
    )
    normalizer = normalizer.replace(fraction_anchor, missing_fraction_block, 1)

if "fun leadingSilenceMillisFor" not in normalizer:
    helper_anchor = re.search(r"(?m)^    private fun replaceNumericOperator\(", normalizer)
    if helper_anchor is None:
        fail("normalizer helper insertion anchor was not found")
    helpers = r'''    fun leadingSilenceMillisFor(text: String, locale: String): Long {
        if (normalizeLocale(locale) != "nl-BE") return 0L
        val normalized = text.trim().lowercase()
        val sound = if (normalized.startsWith("de klank ")) {
            normalized.removePrefix("de klank ").substringBefore(' ')
        } else {
            normalized
        }
        return if (sound in dutchVowelSounds) DUTCH_VOWEL_LABEL_DELAY_MS else 0L
    }

    private fun expandDutchUnits(value: String): String =
        dutchUnitAfterQuantity.replace(value) { match ->
            val quantity = match.groupValues[1]
            val unit = match.groupValues[2].lowercase()
            val unitWord = when (unit) {
                "min" -> if (quantity.toDoubleOrNull() == 1.0) "minuut" else "minuten"
                "s", "sec" -> if (quantity.toDoubleOrNull() == 1.0) "seconde" else "seconden"
                else -> dutchUnitWords[unit] ?: return@replace match.value
            }
            "$quantity $unitWord"
        }

'''
    normalizer = normalizer[: helper_anchor.start()] + helpers + normalizer[helper_anchor.start() :]

write(normalizer_path, normalizer)


audio_service = read(audio_service_path)
if "offlineDutchTts.speak" not in audio_service:
    fail("the complete-utterance offline engine call was not found in AudioService")

if "vowelLabelDelayMillis" not in audio_service:
    normalized_anchor = re.search(
        r"(?m)^(\s*)val normalizedText = SpeechTextNormalizer\.normalize\(text, runtime\.locale\)\s*$",
        audio_service,
    )
    if normalized_anchor is None:
        fail("normalizedText/runtime.locale anchor was not found in AudioService")
    indent = normalized_anchor.group(1)
    delay_block = (
        normalized_anchor.group(0)
        + "\n"
        + f"{indent}val vowelLabelDelayMillis = SpeechTextNormalizer.leadingSilenceMillisFor(\n"
        + f"{indent}    normalizedText,\n"
        + f"{indent}    runtime.locale\n"
        + f"{indent})\n"
        + f"{indent}if (vowelLabelDelayMillis > 0L) {{\n"
        + f"{indent}    delay(vowelLabelDelayMillis)\n"
        + f"{indent}    if (requestGeneration != playbackGeneration) return@launch\n"
        + f"{indent}}}"
    )
    audio_service = (
        audio_service[: normalized_anchor.start()]
        + delay_block
        + audio_service[normalized_anchor.end() :]
    )
    if "import kotlinx.coroutines.delay" not in audio_service:
        import_anchor = re.search(r"(?m)^import kotlinx\.coroutines\.[^\n]+$", audio_service)
        if import_anchor is None:
            fail("kotlinx.coroutines import anchor was not found")
        audio_service = (
            audio_service[: import_anchor.start()]
            + "import kotlinx.coroutines.delay\n"
            + audio_service[import_anchor.start() :]
        )

write(audio_service_path, audio_service)


tests = read(test_path)
if "missing numerator fraction becomes a Dutch question" not in tests:
    class_end = tests.rfind("\n}")
    if class_end < 0:
        fail("test-class closing brace was not found")
    added_tests = r'''

    @Test
    fun `missing numerator fraction becomes a Dutch question`() {
        assertEquals(
            "Hoeveel achtste",
            SpeechTextNormalizer.normalize("?/8", "nl-BE")
        )
    }

    @Test
    fun `Dutch measurement abbreviations become spoken units`() {
        assertEquals(
            "3 meter = centimeter; 2 kilogram = gram; 500 milliliter = liter; " +
                "4 minuten; 1 minuut; 6 vierkante meter; 20 gram",
            SpeechTextNormalizer.normalize(
                "3 m = ___ cm; 2 kg = ___ gr; 500 ml = ___ l; " +
                    "4 min; 1 min; 6 m²; 20 g",
                "nl-BE"
            )
        )
    }

    @Test
    fun `only Dutch vowel labels receive leading silence`() {
        val vowelLabel = SpeechTextNormalizer.normalize("eu", "nl-BE")
        val consonantLabel = SpeechTextNormalizer.normalize("m", "nl-BE")

        assertEquals(200L, SpeechTextNormalizer.leadingSilenceMillisFor(vowelLabel, "nl-BE"))
        assertEquals(0L, SpeechTextNormalizer.leadingSilenceMillisFor(consonantLabel, "nl-BE"))
        assertEquals(0L, SpeechTextNormalizer.leadingSilenceMillisFor(vowelLabel, "fr-BE"))
    }
'''
    tests = tests[:class_end] + added_tests + tests[class_end:]

write(test_path, tests)
PYTHON_MODIFIER

git -C "$REPO_ROOT" archive --format=tar HEAD | tar -xf - -C "$STAGED_REPO"
for root_build_file in settings.gradle.kts build.gradle.kts gradle.properties; do
  if [[ -f "$REPO_ROOT/$root_build_file" ]]; then
    cp "$REPO_ROOT/$root_build_file" "$STAGED_REPO/$root_build_file"
  fi
done
rm -rf "$STAGED_REPO/app"
(
  cd "$REPO_ROOT"
  tar \
    --exclude='app/build' \
    --exclude='app/.cxx' \
    --exclude='app/.gradle' \
    -cf - app
) | tar -xf - -C "$STAGED_REPO"
if [[ -f "$REPO_ROOT/local.properties" ]]; then
  cp "$REPO_ROOT/local.properties" "$STAGED_REPO/local.properties"
elif [[ $SKIP_BUILD -eq 0 && -z ${ANDROID_HOME:-} && -z ${ANDROID_SDK_ROOT:-} ]]; then
  die "Android SDK not configured. Add local.properties or set ANDROID_HOME."
fi

run_modifier "$STAGED_REPO"
log "Staged live app includes the fraction, unit, and vowel-label corrections"

if [[ $SKIP_BUILD -eq 0 ]]; then
  log "Running focused normalizer tests and assembling the complete debug APK"
  run_gradle_checks "$STAGED_REPO"
  log "Staged Gradle checks passed"
else
  log "WARNING: --skip-build selected; Gradle verification has not been performed"
fi

for relative_path in \
  "$NORMALIZER_RELATIVE_PATH" \
  "$AUDIO_SERVICE_RELATIVE_PATH" \
  "$TEST_RELATIVE_PATH"; do
  mkdir -p "$BACKUP_ROOT/$(dirname "$relative_path")"
  cp "$REPO_ROOT/$relative_path" "$BACKUP_ROOT/$relative_path"
done

LIVE_APPLIED=1
run_modifier "$REPO_ROOT"

grep -q 'missingNumeratorFraction' "$REPO_ROOT/$NORMALIZER_RELATIVE_PATH" ||
  die "Missing-numerator fraction rule was not activated."
grep -q 'dutchUnitAfterQuantity' "$REPO_ROOT/$NORMALIZER_RELATIVE_PATH" ||
  die "Dutch measurement-unit rules were not activated."
grep -q 'DUTCH_VOWEL_LABEL_DELAY_MS = 200L' "$REPO_ROOT/$NORMALIZER_RELATIVE_PATH" ||
  die "The 200 ms vowel-label delay was not activated."
grep -q 'vowelLabelDelayMillis' "$REPO_ROOT/$AUDIO_SERVICE_RELATIVE_PATH" ||
  die "AudioService did not activate the vowel-label delay."

SUCCESS=1
log "Pronunciation correction $SCRIPT_VERSION installed successfully"
printf '\n'
printf 'Rebuild and reinstall with:\n'
printf '  .\\gradlew.bat :app:assembleDebug :app:installDebug\n'
printf '\n'
printf 'Retest in airplane mode: ?/8, 20 gr, 3 m, 6 m², 4 min, and vowel label eu.\n'
