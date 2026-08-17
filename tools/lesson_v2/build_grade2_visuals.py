"""Build transparent, lesson-specific Grade 2 theory visuals."""

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


def visible(value: dict | None, locale: str = "nl-BE") -> str:
    return str(((value or {}).get("visible") or {}).get(locale) or "").strip()


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


def content(lesson: dict) -> tuple[str, list[str], str, bool]:
    theory = next(stage for stage in lesson["stages"] if stage["type"] == "THEORY")
    words = next(block for block in theory["contentBlocks"] if block["type"] == "VOCABULARY")
    example = next(block for block in theory["contentBlocks"] if block["type"] == "WORKED_EXAMPLE")
    terms = [visible(term.get("term")) for term in words.get("terms", [])][:4]
    return visible(lesson["title"]), terms, visible(example.get("body")), lesson["legacyRouting"]["domain"] == "WORLD_KNOWLEDGE"


def svg_text(x: int, y: int, lines: list[str], colour: str, size: int, weight: int = 400) -> str:
    spans = "".join(
        f'<tspan x="{x}" dy="{0 if index == 0 else size + 8}">{html.escape(line)}</tspan>'
        for index, line in enumerate(lines)
    )
    return f'<text x="{x}" y="{y}" fill="{colour}" font-family="Segoe UI, Arial, sans-serif" font-size="{size}" font-weight="{weight}">{spans}</text>'


def build_svg(path: Path, lesson: dict) -> None:
    title, terms, example, world = content(lesson)
    accent = WHITE_HEX if world else BLUE_HEX
    pieces = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{HEIGHT}" viewBox="0 0 {WIDTH} {HEIGHT}">']
    pieces.append(svg_text(42, 56, wrap(title, 38, 2), GOLD_HEX, 37, 600))
    positions = [(100, 180), (300, 300), (500, 180), (700, 300)]
    for index, ((x, y), term) in enumerate(zip(positions, terms)):
        colour = GOLD_HEX if index % 2 == 0 else accent
        pieces.append(f'<circle cx="{x}" cy="{y}" r="62" fill="none" stroke="{colour}" stroke-width="8"/>')
        pieces.append(svg_text(x - 74, y + 94, wrap(term, 14, 2), colour, 23, 600))
        if index < len(terms) - 1:
            nx, ny = positions[index + 1]
            pieces.append(f'<path d="M{x + 63} {y} C{x + 100} {y}, {nx - 100} {ny}, {nx - 63} {ny}" fill="none" stroke="{WHITE_HEX}" stroke-width="7" stroke-linecap="round"/>')
    pieces.append(f'<rect x="82" y="410" width="796" height="92" rx="28" fill="none" stroke="{accent}" stroke-width="7"/>')
    pieces.append(svg_text(112, 446, wrap(example, 62, 2), WHITE_HEX, 22, 400))
    pieces.append("</svg>")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(pieces) + "\n", encoding="utf-8")


def draw_text(draw, xy, lines, colour, text_font, gap=7):
    x, y = xy
    for line in lines:
        draw.text((x, y), line, fill=colour, font=text_font)
        box = draw.textbbox((x, y), line, font=text_font)
        y += box[3] - box[1] + gap


def build_png(path: Path, lesson: dict) -> None:
    title, terms, example, world = content(lesson)
    accent = WHITE if world else BLUE
    image = Image.new("RGBA", (WIDTH, HEIGHT), TRANSPARENT)
    draw = ImageDraw.Draw(image)
    draw_text(draw, (42, 28), wrap(title, 38, 2), GOLD, font(37, True), 4)
    positions = [(100, 180), (300, 300), (500, 180), (700, 300)]
    for index, ((x, y), term) in enumerate(zip(positions, terms)):
        colour = GOLD if index % 2 == 0 else accent
        draw.ellipse((x - 62, y - 62, x + 62, y + 62), outline=colour, width=8)
        draw_text(draw, (x - 74, y + 76), wrap(term, 14, 2), colour, font(23, True), 4)
        if index < len(terms) - 1:
            nx, ny = positions[index + 1]
            draw.line((x + 63, y, nx - 63, ny), fill=WHITE, width=7)
    draw.rounded_rectangle((82, 410, 878, 502), radius=28, outline=accent, width=7)
    draw_text(draw, (112, 427), wrap(example, 62, 2), WHITE, font(22), 5)
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)


def main() -> None:
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    lessons = [
        lesson for lesson in catalog["lessons"]
        if lesson["grade"] == 2 and lesson["id"] != "lesson.g2.world.nature.seasons.order-and-signs"
    ]
    for lesson in lessons:
        for visual in lesson.get("visuals", []):
            svg_path = visual.get("assetPathSvg")
            png_path = visual.get("assetPathPng")
            if svg_path and svg_path.startswith("poincare/visuals/grade2/"):
                build_svg(ASSET_ROOT / svg_path, lesson)
            if png_path and png_path.startswith("poincare/visuals/grade2/"):
                build_png(ASSET_ROOT / png_path, lesson)
    print(f"Prepared lesson-specific visual assets for {len(lessons)} Grade 2 lessons.")


if __name__ == "__main__":
    main()
