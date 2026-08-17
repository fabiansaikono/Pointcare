"""Build lesson-specific, transparent theory diagrams for the Grade 1 catalogue."""

from __future__ import annotations

import html
import json
import sys
import textwrap
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "app/src/main/assets/poincare/curriculum/lesson-catalog-v2.json"
ASSET_ROOT = ROOT / "app/src/main/assets"
WIDTH = 960
HEIGHT = 540
GOLD_HEX = "#FFD700"
WHITE_HEX = "#FFFFFF"
BLUE_HEX = "#4FC3F7"
GOLD = (255, 215, 0, 255)
WHITE = (255, 255, 255, 255)
BLUE = (79, 195, 247, 255)
TRANSPARENT = (0, 0, 0, 0)

LIVING_CONCEPT_VISUALS = {
    "levend": "Een plant leeft en groeit",
    "niet-levend": "Een steen leeft niet",
    "ooit-levend": "Hout kwam van een levende boom",
}


def visible(value: dict | None, locale: str = "nl-BE") -> str:
    return str(((value or {}).get("visible") or {}).get(locale) or "").strip()


def wrapped(value: str, width: int, maximum: int) -> list[str]:
    lines = textwrap.wrap(value, width=width, break_long_words=False, break_on_hyphens=False)
    if len(lines) <= maximum:
        return lines
    result = lines[:maximum]
    result[-1] = result[-1].rstrip(" .") + "…"
    return result


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = [
        Path("C:/Windows/Fonts/seguisb.ttf" if bold else "C:/Windows/Fonts/segoeui.ttf"),
        Path("C:/Windows/Fonts/arialbd.ttf" if bold else "C:/Windows/Fonts/arial.ttf"),
    ]
    for candidate in candidates:
        if candidate.is_file():
            return ImageFont.truetype(str(candidate), size)
    return ImageFont.load_default()


def lesson_content(lesson: dict) -> tuple[str, str, list[str], str]:
    title = visible(lesson.get("title"))
    theory = next(stage for stage in lesson["stages"] if stage["type"] == "THEORY")
    blocks = theory.get("contentBlocks", [])
    focus = visible(lesson.get("summary"))
    terms: list[str] = []
    examples: list[str] = []
    for block in blocks:
        if block.get("type") == "VOCABULARY":
            terms.extend(visible(term.get("term")) for term in block.get("terms", []))
        if block.get("type") == "WORKED_EXAMPLE":
            examples.append(visible(block.get("body")))
    terms = [term for term in terms if term][:3]
    while len(terms) < 3:
        terms.append(("stap", "model", "controle")[len(terms)])
    example = next((value for value in examples if value), focus)
    return title, focus, terms, example


def svg_text(x: int, y: int, lines: list[str], colour: str, size: int, weight: int = 400, gap: int = 10) -> str:
    spans = []
    for index, line in enumerate(lines):
        dy = 0 if index == 0 else size + gap
        spans.append(f'<tspan x="{x}" dy="{dy}">{html.escape(line)}</tspan>')
    return (
        f'<text x="{x}" y="{y}" fill="{colour}" font-family="Segoe UI, Arial, sans-serif" '
        f'font-size="{size}" font-weight="{weight}">' + "".join(spans) + "</text>"
    )


