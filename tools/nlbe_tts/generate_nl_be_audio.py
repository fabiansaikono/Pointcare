#!/usr/bin/env python3
"""Generate deterministic Belgian-Dutch neural speech assets for Poincaré.

The tool runs Piper only on the development computer. The Android app receives
compressed audio assets and a lookup manifest, never the Piper runtime/model.
"""
from __future__ import annotations

import argparse
import dataclasses
import datetime as dt
import hashlib
import html
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import time
import unicodedata
import urllib.request
import venv
import wave
import xml.etree.ElementTree as ET

TOOL_VERSION = "31.5.1"
PIPER_LENGTH_SCALE = 1.18
PIPER_VERSION = "1.4.2"
SOUNDFILE_VERSION = "0.13.1"
NL_BE = "nl-BE"

VOICE_CONFIGS = {
    "rdh": {
        "model": "nl_BE-rdh-medium.onnx",
        "model_url": "https://huggingface.co/rhasspy/piper-voices/resolve/664c651454f055ed34bd83f09e024ffbc0da09ac/nl/nl_BE/rdh/medium/nl_BE-rdh-medium.onnx?download=true",
        "model_md5": "33d3469d745677ec4d7e96eb4145b09e",
        "config_url": "https://huggingface.co/rhasspy/piper-voices/resolve/664c651454f055ed34bd83f09e024ffbc0da09ac/nl/nl_BE/rdh/medium/nl_BE-rdh-medium.onnx.json?download=true",
        "config_md5": "652cc22303033a5ddc3dd12e595cd83e",
    },
    "nathalie": {
        "model": "nl_BE-nathalie-medium.onnx",
        "model_url": "https://huggingface.co/rhasspy/piper-voices/resolve/664c651454f055ed34bd83f09e024ffbc0da09ac/nl/nl_BE/nathalie/medium/nl_BE-nathalie-medium.onnx?download=true",
        "model_md5": "ab0c38b5f66764b59ad9e3e98b1c2172",
        "config_url": "https://huggingface.co/rhasspy/piper-voices/resolve/664c651454f055ed34bd83f09e024ffbc0da09ac/nl/nl_BE/nathalie/medium/nl_BE-nathalie-medium.onnx.json?download=true",
        "config_md5": "ed7e4ab955d9514f4274ad93cafe0618",
    },
}

@dataclasses.dataclass(frozen=True)
class Candidate:
    text: str
    priority: int
    source: str


def project_root(start: Path) -> Path:
    for candidate in [start, *start.parents]:
        if (candidate / "app").is_dir() and ((candidate / "settings.gradle.kts").exists() or (candidate / "settings.gradle").exists()):
            return candidate
    raise SystemExit("Could not find the Pointcare project root.")


def canonical_locale(locale: str) -> str:
    key = locale.strip().replace("_", "-").lower()
    if key in {"nl", "nl-be"}: return NL_BE
    if key in {"fr", "fr-be"}: return "fr-BE"
    if key in {"en", "en-us"}: return "en-US"
    return locale.strip().replace("_", "-")


def runtime_normalize(text: str) -> str:
    text = text.replace("_", " ")
    text = re.sub(r"\b(\d{1,2}):(\d{2})\b", lambda m: m.group(1) if int(m.group(2)) == 0 else f"{int(m.group(1))} {int(m.group(2)):02d}", text)
    text = re.sub(r"\b(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})\b", r"\1 \2 \3", text)
    return text


def normalize_lookup(text: str) -> str:
    text = unicodedata.normalize("NFKC", runtime_normalize(text))
    text = text.replace("’", "'").replace("‘", "'").replace("_", " ")
    return re.sub(r"\s+", " ", text).strip().lower()


def speech_key(locale: str, text: str) -> str:
    payload = f"{canonical_locale(locale)}\n{normalize_lookup(text)}".encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def md5(path: Path) -> str:
    digest = hashlib.md5()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def download(url: str, target: Path, expected_md5: str) -> None:
    if target.exists() and md5(target) == expected_md5:
        return
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(target.suffix + ".download")
    print(f"Downloading {target.name} ...")
    request = urllib.request.Request(url, headers={"User-Agent": "Poincare-NLBE-TTS-v31"})
    try:
        with urllib.request.urlopen(request, timeout=120) as response, temporary.open("wb") as output:
            shutil.copyfileobj(response, output)
    except Exception as exc:
        temporary.unlink(missing_ok=True)
        raise RuntimeError(
            f"Could not download {target.name}. Check the development computer's internet connection and retry."
        ) from exc
    actual = md5(temporary)
    if actual != expected_md5:
        temporary.unlink(missing_ok=True)
        raise RuntimeError(f"Checksum mismatch for {target.name}: expected {expected_md5}, got {actual}")
    temporary.replace(target)


def venv_python(venv_dir: Path) -> Path:
    return venv_dir / ("Scripts/python.exe" if os.name == "nt" else "bin/python")


