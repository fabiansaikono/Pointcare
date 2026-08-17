import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ROBBIE = ROOT / "story_import" / "generated" / "robbie"
BOOKS = ("l3_m5", "l3_e5", "l4_m6", "l4_e6")

files = []
for slug in BOOKS:
    files.extend((ROBBIE / slug / "images" / "final").glob("*.png"))
    files.extend((ROBBIE / slug / "cover" / "final").glob("*.png"))

bad_corners = []
magenta_residue = []
for path in files:
    image = Image.open(path).convert("RGBA")
    alpha = image.getchannel("A")
    corners = (
        alpha.getpixel((0, 0)),
        alpha.getpixel((image.width - 1, 0)),
        alpha.getpixel((0, image.height - 1)),
        alpha.getpixel((image.width - 1, image.height - 1)),
    )
    if any(value != 0 for value in corners):
        bad_corners.append(path.relative_to(ROOT).as_posix())
    residue = sum(
        1
        for red, green, blue, value in image.get_flattened_data()
        if value > 8 and red > 235 and green < 35 and blue > 210
    )
    if residue:
        magenta_residue.append({"path": path.relative_to(ROOT).as_posix(), "pixels": residue})

result = {
    "passed": not bad_corners and not magenta_residue and len(files) == 104,
    "files": len(files),
    "expectedFiles": 104,
    "transparentCornersPassed": not bad_corners,
    "magentaResiduePassed": not magenta_residue,
    "badCorners": bad_corners,
    "magentaResidue": magenta_residue,
}
report_path = ROBBIE / "06_L3_L4_IMAGE_ALPHA_QA.json"
report_path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(result)
if not result["passed"]:
    raise SystemExit(1)