def build_svg(path: Path, lesson: dict) -> None:
    title, focus, terms, example = lesson_content(lesson)
    world = lesson["legacyRouting"]["domain"] == "WORLD_KNOWLEDGE"
    accent = GOLD_HEX if world else BLUE_HEX
    title_lines = wrapped(title, 38, 2)
    focus_lines = wrapped(focus, 40, 3)
    example_lines = wrapped(example, 40, 3)
    pieces = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{HEIGHT}" viewBox="0 0 {WIDTH} {HEIGHT}">',
        '<g fill="none" stroke-linecap="round" stroke-linejoin="round">',
        f'<path d="M302 202 H350 M302 302 H350 M302 402 H350" stroke="{GOLD_HEX}" stroke-width="8"/>',
        f'<path d="M338 190 L352 202 L338 214 M338 290 L352 302 L338 314 M338 390 L352 402 L338 414" stroke="{GOLD_HEX}" stroke-width="8"/>',
        f'<rect x="360" y="146" width="560" height="340" rx="34" stroke="{accent}" stroke-width="8"/>',
    ]
    for y in (160, 260, 360):
        pieces.append(f'<rect x="40" y="{y}" width="262" height="84" rx="28" stroke="{WHITE_HEX}" stroke-width="6"/>')
    pieces.append('</g>')
    pieces.append(svg_text(40, 54, title_lines, GOLD_HEX, 38, 600, 7))
    for index, (term, y) in enumerate(zip(terms, (160, 260, 360)), start=1):
        pieces.append(svg_text(66, y + 52, [f"{index}. {term}"], accent, 27, 600))
    pieces.append(svg_text(395, 194, ["Zo werkt het"], GOLD_HEX, 27, 600))
    pieces.append(svg_text(395, 235, focus_lines, WHITE_HEX, 24, 400, 7))
    example_y = 235 + len(focus_lines) * 31 + 28
    pieces.append(svg_text(395, example_y, ["Voorbeeld"], accent, 25, 600))
    pieces.append(svg_text(395, example_y + 38, example_lines, WHITE_HEX, 23, 400, 7))
    pieces.append("</svg>")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(pieces) + "\n", encoding="utf-8")


def draw_lines(draw: ImageDraw.ImageDraw, xy: tuple[int, int], lines: list[str], colour: tuple[int, int, int, int], text_font, gap: int = 8) -> int:
    x, y = xy
    for line in lines:
        draw.text((x, y), line, fill=colour, font=text_font)
        box = draw.textbbox((x, y), line, font=text_font)
        y += (box[3] - box[1]) + gap
    return y


def build_png(path: Path, lesson: dict) -> None:
    title, focus, terms, example = lesson_content(lesson)
    world = lesson["legacyRouting"]["domain"] == "WORLD_KNOWLEDGE"
    accent = GOLD if world else BLUE
    image = Image.new("RGBA", (WIDTH, HEIGHT), TRANSPARENT)
    draw = ImageDraw.Draw(image)
    title_font = font(38, bold=True)
    term_font = font(27, bold=True)
    heading_font = font(27, bold=True)
    subheading_font = font(25, bold=True)
    body_font = font(23)

    draw_lines(draw, (40, 38), wrapped(title, 38, 2), GOLD, title_font, 4)
    for index, (term, y) in enumerate(zip(terms, (160, 260, 360)), start=1):
        draw.rounded_rectangle((40, y, 302, y + 84), radius=28, outline=WHITE, width=6)
        draw.text((66, y + 22), f"{index}. {term}", fill=accent, font=term_font)
        draw.line((302, y + 42, 350, y + 42), fill=GOLD, width=8)
        draw.line((338, y + 30, 352, y + 42, 338, y + 54), fill=GOLD, width=8, joint="curve")

    draw.rounded_rectangle((360, 146, 920, 486), radius=34, outline=accent, width=8)
    draw.text((395, 170), "Zo werkt het", fill=GOLD, font=heading_font)
    focus_lines = wrapped(focus, 40, 3)
    y = draw_lines(draw, (395, 218), focus_lines, WHITE, body_font, 7) + 14
    draw.text((395, y), "Voorbeeld", fill=accent, font=subheading_font)
    y += 38
    draw_lines(draw, (395, y), wrapped(example, 40, 3), WHITE, body_font, 7)
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)