def bootstrap_and_reexec(args: argparse.Namespace, argv: list[str], root: Path) -> None:
    if not args.bootstrap or os.environ.get("POINCARE_NLBE_TTS_VENV") == "1":
        return
    venv_dir = root / ".poincare_tools" / "nlbe_tts" / "venv"
    python = venv_python(venv_dir)
    if not python.exists():
        print("Creating the isolated NL-BE speech environment ...")
        venv.EnvBuilder(with_pip=True).create(venv_dir)
    marker = venv_dir / f"packages-{PIPER_VERSION}-{SOUNDFILE_VERSION}.ok"
    if not marker.exists():
        subprocess.run([str(python), "-m", "pip", "install", "--disable-pip-version-check", f"piper-tts=={PIPER_VERSION}", f"soundfile=={SOUNDFILE_VERSION}"], check=True)
        marker.write_text("ok\n", encoding="utf-8")
    clean_argv = [item for item in argv if item != "--bootstrap"]
    env = os.environ.copy()
    env["POINCARE_NLBE_TTS_VENV"] = "1"
    raise SystemExit(subprocess.call([str(python), str(Path(__file__).resolve()), *clean_argv], env=env))


def locale_of(element: ET.Element, inherited: str | None = None) -> str | None:
    return element.attrib.get("locale") or inherited


def clean_text(value: str | None) -> str | None:
    if value is None: return None
    value = re.sub(r"\s+", " ", value).strip()
    return value or None


def collect_explicit(root: ET.Element, file_label: str) -> list[Candidate]:
    """Collect explicitly authored speech while respecting inherited locales.

    Many elements inherit their locale from a parent <translation>. Treating an
    unlabelled English child as Dutch would generate the wrong voice and waste
    APK space, so walk to the nearest ancestor carrying a locale.
    """
    items: list[Candidate] = []
    parent_map = {child: parent for parent in root.iter() for child in parent}
    default_locale = canonical_locale(
        root.attrib.get("defaultLocale")
        or root.attrib.get("locale")
        or root.attrib.get("language")
        or NL_BE
    )

    def effective_locale(element: ET.Element) -> str:
        current: ET.Element | None = element
        while current is not None:
            declared = current.attrib.get("locale")
            if declared:
                return canonical_locale(declared)
            current = parent_map.get(current)
        return default_locale

    for element in root.iter():
        locale = effective_locale(element)
        if locale != NL_BE:
            continue
        for attr in ("ttsText", "phoneticText"):
            value = clean_text(element.attrib.get(attr))
            if value:
                items.append(Candidate(value, 0, f"{file_label}:{element.tag}@{attr}"))
        if element.tag == "audio":
            value = clean_text(
                element.attrib.get("ttsText")
                or element.attrib.get("phoneticText")
                or element.attrib.get("text")
                or element.text
            )
            if value:
                items.append(Candidate(value, 0, f"{file_label}:audio"))
        if element.tag == "speak":
            value = clean_text(
                element.attrib.get("ttsText")
                or element.attrib.get("phoneticText")
                or element.text
            )
            if value:
                items.append(Candidate(value, 0, f"{file_label}:speak"))

    # Missing-letter missions display the incomplete spelling visually, but the
    # spoken instruction must say the complete word once and never spell it out.
    for exercise in root.iter("exercise"):
        if exercise.attrib.get("type") != "missing_letter_choice":
            continue
        missing_display = exercise.find("./missingLetterDisplay")
        full_word = clean_text(
            exercise.attrib.get("fullWord")
            or (missing_display.attrib.get("ttsFullWord") if missing_display is not None else None)
            or (missing_display.attrib.get("fullWord") if missing_display is not None else None)
        )
        if full_word:
            items.append(
                Candidate(
                    f"Welke letter ontbreekt? Het woord is {full_word}.",
                    0,
                    f"{file_label}:missing_letter_natural_prompt",
                )
            )
    return items


