"""Build the deterministic PNG fallback for the original Poincare seasons SVG."""

from pathlib import Path
from PIL import Image, ImageDraw


SIZE = 512
GOLD = (255, 215, 0, 255)
WHITE = (255, 255, 255, 255)
TRANSPARENT = (0, 0, 0, 0)


def main() -> None:
    output = Path("app/src/main/assets/poincare/visuals/world/seasons-cycle-v1.png")
    output.parent.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGBA", (SIZE, SIZE), TRANSPARENT)
    draw = ImageDraw.Draw(image)

    draw.ellipse((48, 48, 464, 464), outline=GOLD, width=16)
    for points in (
        ((438, 218), (464, 256), (426, 282)),
        ((294, 438), (256, 464), (230, 426)),
        ((74, 294), (48, 256), (86, 230)),
        ((218, 74), (256, 48), (282, 86)),
    ):
        draw.line(points, fill=GOLD, width=16, joint="curve")

    draw.line((256, 154, 256, 222), fill=WHITE, width=12)
    draw.polygon(((256, 190), (222, 188), (198, 140), (244, 148)), fill=GOLD, outline=WHITE)
    draw.polygon(((256, 178), (282, 174), (308, 134), (270, 140)), fill=GOLD, outline=WHITE)

    draw.ellipse((332, 222, 400, 290), fill=GOLD, outline=WHITE, width=12)
    for start, end in (
        ((366, 194), (366, 174)), ((366, 338), (366, 318)),
        ((304, 256), (284, 256)), ((448, 256), (428, 256)),
        ((322, 212), (308, 198)), ((424, 314), (410, 300)),
        ((410, 212), (424, 198)), ((308, 314), (322, 300)),
    ):
        draw.line((*start, *end), fill=WHITE, width=12)

    draw.polygon(((256, 346), (224, 334), (188, 348), (178, 378), (214, 386)), fill=GOLD, outline=WHITE)
    draw.line((256, 346, 212, 376), fill=WHITE, width=12)

    draw.line((146, 220, 146, 292), fill=WHITE, width=12)
    draw.line((110, 241, 182, 271), fill=WHITE, width=12)
    draw.line((110, 271, 182, 241), fill=WHITE, width=12)
    for start, end in (
        ((146, 220), (134, 234)), ((146, 220), (158, 234)),
        ((146, 292), (134, 278)), ((146, 292), (158, 278)),
        ((110, 241), (128, 243)), ((110, 241), (118, 257)),
        ((182, 271), (164, 269)), ((182, 271), (174, 255)),
    ):
        draw.line((*start, *end), fill=WHITE, width=8)

    image.save(output, optimize=True)
    print(output.resolve())


if __name__ == "__main__":
    main()