def build_addition_example_two(path: Path) -> None:
    size = 1280
    image = Image.new("RGBA", (size, size), TRANSPARENT)
    draw = ImageDraw.Draw(image)
    radius = 54
    top_y = 310
    gold_x = (130, 280, 430, 580)
    blue_x = (850, 1000, 1150)
    for x in gold_x:
        draw.ellipse((x - radius, top_y - radius, x + radius, top_y + radius), fill=GOLD)
    for x in blue_x:
        draw.ellipse((x - radius, top_y - radius, x + radius, top_y + radius), fill=BLUE)
    draw.line((690, top_y, 780, top_y), fill=WHITE, width=22)
    draw.line((735, top_y - 45, 735, top_y + 45), fill=WHITE, width=22)
    draw.line((640, 430, 640, 545), fill=WHITE, width=24)
    draw.line((600, 505, 640, 545, 680, 505), fill=WHITE, width=24, joint="curve")

    left, top, right, bottom = 165, 635, 1115, 1015
    draw.rounded_rectangle((left, top, right, bottom), radius=26, outline=WHITE, width=16)
    cell_width = (right - left) / 5
    row_height = (bottom - top) / 2
    for column in range(1, 5):
        x = int(left + column * cell_width)
        draw.line((x, top, x, bottom), fill=WHITE, width=12)
    draw.line((left, int(top + row_height), right, int(top + row_height)), fill=WHITE, width=12)
    counter_colours = [GOLD, GOLD, GOLD, GOLD, BLUE, BLUE, BLUE]
    for index, colour in enumerate(counter_colours):
        row = index // 5
        column = index % 5
        cx = int(left + (column + 0.5) * cell_width)
        cy = int(top + (row + 0.5) * row_height)
        draw.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=colour)
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)


def build_living_concept_svg(path: Path, concept: str) -> None:
    pieces = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{HEIGHT}" viewBox="0 0 {WIDTH} {HEIGHT}" role="img">',
        f'<title>{html.escape(LIVING_CONCEPT_VISUALS[concept])}</title>',
        f'<g fill="none" stroke-linecap="round" stroke-linejoin="round" stroke-width="18">',
    ]
    if concept == "levend":
        pieces.extend([
            f'<path d="M480 460 V270" stroke="{WHITE_HEX}"/>',
            f'<path d="M480 330 C390 320 335 270 330 190 C415 195 465 240 480 330 Z" stroke="{GOLD_HEX}"/>',
            f'<path d="M480 285 C550 275 610 230 625 155 C545 155 500 205 480 285 Z" stroke="{GOLD_HEX}"/>',
            f'<path d="M330 465 C390 415 570 415 630 465" stroke="{WHITE_HEX}"/>',
            f'<path d="M220 160 C220 120 270 95 270 55 C270 95 320 120 320 160 C320 215 220 215 220 160 Z" stroke="{GOLD_HEX}"/>',
            f'<circle cx="740" cy="125" r="58" stroke="{WHITE_HEX}"/>',
            f'<path d="M740 35 V10 M740 240 V215 M650 125 H625 M855 125 H830 M675 60 L655 40 M825 210 L805 190 M805 60 L825 40 M655 210 L675 190" stroke="{WHITE_HEX}"/>',
        ])
    elif concept == "niet-levend":
        pieces.extend([
            f'<path d="M250 415 L325 210 L465 135 L635 195 L720 415 Z" stroke="{GOLD_HEX}"/>',
            f'<path d="M325 210 L455 275 L635 195 M455 275 L500 415" stroke="{WHITE_HEX}"/>',
            f'<path d="M180 455 H790" stroke="{WHITE_HEX}"/>',
            f'<path d="M200 90 L240 130 M240 90 L200 130 M750 90 L790 130 M790 90 L750 130" stroke="{WHITE_HEX}"/>',
        ])
    else:
        pieces.extend([
            f'<path d="M235 450 V220 M235 245 C175 235 130 190 125 125 C190 125 230 165 235 245 Z M235 205 C295 200 340 155 345 95 C285 95 245 140 235 205 Z" stroke="{GOLD_HEX}"/>',
            f'<path d="M125 450 C175 410 295 410 345 450" stroke="{WHITE_HEX}"/>',
            f'<path d="M390 270 H540 M505 235 L540 270 L505 305" stroke="{WHITE_HEX}"/>',
            f'<path d="M610 235 H780 V335 H610 Z M635 335 V455 M755 335 V455 M610 285 H545 V390 H610" stroke="{GOLD_HEX}"/>',
        ])
    pieces.extend(["</g>", "</svg>"])
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(pieces) + "\n", encoding="utf-8")


