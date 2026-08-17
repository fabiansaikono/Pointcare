"""Normalize and validate the canonical Robbie reference PNG assets."""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
REFERENCE_DIR = ROOT / "story_import" / "generated" / "robbie" / "references" / "final"
REPORT_PATH = ROOT / "story_import" / "generated" / "robbie" / "phase2" / "02_REFERENCE_QA.json"

ASSETS = {
    "videogame_style_reference.png": {"size": (1536, 1024), "kind": "scene"},
    "robbie_character_reference.png": {"size": (1024, 1024), "kind": "character"},
    "fien_character_reference.png": {"size": (1024, 1024), "kind": "character"},
    "mats_character_reference.png": {"size": (1024, 1024), "kind": "character"},
    "kroon_character_reference.png": {"size": (1024, 1024), "kind": "character"},
    "robbie_cast_reference.png": {"size": (1536, 1024), "kind": "lineup"},
}


def normalize_character(source: Image.Image, target_size: tuple[int, int]) -> Image.Image:
    rgba = source.convert("RGBA")
    alpha = rgba.getchannel("A")
    bbox = alpha.getbbox()
    if bbox is None:
        raise ValueError("character image is fully transparent")

    subject = rgba.crop(bbox)
    max_width = target_size[0] - 96
    max_height = target_size[1] - 64
    scale = min(max_width / subject.width, max_height / subject.height)
    new_size = (
        max(1, round(subject.width * scale)),
        max(1, round(subject.height * scale)),
    )
    subject = subject.resize(new_size, Image.Resampling.NEAREST)

    canvas = Image.new("RGBA", target_size, (0, 0, 0, 0))
    x = (target_size[0] - subject.width) // 2
    y = (target_size[1] - subject.height) // 2
    canvas.alpha_composite(subject, (x, y))
    return canvas


def normalize_scene(source: Image.Image, target_size: tuple[int, int]) -> Image.Image:
    rgba = source.convert("RGBA")
    if rgba.size != target_size:
        rgba = rgba.resize(target_size, Image.Resampling.NEAREST)
    return rgba


def quantize_rgba(source: Image.Image) -> Image.Image:
    return source.quantize(
        colors=256,
        method=Image.Quantize.FASTOCTREE,
        dither=Image.Dither.NONE,
    )


def validate(path: Path, expected_size: tuple[int, int]) -> dict[str, object]:
    with Image.open(path) as image:
        rgba = image.convert("RGBA")
        colors = rgba.getcolors(maxcolors=1_000_000)
        alpha = rgba.getchannel("A")
        bbox = alpha.getbbox()
        width, height = rgba.size
        corners = [
            rgba.getpixel((0, 0))[3],
            rgba.getpixel((width - 1, 0))[3],
            rgba.getpixel((0, height - 1))[3],
            rgba.getpixel((width - 1, height - 1))[3],
        ]
        transparent_pixels = sum(count for count, color in (colors or []) if color[3] == 0)
        total = width * height
        unique_colors = len(colors) if colors is not None else None
        checks = {
            "expectedDimensions": rgba.size == expected_size,
            "paletteAtMost256": unique_colors is not None and unique_colors <= 256,
            "hasTransparency": alpha.getextrema()[0] == 0,
            "transparentCorners": all(value == 0 for value in corners),
            "hasVisibleSubject": bbox is not None,
        }
        return {
            "file": path.name,
            "mode": image.mode,
            "dimensions": [width, height],
            "uniqueRgbaColors": unique_colors,
            "transparentFraction": round(transparent_pixels / total, 4),
            "visibleBounds": list(bbox) if bbox else None,
            "checks": checks,
            "passed": all(checks.values()),
        }


def main() -> None:
    results: list[dict[str, object]] = []
    for filename, spec in ASSETS.items():
        path = REFERENCE_DIR / filename
        if not path.exists():
            results.append({"file": filename, "passed": False, "error": "missing"})
            continue

        with Image.open(path) as original:
            if spec["kind"] == "character":
                normalized = normalize_character(original, spec["size"])
            else:
                normalized = normalize_scene(original, spec["size"])
            quantized = quantize_rgba(normalized)
            temp_path = path.with_suffix(".normalized.png")
            quantized.save(temp_path, format="PNG", optimize=True)
        temp_path.replace(path)
        results.append(validate(path, spec["size"]))

    report = {
        "assetCount": len(results),
        "passed": all(item.get("passed") is True for item in results),
        "requirements": {
            "characterDimensions": [1024, 1024],
            "sceneAndLineupDimensions": [1536, 1024],
            "maxPaletteColors": 256,
            "transparentBackground": True,
        },
        "assets": results,
    }
    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    REPORT_PATH.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))
    if not report["passed"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