def collect_language(root: ET.Element, file_label: str, scope: str) -> list[Candidate]:
    if scope == "critical": return []
    items: list[Candidate] = []
    for exercise in root.iter("exercise"):
        subcategory = (exercise.attrib.get("subcategoryId") or exercise.attrib.get("subCategory") or "").lower()
        is_language_focus = any(token in file_label.lower() for token in ("language", "reading", "writing", "listening", "taal", "lezen", "schrijven", "luisteren"))
        if not is_language_focus and scope != "full":
            continue
        for translation in exercise.findall("./translations/translation"):
            if canonical_locale(translation.attrib.get("locale", "")) != NL_BE: continue
            for tag in ("instruction", "question"):
                child = translation.find(tag)
                value = clean_text(child.attrib.get("ttsText") if child is not None else None) or clean_text(child.text if child is not None else None)
                if value: items.append(Candidate(value, 2, f"{file_label}:{subcategory}:{tag}"))
            for answer in translation.findall("./answers/answer"):
                speak = next((s for s in answer.findall("speak") if canonical_locale(s.attrib.get("locale", NL_BE)) == NL_BE), None)
                if speak is not None:
                    value = clean_text(speak.attrib.get("ttsText") or speak.attrib.get("phoneticText") or speak.text)
                else:
                    display = next((d for d in answer.findall("display") if canonical_locale(d.attrib.get("locale", NL_BE)) == NL_BE), None)
                    value = clean_text(display.text if display is not None else answer.attrib.get("display") or answer.attrib.get("value"))
                if value and (scope == "full" or len(value) <= 80):
                    items.append(Candidate(value, 3, f"{file_label}:{subcategory}:answer"))
        for prompt in exercise.findall("./prompts/prompt"):
            if canonical_locale(prompt.attrib.get("locale", "")) != NL_BE: continue
            value = clean_text(prompt.attrib.get("ttsText")) or clean_text(prompt.text)
            if value: items.append(Candidate(value, 2, f"{file_label}:{subcategory}:prompt"))
        # Older/default-NL structures without a translations wrapper.
        if not exercise.findall("./translations/translation"):
            for tag in ("instruction", "question"):
                child = exercise.find(tag)
                value = clean_text(child.attrib.get("ttsText") if child is not None else None) or clean_text("".join(child.itertext()) if child is not None else None)
                if value: items.append(Candidate(value, 2, f"{file_label}:{subcategory}:{tag}"))
        for answer in exercise.findall("./answers/answer"):
            displays = [d for d in answer.findall("display") if canonical_locale(d.attrib.get("locale", NL_BE)) == NL_BE]
            speaks = [s for s in answer.findall("speak") if canonical_locale(s.attrib.get("locale", NL_BE)) == NL_BE]
            value = None
            if speaks:
                value = clean_text(speaks[0].attrib.get("ttsText") or speaks[0].attrib.get("phoneticText") or speaks[0].text)
            elif displays:
                value = clean_text(displays[0].text)
            elif scope == "full" or subcategory in {"listening", "reading", "writing", "schrijven", "lezen"}:
                value = clean_text(answer.attrib.get("display") or answer.attrib.get("value") or answer.text)
            if value and (scope == "full" or len(value) <= 80):
                items.append(Candidate(value, 3, f"{file_label}:{subcategory}:answer"))
    return items



# POINCARE_NLBE_FULL_APP_V31_3_1: begin
def _poincare_is_speakable_text(value: str | None) -> bool:
    value = clean_text(value)
    if not value or len(value) > 220:
        return False
    lowered = value.lower()
    if lowered.startswith(("http://", "https://", "@", "file:", "content:")):
        return False
    if any(token in value for token in ("%1$", "%2$", "${", "{{", "}}")):
        return False
    if re.fullmatch(r"[A-Za-z0-9_.:/\\-]+", value):
        if any(token in value for token in ("_", "/", "\\", "::")):
            return False
        if "." in value and not re.fullmatch(r"\d+(?:[.,]\d+)?", value):
            return False
    return bool(re.search(r"[A-Za-zÀ-ÿ0-9]", value))


def _poincare_effective_locale(
    element: ET.Element,
    parent_map: dict[ET.Element, ET.Element],
    default_locale: str,
) -> str:
    current: ET.Element | None = element
    while current is not None:
        declared = (
            current.attrib.get("locale")
            or current.attrib.get("lang")
            or current.attrib.get("{http://www.w3.org/XML/1998/namespace}lang")
        )
        if declared:
            return canonical_locale(declared)
        current = parent_map.get(current)
    return default_locale


def collect_full_xml(root: ET.Element, file_label: str) -> list[Candidate]:
    # Collect additional visible NL-BE strings for app-wide speech coverage.
    items: list[Candidate] = []
    parent_map = {child: parent for parent in root.iter() for child in parent}
    default_locale = canonical_locale(
        root.attrib.get("defaultLocale")
        or root.attrib.get("locale")
        or root.attrib.get("language")
        or NL_BE
    )
    text_tags = {
        "instruction", "question", "prompt", "display", "speak", "answer",
        "title", "subtitle", "label", "hint", "description", "feedback",
        "success", "error", "message", "name", "text", "word", "sentence",
        "example", "option", "item",
    }
    text_attrs = {
        "ttsText", "phoneticText", "text", "display", "value", "label",
        "title", "subtitle", "instruction", "question", "prompt", "hint",
        "description", "feedback", "message",
    }

    for element in root.iter():
        if _poincare_effective_locale(element, parent_map, default_locale) != NL_BE:
            continue

        tag = element.tag.rsplit("}", 1)[-1]
        for attr in text_attrs:
            value = clean_text(element.attrib.get(attr))
            if _poincare_is_speakable_text(value):
                items.append(Candidate(value, 4, f"{file_label}:{tag}@{attr}"))

        if tag in text_tags:
            value = clean_text(element.text)
            if _poincare_is_speakable_text(value):
                items.append(Candidate(value, 4, f"{file_label}:{tag}"))

    return items


def collect_android_nl_strings(res_dir: Path) -> list[Candidate]:
    # Collect strings from Android values-nl* resource folders.
    items: list[Candidate] = []
    if not res_dir.exists():
        return items

    for values_dir in sorted(res_dir.glob("values-nl*")):
        for xml_file in sorted(values_dir.glob("*.xml")):
            try:
                parsed = ET.parse(xml_file).getroot()
            except ET.ParseError as exc:
                print(f"WARNING: skipping invalid Android resource {xml_file}: {exc}")
                continue
            for element in parsed.iter():
                tag = element.tag.rsplit("}", 1)[-1]
                if tag not in {"string", "item"}:
                    continue
                value = clean_text("".join(element.itertext()))
                if _poincare_is_speakable_text(value):
                    items.append(
                        Candidate(value, 4, f"android:{values_dir.name}/{xml_file.name}")
                    )
    return items


