import hashlib
import json
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
IMAGE_DIR = ROOT / "story_import" / "generated" / "robbie" / "l1_m3" / "images" / "final"
COVER_PATH = ROOT / "story_import" / "generated" / "robbie" / "l1_m3" / "cover" / "final" / "robbie_l1_m3_cover.png"
REPORT_PATH = ROOT / "story_import" / "generated" / "robbie" / "phase5" / "01_L1_M3_IMAGE_QA.json"


def inspect_png(path: Path, expected_size: tuple[int, int]) -> dict:
    failures: list[str] = []
    if not path.exists():
        return {"path": str(path), "failures": ["missing"]}
    if path.stat().st_size == 0:
        return {"path": str(path), "failures": ["empty file"]}

    with Image.open(path) as image:
        image.load()
        if image.format != "PNG":
            failures.append(f"format={image.format}")
        if image.size != expected_size:
            failures.append(f"size={image.size}")
        if image.mode != "P":
            failures.append(f"mode={image.mode}")
        transparency = image.info.get("transparency")
        rgba = image.convert("RGBA")
        alpha = rgba.getchannel("A")
        alpha_extrema = alpha.getextrema()
        if alpha_extrema[0] != 0:
            failures.append("no fully transparent pixels")
        if alpha_extrema[1] != 255:
            failures.append("no fully opaque pixels")
        corners = [alpha.getpixel((0, 0)), alpha.getpixel((rgba.width - 1, 0)), alpha.getpixel((0, rgba.height - 1)), alpha.getpixel((rgba.width - 1, rgba.height - 1))]
        if any(corner != 0 for corner in corners):
            failures.append(f"opaque corner={corners}")
        colors = image.getcolors(maxcolors=1_000_000) or []
        if len(colors) > 256:
            failures.append(f"colors={len(colors)}")

        chroma_pixels = 0
        visible_pixels = 0
        for red, green, blue, a in rgba.getdata():
            if a > 16:
                visible_pixels += 1
                if red >= 245 and green <= 20 and blue >= 245:
                    chroma_pixels += 1
        chroma_ratio = chroma_pixels / visible_pixels if visible_pixels else 1.0
        if chroma_ratio > 0.0001:
            failures.append(f"chroma_ratio={chroma_ratio:.6f}")

        return {
            "path": str(path),
            "bytes": path.stat().st_size,
            "size": list(image.size),
            "mode": image.mode,
            "colors": len(colors),
            "transparencyIndex": transparency if isinstance(transparency, int) else None,
            "alphaExtrema": list(alpha_extrema),
            "chromaPixels": chroma_pixels,
            "chromaRatio": round(chroma_ratio, 8),
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "failures": failures,
        }


def main() -> None:
    expected = [IMAGE_DIR / f"robbie_l1_m3_p{page:03d}.png" for page in range(1, 151)]
    unexpected = sorted(path.name for path in IMAGE_DIR.glob("*.png") if path not in expected)
    inspected = [inspect_png(path, (1536, 1024)) for path in expected]
    cover = inspect_png(COVER_PATH, (1024, 1536))
    hashes = [item.get("sha256") for item in inspected if item.get("sha256")]
    duplicates = sorted({value for value in hashes if hashes.count(value) > 1})
    failures = []
    for page, item in enumerate(inspected, start=1):
        failures.extend(f"page {page}: {failure}" for failure in item["failures"])
    failures.extend(f"cover: {failure}" for failure in cover["failures"])
    if unexpected:
        failures.append(f"unexpected final PNG files: {unexpected}")
    if duplicates:
        failures.append(f"duplicate hashes: {duplicates}")

    report = {
        "passed": not failures,
        "bookId": "L1_M3",
        "interiorCount": len([item for item in inspected if not item["failures"]]),
        "expectedInteriorCount": 150,
        "coverCount": 1 if not cover["failures"] else 0,
        "uniqueInteriorHashes": len(set(hashes)),
        "totalInteriorBytes": sum(item.get("bytes", 0) for item in inspected),
        "coverBytes": cover.get("bytes", 0),
        "unexpectedFiles": unexpected,
        "failures": failures,
        "interiors": inspected,
        "cover": cover,
    }
    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    REPORT_PATH.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key not in {"interiors", "cover"}}, ensure_ascii=False, indent=2))
    if failures:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