def build_living_concept_png(path: Path, concept: str) -> None:
    image = Image.new("RGBA", (WIDTH, HEIGHT), TRANSPARENT)
    draw = ImageDraw.Draw(image)
    stroke = 18
    if concept == "levend":
        draw.line((480, 460, 480, 270), fill=WHITE, width=stroke)
        draw.ellipse((330, 185, 485, 335), outline=GOLD, width=stroke)
        draw.ellipse((475, 150, 630, 290), outline=GOLD, width=stroke)
        draw.arc((330, 395, 630, 535), 200, 340, fill=WHITE, width=stroke)
        draw.line((220, 160, 270, 55, 320, 160), fill=GOLD, width=stroke, joint="curve")
        draw.ellipse((682, 67, 798, 183), outline=WHITE, width=stroke)
        for line in [((740, 35), (740, 10)), ((740, 240), (740, 215)), ((650, 125), (625, 125)), ((855, 125), (830, 125))]:
            draw.line(line, fill=WHITE, width=stroke)
    elif concept == "niet-levend":
        draw.line((250, 415, 325, 210, 465, 135, 635, 195, 720, 415, 250, 415), fill=GOLD, width=stroke, joint="curve")
        draw.line((325, 210, 455, 275, 635, 195), fill=WHITE, width=stroke, joint="curve")
        draw.line((455, 275, 500, 415), fill=WHITE, width=stroke)
        draw.line((180, 455, 790, 455), fill=WHITE, width=stroke)
    else:
        draw.line((235, 450, 235, 220), fill=GOLD, width=stroke)
        draw.ellipse((125, 125, 240, 245), outline=GOLD, width=stroke)
        draw.ellipse((230, 95, 345, 210), outline=GOLD, width=stroke)
        draw.arc((125, 395, 345, 510), 200, 340, fill=WHITE, width=stroke)
        draw.line((390, 270, 540, 270, 505, 235), fill=WHITE, width=stroke, joint="curve")
        draw.line((540, 270, 505, 305), fill=WHITE, width=stroke)
        draw.rectangle((610, 235, 780, 335), outline=GOLD, width=stroke)
        draw.line((635, 335, 635, 455), fill=GOLD, width=stroke)
        draw.line((755, 335, 755, 455), fill=GOLD, width=stroke)
        draw.line((610, 285, 545, 285, 545, 390, 610, 390), fill=GOLD, width=stroke, joint="curve")
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)


def build_living_concept_visuals() -> None:
    for concept in LIVING_CONCEPT_VISUALS:
        stem = f"science-tech.organisms.{concept}"
        build_living_concept_svg(ASSET_ROOT / f"poincare/visuals/grade1/{stem}.svg", concept)
        build_living_concept_png(ASSET_ROOT / f"poincare/visuals/grade1/{stem}.png", concept)


def main() -> None:
    if "--living-only" in sys.argv:
        build_living_concept_visuals()
        print("Prepared three child-friendly living-concept visual pairs.")
        return
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    lessons = [lesson for lesson in catalog["lessons"] if lesson["grade"] == 1]
    for lesson in lessons:
        for visual in lesson["visuals"]:
            svg_path = visual.get("assetPathSvg")
            png_path = visual.get("assetPathPng")
            if svg_path and svg_path.startswith("poincare/visuals/grade1/"):
                build_svg(ASSET_ROOT / svg_path, lesson)
            if png_path and png_path.startswith("poincare/visuals/grade1/"):
                build_png(ASSET_ROOT / png_path, lesson)
    build_addition_example_two(
        ASSET_ROOT / "poincare/visuals/theory/addition-within10-example2-v1.png"
    )
    build_living_concept_visuals()
    print(f"Prepared lesson-specific visual assets for {len(lessons)} lessons.")


if __name__ == "__main__":
    main()