KOTLIN_STRING_RE = re.compile(r'"(?:\\.|[^"\\])*"')
KOTLIN_RUNTIME_TOKEN_RE = re.compile(
    r"[A-Za-zÀ-ÖØ-öø-ÿ]+(?:['’][A-Za-zÀ-ÖØ-öø-ÿ]+)?|\d+(?:[.,]\d+)?|[+×x*÷:/−-]|[%€]"
)
KOTLIN_NL_LITERAL_PATTERNS = (
    # The small curriculum factories store translations as L(nl, en, fr).
    re.compile(r"\bL\s*\(\s*(?P<literal>\"(?:\\.|[^\"\\])*\")", re.DOTALL),
    # Most generated missions use LocalizedText(nl = ..., fr = ..., en = ...).
    re.compile(r"\bLocalizedText\s*\(\s*(?:nl\s*=\s*)?(?P<literal>\"(?:\\.|[^\"\\])*\")", re.DOTALL),
    # Generated prompt builders commonly assign their Dutch sentence to nl.
    re.compile(r"\bnl\s*=\s*(?P<literal>\"(?:\\.|[^\"\\])*\")", re.DOTALL),
)


def _decode_kotlin_literal(literal: str) -> str:
    value = literal[1:-1]
    value = (
        value.replace(r'\"', '"')
        .replace(r'\n', ' ')
        .replace(r'\t', ' ')
        .replace(r'\\', '\\')
    )
    value = re.sub(r"\$\{[^}]*}", " ", value)
    value = re.sub(r"\$[A-Za-z_][A-Za-z0-9_]*", " ", value)
    return clean_text(value)


RUNTIME_NL_PHRASES = {
    "Wat is het meervoud van",
    "Welke letter ontbreekt",
    "Het woord is",
    "Gemengde bewerkingen",
    "Gemengde berekeningen",
    "Bereken",
    "Vermenigvuldig eerst",
    "Een robot heeft",
    "dozen met telkens",
    "Hij gebruikt er",
    "Hoeveel onderdelen blijven over",
}

PLURAL_PROMPT_WORDS = {
    "auto", "baby", "foto", "menu",
    "brief", "duif", "wolf", "golf",
    "huis", "muis", "doos", "reis",
    "kind", "ei", "schip", "stad",
    "kat", "boek", "stoel", "hond",
}


def collect_kotlin_runtime_tokens(java_dir: Path) -> list[Candidate]:
    """Collect Dutch word/number fragments assembled dynamically in Kotlin.

    Only Dutch positions in LocalizedText/L triples and explicit ``nl =`` prompt
    assignments are scanned. Existing fixed arithmetic, number and phonics tokens
    stay mandatory so this remains compatible with the installed full-app TTS set.
    """
    items: list[Candidate] = []
    fixed_tokens = {
        "plus", "min", "keer", "gedeeld door", "komma", "procent", "euro", "eu",
        "nul", "een", "twee", "drie", "vier", "vijf", "zes", "zeven", "acht", "negen", "tien",
        "twintig", "dertig", "veertig", "vijftig", "zestig", "zeventig", "tachtig", "negentig", "honderd", "en",
        *list("abcdefghijklmnopqrstuvwxyz"),
    }
    for token in sorted(fixed_tokens):
        source = "kotlin_runtime_fragment:fixed" if len(token) == 1 and token.isalpha() else "kotlin_runtime_token:fixed"
        items.append(Candidate(token, 0, source))
    for phrase in sorted(RUNTIME_NL_PHRASES):
        items.append(Candidate(phrase, 0, "kotlin_runtime_phrase:fixed"))
    for singular in sorted(PLURAL_PROMPT_WORDS):
        items.append(
            Candidate(
                f"Wat is het meervoud van {singular}?",
                0,
                "kotlin_runtime_phrase:plural",
            )
        )

    if not java_dir.exists():
        return items

    for kotlin_file in sorted(java_dir.rglob("*.kt")):
        try:
            source = kotlin_file.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        if not any(marker in source for marker in ("ExerciseContent", "LocalizedText(", "audioService.speak")):
            continue
        relative = kotlin_file.relative_to(java_dir).as_posix()
        seen_literals: set[str] = set()
        for pattern in KOTLIN_NL_LITERAL_PATTERNS:
            for match in pattern.finditer(source):
                literal = _decode_kotlin_literal(match.group("literal"))
                if not literal or literal in seen_literals:
                    continue
                seen_literals.add(literal)
                for token_match in KOTLIN_RUNTIME_TOKEN_RE.finditer(literal):
                    token = token_match.group(0)
                    replacement = {
                        "+": "plus",
                        "−": "min",
                        "-": "min",
                        "×": "keer",
                        "x": "keer",
                        "*": "keer",
                        "÷": "gedeeld door",
                        ":": "gedeeld door",
                        "/": "gedeeld door",
                        "%": "procent",
                        "€": "euro",
                    }.get(token, token)
                    if replacement and any(character.isalnum() for character in replacement):
                        items.append(Candidate(replacement, 0, f"kotlin_runtime_token:{relative}"))
    return items


