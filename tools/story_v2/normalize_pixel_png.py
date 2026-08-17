"""Normalize transparent pixel-art PNGs to a fixed canvas and PNG-8 palette."""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--width", type=int, required=True)
    parser.add_argument("--height", type=int, required=True)
    parser.add_argument("--fit-subject", action="store_true")
    parser.add_argument("--padding", type=int, default=32)
    parser.add_argument("--colors", type=int, default=256)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    source_path = Path(args.input)
    output_path = Path(args.out)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with Image.open(source_path) as image:
        rgba = image.convert("RGBA")
        if args.fit_subject:
            bbox = rgba.getchannel("A").getbbox()
            if bbox is None:
                raise SystemExit("Input is fully transparent")
            subject = rgba.crop(bbox)
            max_width = args.width - (2 * args.padding)
            max_height = args.height - (2 * args.padding)
            scale = min(max_width / subject.width, max_height / subject.height)
            subject = subject.resize(
                (max(1, round(subject.width * scale)), max(1, round(subject.height * scale))),
                Image.Resampling.NEAREST,
            )
            rgba = Image.new("RGBA", (args.width, args.height), (0, 0, 0, 0))
            rgba.alpha_composite(subject, ((args.width - subject.width) // 2, (args.height - subject.height) // 2))
        elif rgba.size != (args.width, args.height):
            rgba = rgba.resize((args.width, args.height), Image.Resampling.NEAREST)

        quantized = rgba.quantize(
            colors=args.colors,
            method=Image.Quantize.FASTOCTREE,
            dither=Image.Dither.NONE,
        )
        quantized.save(output_path, format="PNG", optimize=True)

    with Image.open(output_path) as result:
        check = result.convert("RGBA")
        corners = [
            check.getpixel((0, 0))[3],
            check.getpixel((args.width - 1, 0))[3],
            check.getpixel((0, args.height - 1))[3],
            check.getpixel((args.width - 1, args.height - 1))[3],
        ]
        unique = check.getcolors(maxcolors=1_000_000)
        if result.size != (args.width, args.height):
            raise SystemExit("Unexpected output dimensions")
        if not all(alpha == 0 for alpha in corners):
            raise SystemExit("Output corners are not transparent")
        if unique is None or len(unique) > args.colors:
            raise SystemExit("Output exceeds palette limit")
        print(f"Wrote {output_path} size={result.size} mode={result.mode} colors={len(unique)}")


if __name__ == "__main__":
    main()
