import argparse
import math
from pathlib import Path

from PIL import Image, ImageDraw


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input-dir", required=True)
    parser.add_argument("--prefix", required=True)
    parser.add_argument("--pages", required=True, help="Comma-separated page numbers")
    parser.add_argument("--out", required=True)
    parser.add_argument("--columns", type=int, default=4)
    parser.add_argument("--thumb-width", type=int, default=384)
    args = parser.parse_args()

    pages = [int(value.strip()) for value in args.pages.split(",") if value.strip()]
    thumb_h = round(args.thumb_width * 2 / 3)
    label_h = 28
    rows = math.ceil(len(pages) / args.columns)
    sheet = Image.new("RGB", (args.columns * args.thumb_width, rows * (thumb_h + label_h)), "#20242c")
    draw = ImageDraw.Draw(sheet)

    for index, page in enumerate(pages):
        path = Path(args.input_dir) / f"{args.prefix}{page:03d}.png"
        image = Image.open(path).convert("RGBA")
        checker = Image.new("RGB", image.size, "#d8d8d8")
        checker_draw = ImageDraw.Draw(checker)
        block = max(16, image.width // 32)
        for y in range(0, image.height, block):
            for x in range(0, image.width, block):
                if (x // block + y // block) % 2:
                    checker_draw.rectangle((x, y, x + block - 1, y + block - 1), fill="#eeeeee")
        checker.paste(image, mask=image.getchannel("A"))
        checker.thumbnail((args.thumb_width, thumb_h), Image.Resampling.NEAREST)
        x = (index % args.columns) * args.thumb_width
        y = (index // args.columns) * (thumb_h + label_h)
        sheet.paste(checker, (x, y))
        draw.rectangle((x, y + thumb_h, x + args.thumb_width, y + thumb_h + label_h), fill="#11151b")
        draw.text((x + 10, y + thumb_h + 6), f"pagina {page}", fill="white")

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out, "PNG")
    print(f"Wrote {out} size={sheet.size}")


if __name__ == "__main__":
    main()