def dedupe_candidates(candidates: list[Candidate]) -> list[Candidate]:
    best: dict[str, Candidate] = {}
    for candidate in candidates:
        normalized = normalize_lookup(candidate.text)
        if not normalized:
            continue
        current = best.get(normalized)
        if current is None or candidate.priority < current.priority:
            best[normalized] = candidate
    return sorted(best.values(), key=lambda item: (item.priority, normalize_lookup(item.text)))
# POINCARE_NLBE_FULL_APP_V31_3_1: end

def collect_candidates(content_dir: Path, scope: str, overrides: dict) -> list[Candidate]:
    candidates = [Candidate(key, 0, "pronunciation_override") for key in overrides]
    xml_files = sorted(content_dir.rglob("*.xml"))
    if not xml_files:
        raise RuntimeError(f"No XML content files found in {content_dir}")
    for xml_file in xml_files:
        try:
            parsed = ET.parse(xml_file).getroot()
        except ET.ParseError as exc:
            print(f"WARNING: skipping invalid XML {xml_file.name}: {exc}")
            continue
        candidates.extend(collect_explicit(parsed, xml_file.name))
        candidates.extend(collect_language(parsed, xml_file.name, scope))
        if scope == "full":
            candidates.extend(collect_full_xml(parsed, xml_file.name))
    return dedupe_candidates(candidates)


def load_overrides(path: Path) -> dict[str, dict]:
    data = json.loads(path.read_text(encoding="utf-8"))
    return {normalize_lookup(key): value for key, value in data.get("entries", {}).items()}


def synthesize_to_file(
    voice,
    synthesis_text: str,
    destination: Path,
    temp_dir: Path,
    speed: float,
    compact_fragment: bool = False,
) -> str:
    from piper import SynthesisConfig
    wav_path = temp_dir / (destination.stem + ".wav")
    config = SynthesisConfig(length_scale=speed, noise_scale=0.667, noise_w_scale=0.8, normalize_audio=True)
    with wave.open(str(wav_path), "wb") as wav_file:
        voice.synthesize_wav(synthesis_text, wav_file, syn_config=config)
    destination.parent.mkdir(parents=True, exist_ok=True)
    try:
        import numpy as np
        import soundfile as sf
        audio, sample_rate = sf.read(str(wav_path), dtype="float32", always_2d=False)
        if getattr(audio, "ndim", 1) > 1:
            audio = audio.mean(axis=1)
        threshold = 0.004
        active = np.flatnonzero(np.abs(audio) > threshold)
        if active.size:
            padding = int(sample_rate * 0.055)
            start = max(0, int(active[0]) - padding)
            end = min(len(audio), int(active[-1]) + padding)
            audio = audio[start:end]
        # Full utterances retain calm padding. Runtime fragments use short padding
        # so sequential Nathalie playback does not sound unnaturally interrupted.
        leading_ms = 35 if compact_fragment else 120
        trailing_ms = 60 if compact_fragment else 250
        leading = np.zeros(int(sample_rate * leading_ms / 1000.0), dtype=audio.dtype)
        trailing = np.zeros(int(sample_rate * trailing_ms / 1000.0), dtype=audio.dtype)
        audio = np.concatenate((leading, audio, trailing))
        sf.write(str(destination), audio, sample_rate, format="OGG", subtype="VORBIS")
        return destination.suffix
    except Exception as exc:
        wav_destination = destination.with_suffix(".wav")
        shutil.copy2(wav_path, wav_destination)
        print(f"WARNING: OGG conversion failed ({exc}); kept WAV for {destination.stem}")
        return ".wav"


def write_preview(root: Path, manifest: dict, override_keys: list[str]) -> None:
    preview_dir = root / "build" / "nlbe_tts_preview"
    preview_dir.mkdir(parents=True, exist_ok=True)
    rows = []
    for text in override_keys:
        key = speech_key(NL_BE, text)
        entry = manifest["entries"].get(key)
        if not entry: continue
        source = root / "app" / "src" / "main" / "assets" / entry["asset"]
        preview_name = f"{normalize_lookup(text).replace(' ', '_')}{source.suffix}"
        shutil.copy2(source, preview_dir / preview_name)
        rows.append(f"<tr><td>{html.escape(text)}</td><td>{html.escape(entry.get('example',''))}</td><td><audio controls src='{html.escape(preview_name)}'></audio></td></tr>")
    page = "<!doctype html><meta charset='utf-8'><title>Poincaré NL-BE pronunciation preview</title><style>body{font-family:sans-serif;max-width:900px;margin:30px auto}td,th{padding:8px;border-bottom:1px solid #ccc}table{border-collapse:collapse;width:100%}</style><h1>NL-BE pronunciation preview</h1><table><tr><th>Text</th><th>Example</th><th>Audio</th></tr>" + "".join(rows) + "</table>"
    (preview_dir / "index.html").write_text(page, encoding="utf-8")


