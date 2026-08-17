"""Generate transparent tri-colour SVG/PNG cards for World Knowledge concepts."""

from __future__ import annotations

import html
import json
import textwrap
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "app/src/main/assets/poincare/curriculum/lesson-catalog-v2.json"
ASSET_ROOT = ROOT / "app/src/main/assets"
WIDTH, HEIGHT = 960, 540
GOLD_HEX, WHITE_HEX, BLUE_HEX = "#FFD700", "#FFFFFF", "#4FC3F7"
GOLD, WHITE, BLUE, TRANSPARENT = (255, 215, 0, 255), (255, 255, 255, 255), (79, 195, 247, 255), (0, 0, 0, 0)


def visible(value: dict | None) -> str:
    return str(((value or {}).get("visible") or {}).get("nl-BE") or "").strip()


def wrap(value: str, width: int, maximum: int) -> list[str]:
    lines = textwrap.wrap(value, width=width, break_long_words=False, break_on_hyphens=False)
    if len(lines) <= maximum:
        return lines
    lines = lines[:maximum]
    lines[-1] = lines[-1].rstrip(" .") + "…"
    return lines


def font(size: int, bold: bool = False):
    for candidate in (
        Path("C:/Windows/Fonts/seguisb.ttf" if bold else "C:/Windows/Fonts/segoeui.ttf"),
        Path("C:/Windows/Fonts/arialbd.ttf" if bold else "C:/Windows/Fonts/arial.ttf"),
    ):
        if candidate.is_file():
            return ImageFont.truetype(str(candidate), size)
    return ImageFont.load_default()


def concept_content(lesson: dict, visual_id: str) -> tuple[str, str]:
    theory = next(stage for stage in lesson["stages"] if stage["type"] == "THEORY")
    block = next(
        block for block in theory.get("contentBlocks", [])
        if visual_id in block.get("visualIds", []) and block.get("type") == "CHILD_FRIENDLY_DEFINITION"
    )
    return visible(block.get("title")), visible(block.get("body"))


def svg_text(x: int, y: int, lines: list[str], colour: str, size: int, weight: int = 400) -> str:
    spans = "".join(
        f'<tspan x="{x}" dy="{0 if index == 0 else size + 10}">{html.escape(line)}</tspan>'
        for index, line in enumerate(lines)
    )
    return (
        f'<text x="{x}" y="{y}" fill="{colour}" font-family="Segoe UI, Arial, sans-serif" '
        f'font-size="{size}" font-weight="{weight}">{spans}</text>'
    )


def build_svg(path: Path, title: str, body: str) -> None:
    pieces = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{HEIGHT}" viewBox="0 0 {WIDTH} {HEIGHT}" role="img">',
        f'<title>{html.escape(title)}</title>',
        '<g fill="none" stroke-linecap="round" stroke-linejoin="round">',
        f'<circle cx="185" cy="270" r="118" stroke="{GOLD_HEX}" stroke-width="14"/>',
        f'<path d="M145 270 L178 304 L237 226" stroke="{WHITE_HEX}" stroke-width="22"/>',
        f'<path d="M330 270 H392 M370 244 L398 270 L370 296" stroke="{BLUE_HEX}" stroke-width="12"/>',
        f'<rect x="410" y="100" width="500" height="340" rx="42" stroke="{BLUE_HEX}" stroke-width="10"/>',
        '</g>',
        svg_text(452, 174, wrap(title, 24, 2), GOLD_HEX, 42, 700),
        svg_text(452, 275, wrap(body, 38, 4), WHITE_HEX, 28, 400),
        '</svg>',
    ]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(pieces) + "\n", encoding="utf-8")


def draw_text(draw: ImageDraw.ImageDraw, x: int, y: int, lines: list[str], colour, text_font, gap: int = 9) -> None:
    for line in lines:
        draw.text((x, y), line, fill=colour, font=text_font)
        bounds = draw.textbbox((x, y), line, font=text_font)
        y += bounds[3] - bounds[1] + gap


def build_png(path: Path, title: str, body: str) -> None:
    image = Image.new("RGBA", (WIDTH, HEIGHT), TRANSPARENT)
    draw = ImageDraw.Draw(image)
    draw.ellipse((67, 152, 303, 388), outline=GOLD, width=14)
    draw.line((145, 270, 178, 304, 237, 226), fill=WHITE, width=22, joint="curve")
    draw.line((330, 270, 392, 270), fill=BLUE, width=12)
    draw.line((370, 244, 398, 270, 370, 296), fill=BLUE, width=12, joint="curve")
    draw.rounded_rectangle((410, 100, 910, 440), radius=42, outline=BLUE, width=10)
    draw_text(draw, 452, 135, wrap(title, 24, 2), GOLD, font(42, True))
    draw_text(draw, 452, 245, wrap(body, 38, 4), WHITE, font(28))
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)


def main() -> None:
    index = json.loads(CATALOG.read_text(encoding="utf-8"))
    lessons = list(index.get("lessons", []))
    for shard in index.get("shards", []):
        shard_path = CATALOG.parent / str(shard["file"])
        lessons.extend(json.loads(shard_path.read_text(encoding="utf-8")).get("lessons", []))
    generated = 0
    for lesson in lessons:
        if lesson.get("legacyRouting", {}).get("domain") != "WORLD_KNOWLEDGE" or lesson.get("grade") not in (1, 2):
            continue
        for visual in lesson.get("visuals", []):
            svg_path = str(visual.get("assetPathSvg") or "")
            png_path = str(visual.get("assetPathPng") or "")
            if "/concepts/" not in svg_path or "/concepts/" not in png_path:
                continue
            title, body = concept_content(lesson, visual["id"])
            build_svg(ASSET_ROOT / svg_path, title, body)
            build_png(ASSET_ROOT / png_path, title, body)
            generated += 1
    print(f"Prepared {generated} World Knowledge concept visual pairs.")


if __name__ == "__main__":
    main()