def validate(root: Path, required: list[Candidate] | None = None) -> int:
    manifest_path = root / "app/src/main/assets/tts/nl-BE/manifest.json"
    if not manifest_path.exists():
        print("ERROR: neural speech manifest does not exist")
        return 1
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    entries = manifest.get("entries", {})
    missing_assets = []
    for key, entry in entries.items():
        asset = root / "app/src/main/assets" / entry.get("asset", "")
        if not asset.is_file() or asset.stat().st_size < 256:
            missing_assets.append(entry.get("asset", key))
    missing_required = []
    if required:
        for candidate in required:
            if speech_key(NL_BE, candidate.text) not in entries:
                missing_required.append(candidate.text)
    print(f"Validated {len(entries)} NL-BE neural speech entries.")
    if missing_assets:
        print(f"ERROR: {len(missing_assets)} manifest assets are missing or empty.")
    if missing_required:
        print(f"ERROR: {len(missing_required)} required utterances are missing from the manifest.")
    return 1 if missing_assets or missing_required else 0



def _transaction_paths(root: Path) -> dict[str, Path]:
    locale_root = root / "app/src/main/assets/tts/nl-BE"
    return {
        "locale_root": locale_root,
        "live_audio": locale_root / "audio",
        "live_manifest": locale_root / "manifest.json",
        "stage_audio": locale_root / ".poincare_audio_stage",
        "stage_manifest": locale_root / ".poincare_manifest_stage.json",
        "previous_audio": locale_root / ".poincare_audio_previous",
        "previous_manifest": locale_root / ".poincare_manifest_previous.json",
        "marker": locale_root / ".poincare_tts_transaction.json",
    }


def _make_writable(path: Path) -> None:
    try:
        path.chmod(0o700 if path.is_dir() else 0o600)
    except Exception:
        pass


def _remove_path(path: Path, attempts: int = 10) -> None:
    if not path.exists():
        return
    last_error: Exception | None = None
    for attempt in range(1, attempts + 1):
        try:
            if path.is_dir():
                shutil.rmtree(
                    path,
                    onerror=lambda function, target, _exc: (
                        _make_writable(Path(target)),
                        function(target),
                    ),
                )
            else:
                _make_writable(path)
                path.unlink()
            if not path.exists():
                return
        except Exception as error:
            last_error = error
        time.sleep(min(0.5 * attempt, 3.0))
    raise RuntimeError(f"Could not remove {path}: {last_error}")


def _replace_with_retries(source: Path, target: Path, attempts: int = 10) -> None:
    last_error: Exception | None = None
    for attempt in range(1, attempts + 1):
        try:
            os.replace(source, target)
            return
        except Exception as error:
            last_error = error
        time.sleep(min(0.5 * attempt, 3.0))
    raise RuntimeError(f"Could not replace {target} with {source}: {last_error}")


def _stage_asset_path(stage_audio: Path, asset_value: str) -> Path:
    normalized = asset_value.replace("\\", "/")
    prefix = "tts/nl-BE/audio/"
    if not normalized.startswith(prefix):
        raise RuntimeError(f"Unexpected Nathalie asset path in manifest: {asset_value}")
    relative = normalized[len(prefix):]
    if not relative or relative.startswith("../") or "/../" in relative:
        raise RuntimeError(f"Unsafe Nathalie asset path in manifest: {asset_value}")
    return stage_audio / Path(relative)


def _link_or_copy(source: Path, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        return
    try:
        os.link(source, target)
    except Exception:
        shutil.copy2(source, target)


def rollback_tts_transaction(root: Path) -> None:
    paths = _transaction_paths(root)
    marker = paths["marker"]
    previous_audio = paths["previous_audio"]
    previous_manifest = paths["previous_manifest"]
    live_audio = paths["live_audio"]
    live_manifest = paths["live_manifest"]

    # A stage that was never swapped is always disposable.
    _remove_path(paths["stage_audio"])
    _remove_path(paths["stage_manifest"])

    if marker.exists() or previous_audio.exists() or previous_manifest.exists():
        if previous_audio.exists():
            _remove_path(live_audio)
            _replace_with_retries(previous_audio, live_audio)
        if previous_manifest.exists():
            _remove_path(live_manifest)
            _replace_with_retries(previous_manifest, live_manifest)
        elif marker.exists():
            # The old project had no manifest.
            _remove_path(live_manifest)

    _remove_path(marker)


def commit_tts_transaction(root: Path) -> None:
    paths = _transaction_paths(root)
    _remove_path(paths["previous_audio"])
    _remove_path(paths["previous_manifest"])
    _remove_path(paths["stage_audio"])
    _remove_path(paths["stage_manifest"])
    _remove_path(paths["marker"])


def _activate_staged_tts(root: Path, manifest_existed: bool, audio_existed: bool) -> None:
    paths = _transaction_paths(root)
    locale_root = paths["locale_root"]
    locale_root.mkdir(parents=True, exist_ok=True)

    if paths["marker"].exists() or paths["previous_audio"].exists() or paths["previous_manifest"].exists():
        raise RuntimeError(
            "A previous Nathalie transaction is still pending. Run the generator with "
            "--rollback-transaction before starting another generation."
        )

    paths["marker"].write_text(
        json.dumps(
            {
                "manifestExisted": manifest_existed,
                "audioExisted": audio_existed,
                "createdAt": dt.datetime.now(dt.timezone.utc).isoformat(),
            },
            indent=2,
        ),
        encoding="utf-8",
    )

    try:
        if audio_existed:
            _replace_with_retries(paths["live_audio"], paths["previous_audio"])
        if manifest_existed:
            _replace_with_retries(paths["live_manifest"], paths["previous_manifest"])

        _replace_with_retries(paths["stage_audio"], paths["live_audio"])
        _replace_with_retries(paths["stage_manifest"], paths["live_manifest"])
    except Exception:
        rollback_tts_transaction(root)
        raise


def _validate_staged_entries(stage_audio: Path, entries: dict[str, dict]) -> int:
    total_bytes = 0
    missing: list[str] = []
    for key, entry in entries.items():
        asset_value = str(entry.get("asset", ""))
        try:
            asset = _stage_asset_path(stage_audio, asset_value)
        except Exception:
            missing.append(f"{key}: invalid asset path {asset_value!r}")
            continue
        if not asset.is_file():
            missing.append(f"{key}: {asset_value}")
            continue
        total_bytes += asset.stat().st_size

    if missing:
        sample = "\n".join(f"  - {item}" for item in missing[:20])
        suffix = "" if len(missing) <= 20 else f"\n  ... and {len(missing) - 20} more"
        raise RuntimeError(
            "The staged Nathalie manifest references missing audio files:\n"
            + sample
            + suffix
        )
    return total_bytes


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-root", type=Path)
    parser.add_argument("--bootstrap", action="store_true", help="Create/use an isolated venv and install Piper automatically")
    parser.add_argument("--voice", choices=sorted(VOICE_CONFIGS), default="nathalie")
    parser.add_argument("--scope", choices=["critical", "language", "full", "essential"], default="full")
    parser.add_argument("--max-items", type=int, default=20000, help="0 means unlimited; explicit phonics/ttsText entries are never dropped")
    parser.add_argument("--speed", type=float, default=PIPER_LENGTH_SCALE, help="Piper length scale; above 1 is slower")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--keep-stale", action="store_true")
    parser.add_argument("--validate-only", action="store_true")
    parser.add_argument("--transactional", action="store_true", help="Generate into a staged directory and atomically activate after validation")
    parser.add_argument("--commit-transaction", action="store_true", help="Delete the previous Nathalie asset snapshot after all project validation succeeds")
    parser.add_argument("--rollback-transaction", action="store_true", help="Restore the previous Nathalie asset snapshot and remove staged output")
    args = parser.parse_args(argv)
    root = args.project_root.resolve() if args.project_root else project_root(Path.cwd().resolve())
    if args.rollback_transaction:
        rollback_tts_transaction(root)
        print("Nathalie transaction rolled back or staging cleaned.")
        return 0
    if args.commit_transaction:
        commit_tts_transaction(root)
        print("Nathalie transaction committed.")
        return 0
    bootstrap_and_reexec(args, list(argv if argv is not None else sys.argv[1:]), root)

    tool_dir = root / "tools/nlbe_tts"
    overrides_path = tool_dir / "pronunciation_overrides.json"
    overrides = load_overrides(overrides_path)
    scope = "language" if args.scope == "essential" else args.scope
    candidates = collect_candidates(root / "app/src/main/assets/poincare/content", scope, overrides)
    if scope in {"critical", "full"}:
        candidates = dedupe_candidates([
            *candidates,
            *collect_kotlin_runtime_tokens(root / "app/src/main/java"),
        ])
    if scope == "full":
        candidates = dedupe_candidates([
            *candidates,
            *collect_android_nl_strings(root / "app/src/main/res"),
        ])
    mandatory = [item for item in candidates if item.priority == 0]
    optional = [item for item in candidates if item.priority != 0]
    if args.max_items > 0 and len(candidates) > args.max_items:
        optional = optional[:max(0, args.max_items - len(mandatory))]
        candidates = mandatory + optional
    print(f"Found {len(candidates)} unique NL-BE utterances ({len(mandatory)} explicit/critical).")

    if args.validate_only:
        return validate(root, mandatory)

    try:
        from piper import PiperVoice
    except ImportError:
        raise SystemExit("Piper is not installed. Run with --bootstrap.")

    voice_info = VOICE_CONFIGS[args.voice]
    voice_dir = root / ".poincare_tools/nlbe_tts/voices"
    model_path = voice_dir / voice_info["model"]
    config_path = voice_dir / f"{voice_info['model']}.json"
    download(voice_info["model_url"], model_path, voice_info["model_md5"])
    download(voice_info["config_url"], config_path, voice_info["config_md5"])
    print(f"Loading {model_path.name} ...")
    voice = PiperVoice.load(str(model_path))

    assets_root = root / "app/src/main/assets"
    paths = _transaction_paths(root)
    output_dir = paths["live_audio"]
    manifest_path = paths["live_manifest"]
    stage_audio = paths["stage_audio"]
    stage_manifest = paths["stage_manifest"]

    # Generation is always staged. --transactional remains an explicit contract flag,
    # but even direct/manual runs use the safe path.
    rollback_tts_transaction(root)
    paths["locale_root"].mkdir(parents=True, exist_ok=True)
    stage_audio.mkdir(parents=True, exist_ok=False)

    manifest_existed = manifest_path.is_file()
    audio_existed = output_dir.is_dir()
    old_manifest = (
        json.loads(manifest_path.read_text(encoding="utf-8"))
        if manifest_existed
        else {"entries": {}}
    )
    old_entries = old_manifest.get("entries", {})
    entries: dict[str, dict] = {}
    dropped_missing_stale = 0

    # Preserve only stale entries that still point to a real file. Missing entries are
    # deliberately discarded instead of poisoning the new manifest.
    if args.keep_stale:
        for key, old in old_entries.items():
            asset_value = str(old.get("asset", ""))
            old_asset = assets_root / asset_value
            if not old_asset.is_file():
                dropped_missing_stale += 1
                continue
            stage_target = _stage_asset_path(stage_audio, asset_value)
            _link_or_copy(old_asset, stage_target)
            entries[key] = old

    generated = skipped = 0
    try:
        with tempfile.TemporaryDirectory(prefix="poincare_nlbe_tts_") as temp:
            temp_dir = Path(temp)
            for index, candidate in enumerate(candidates, 1):
                normalized = normalize_lookup(candidate.text)
                key = speech_key(NL_BE, candidate.text)
                override = overrides.get(normalized, {})
                item_speed = float(override.get("lengthScale", args.speed))
                synthesis_text = override.get("synthesisText", runtime_normalize(candidate.text))
                compact_fragment = candidate.source.startswith("kotlin_runtime_fragment:")
                synthesis_hash_input = voice_info["model"] + "\n" + synthesis_text + f"\n{item_speed}"
                if compact_fragment:
                    synthesis_hash_input += "\ncompact-fragment-v3"
                synthesis_hash = hashlib.sha256(synthesis_hash_input.encode("utf-8")).hexdigest()
                old = old_entries.get(key, {})
                old_asset = assets_root / str(old.get("asset", ""))

                if (
                    not args.force
                    and old.get("synthesisHash") == synthesis_hash
                    and old_asset.is_file()
                ):
                    stage_target = _stage_asset_path(stage_audio, str(old["asset"]))
                    _link_or_copy(old_asset, stage_target)
                    entries[key] = old
                    skipped += 1
                    continue

                destination = stage_audio / f"{key}.ogg"
                suffix = synthesize_to_file(
                    voice,
                    synthesis_text,
                    destination,
                    temp_dir,
                    item_speed,
                    compact_fragment=compact_fragment,
                )
                actual_destination = destination.with_suffix(suffix)
                asset_path = f"tts/nl-BE/audio/{actual_destination.name}"
                entries[key] = {
                    "asset": asset_path,
                    "lookupText": candidate.text,
                    "normalizedText": normalized,
                    "synthesisText": synthesis_text,
                    "synthesisHash": synthesis_hash,
                    "source": candidate.source,
                    "priority": candidate.priority,
                    "example": override.get("example", ""),
                }
                generated += 1
                if generated == 1 or generated % 50 == 0:
                    print(f"Generated {generated}; processed {index}/{len(candidates)}")

        # Remove unreferenced stage files before activation.
        referenced_stage = {
            _stage_asset_path(stage_audio, str(entry["asset"])).resolve()
            for entry in entries.values()
        }
        for staged_asset in stage_audio.rglob("*"):
            if staged_asset.is_file() and staged_asset.resolve() not in referenced_stage:
                staged_asset.unlink()

        manifest = {
            "version": 1,
            "toolVersion": TOOL_VERSION,
            "locale": NL_BE,
            "voice": voice_info["model"].removesuffix(".onnx"),
            "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
            "scope": scope,
            "entryCount": len(entries),
            "entries": dict(sorted(entries.items())),
        }

        asset_bytes = _validate_staged_entries(stage_audio, entries)
        stage_manifest.write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )

        report = {
            "generated": generated,
            "reused": skipped,
            "droppedMissingStale": dropped_missing_stale,
            "total": len(entries),
            "assetBytes": asset_bytes,
            "voice": manifest["voice"],
            "scope": scope,
        }
        report_path = root / "build/nlbe_tts_report.json"
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")

        _activate_staged_tts(root, manifest_existed, audio_existed)

        # The preview reads asset paths from the now-live manifest. It must run only
        # after staged assets are activated. Preview creation is a developer aid and
        # must never invalidate a fully generated and validated speech library.
        try:
            write_preview(root, manifest, list(overrides.keys()))
        except Exception as preview_error:
            print(
                "WARNING: Nathalie pronunciation preview could not be generated: "
                f"{preview_error}"
            )

        print(
            f"NL-BE neural audio staged and activated: {len(entries)} files, "
            f"{asset_bytes / (1024*1024):.1f} MiB"
        )
        if dropped_missing_stale:
            print(
                f"Removed {dropped_missing_stale} stale manifest entries whose audio files "
                "were already missing."
            )
        print(f"Pronunciation preview: {root / 'build/nlbe_tts_preview/index.html'}")

        validation_code = validate(root, mandatory)
        if validation_code:
            rollback_tts_transaction(root)
            return validation_code
        return 0

    except Exception:
        # If activation never happened this only removes the stage. If activation happened,
        # the previous live directory and manifest are restored by atomic directory swaps.
        rollback_tts_transaction(root)
        raise


if __name__ == "__main__":
    raise SystemExit(main())
