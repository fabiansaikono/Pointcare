from __future__ import annotations

import argparse
import hashlib
import json
import random
from pathlib import Path
from typing import Any

from PIL import Image, ImageDraw, ImageOps


ROOT = Path(__file__).resolve().parents[2]
ROBBIE_ROOT = ROOT / "story_import" / "generated" / "robbie"
LIBRARY_ROOT = ROBBIE_ROOT / "hybrid_library"
COMPONENT_ROOT = LIBRARY_ROOT / "components"
LIBRARY_MANIFEST = LIBRARY_ROOT / "library_manifest.json"
LOGICAL_PAGE = (384, 256)
FINAL_PAGE = (1536, 1024)
LOGICAL_COVER = (256, 384)
FINAL_COVER = (1024, 1536)


POSES = {
    "robbie": ["neutral", "point", "card", "walk", "stop", "inspect"],
    "fien": ["neutral", "point", "clipboard", "crouch", "stop", "walk"],
    "mats": ["neutral", "meter", "cable", "wrench", "map", "run"],
    "kroon": ["neutral", "sensor", "point", "transmitter", "folder", "leave"],
    "noor": ["neutral", "diagram", "meter", "repair", "permission", "folder"],
    "aicha": ["neutral", "meter", "plant", "water", "compare", "caution"],
    "sam": ["neutral", "wrench", "lamp", "cable", "point", "approve"],
    "lotte": ["neutral", "plan", "listen", "sign", "map", "evidence"],
}


CHARACTER_ALIASES = {
    "mayor": "lotte",
    "aïcha": "aicha",
    "puk": "puk",
    "crowd": "residents",
    "villagers": "residents",
    "residents": "residents",
    "children": "residents",
    "workers": "residents",
}


RESIDENTS = ["school_child", "cargo_parent", "older_resident", "wheelchair_user", "waste_worker", "care_worker"]


ENVIRONMENT_RULES = [
    (("world_map_room",), "environments/advanced_l3/world_map"),
    (("black_river",), "environments/advanced_l3/black_river"),
    (("forest_gap",), "environments/advanced_l3/forest_gap"),
    (("mine_entry",), "environments/advanced_l3/mine_entry"),
    (("wetland",), "environments/advanced_l3/wetland"),
    (("data_room", "city_energy_room"), "environments/advanced_l3/data_room"),
    (("shrink_lab",), "environments/advanced_l3/shrink_lab"),
    (("greenhouse",), "environments/advanced_l3/greenhouse"),
    (("mine_valley",), "environments/advanced_l3/mine_valley"),
    (("collapsing_field",), "environments/advanced_l3/cracked_field"),
    (("world_control_map",), "environments/advanced_l4/world_control"),
    (("shrink_module_yard",), "environments/advanced_l4/module_yard"),
    (("protected_forest",), "environments/advanced_l4/protected_forest"),
    (("reversible_test_site",), "environments/advanced_l4/reversible_test"),
    (("council_chamber", "windrand_council_site"), "environments/advanced_l4/council_chamber"),
    (("sabotaged_core_room",), "environments/advanced_l4/sabotaged_core"),
    (("windrand_trail",), "environments/advanced_l4/windrand_trail"),
    (("windrand_factory",), "environments/advanced_l4/windrand_factory"),
    (("windrand_forest",), "environments/advanced_l4/forest_sphere"),
    (("small_world_habitat",), "environments/advanced_l4/small_world"),
    (("restoration_site",), "environments/advanced_l4/restoration_site"),
    (("morgenpark",), "environments/advanced_l4/tomorrow_tree"),
    (("network_core_room", "central_network_hall", "hidden_control_room", "locked_city"), "environments/e4/network_center"),
    (("old_workshop_roof",), "environments/e3/workshop"),
    (("city_streets",), "environments/e3/market_square"),
    (("hearing", "civic_hall"), "environments/e3/hearing_hall"),
    (("kroon_hidden", "kroon_remote", "hidden_lab", "remote_lab"), "environments/e3/kroon_lab"),
    (("transport_van", "kroon_van"), "environments/e3/kroon_van"),
    (("market", "square"), "environments/e3/market_square"),
    (("old_shed", "shed"), "environments/e3/old_shed"),
    (("school_street", "school"), "environments/m4/school_street"),
    (("care_center", "care"), "environments/m4/care_center"),
    (("remote_street", "residential", "waste_street"), "environments/m4/remote_street"),
    (("canal_lock", "sluice", "lock"), "environments/m4/canal_lock"),
    (("night_park", "park"), "environments/m4/night_park"),
    (("canal_bridge", "under_bridge"), "environments/m4/canal_bridge"),
    (("source_wall", "source_check"), "environments/e4/source_wall"),
    (("flood_station", "river_station", "flood"), "environments/e4/flood_station"),
    (("privacy", "private_data"), "environments/e4/privacy_alcove"),
    (("monorail", "rail_platform"), "environments/e4/monorail"),
    (("hidden_transmitter", "priority_transmitter"), "environments/e4/hidden_transmitter"),
    (("network", "data_center", "net_center"), "environments/e4/network_center"),
    (("workshop", "learning", "repair_bay"), "environments/e3/workshop"),
]


PROP_RULES = [
    (("portable core", "draagbare kern", "shrinking core", "leerkern"), "environments/advanced_l3/portable_core"),
    (("three key", "drie sleutel", "external stop", "externe stop", "time limit", "tijdslimiet"), "environments/advanced_l3/three_key_console"),
    (("grate", "rooster"), "props/story/garden_grate"),
    (("name", "choice", "choose", "card", "plan"), "props/story/choice_cards"),
    (("receiver", "signal bridge", "blue bridge", "memory bridge"), "props/story/signal_bridge"),
    (("red test", "test light", "testlamp"), "props/story/red_test_lamp"),
    (("learning-copy", "learning copy", "copy card", "blue card"), "props/story/learning_copy"),
    (("city sensor", "sensor box", "meter box"), "props/story/city_sensor"),
    (("city map", "route map", "public map"), "props/story/city_map"),
    (("canal", "lock wheel", "sluice"), "props/story/canal_controls"),
    (("nest", "bird"), "props/story/empty_nest"),
    (("waste", "bin"), "props/story/waste_bin"),
    (("privacy lock", "permission", "consent"), "props/story/privacy_lock"),
    (("message chain", "linked message"), "props/story/message_chain"),
    (("uncertain", "doubt", "not sure"), "props/story/uncertainty_meter"),
    (("cassette", "old image", "new image"), "props/network/old_new_cassette"),
    (("time code", "clock", "date"), "props/network/clock_pair"),
    (("rain", "storm", "sun"), "props/network/rain_sun"),
    (("private", "privacy cabinet"), "props/network/private_cabinet"),
    (("source", "origin"), "props/network/source_card"),
    (("shortened", "copied", "copy trail", "changed"), "props/network/shortened_copy"),
    (("priority", "ranking"), "props/network/priority_arrows"),
    (("public service", "care network", "monorail warning"), "props/network/public_services"),
    (("flood", "overflow"), "props/network/flood_warning"),
    (("two meters", "independent meters", "two sources"), "props/network/safe_meter_pair"),
    (("logbook", "evidence log"), "props/network/public_logbook"),
    (("remote signal", "transmitter", "command"), "props/network/remote_signal"),
]


def component_path(asset_id: str) -> Path:
    return COMPONENT_ROOT / f"{asset_id}.png"


def load_component(asset_id: str) -> Image.Image:
    path = component_path(asset_id)
    if not path.exists():
        raise FileNotFoundError(path)
    return Image.open(path).convert("RGBA")


def resize_to_fit(image: Image.Image, max_width: int, max_height: int) -> Image.Image:
    if image.width == 0 or image.height == 0:
        raise ValueError("empty image")
    scale = min(max_width / image.width, max_height / image.height)
    width = max(1, round(image.width * scale))
    height = max(1, round(image.height * scale))
    return image.resize((width, height), Image.Resampling.NEAREST)


def composite_center(canvas: Image.Image, asset: Image.Image, center_x: int, bottom_y: int) -> tuple[int, int, int, int]:
    x = round(center_x - asset.width / 2)
    y = bottom_y - asset.height
    canvas.alpha_composite(asset, (x, y))
    return (x, y, x + asset.width, y + asset.height)


def choose_environment(location: str, book_slug: str) -> str:
    normalized = location.lower()
    for tokens, asset_id in ENVIRONMENT_RULES:
        if any(token in normalized for token in tokens):
            return asset_id
    if book_slug == "l2_m4":
        return "environments/e3/market_square"
    if book_slug == "l2_e4":
        return "environments/e4/network_center"
    if book_slug in ("l3_m5", "l3_e5"):
        return "environments/advanced_l3/world_map"
    if book_slug in ("l4_m6", "l4_e6"):
        return "environments/advanced_l4/world_control"
    return "environments/e3/workshop"


def combined_page_words(page: dict[str, Any]) -> str:
    parts = [str(page.get("visual", "")), str(page.get("mood", "")), str(page.get("location", ""))]
    parts.extend(str(tag) for tag in page.get("continuityTags", []))
    return " ".join(parts).lower().replace("_", " ")


def choose_pose(character: str, page: dict[str, Any], index: int) -> str:
    options = POSES[character]
    words = combined_page_words(page)
    preferred: dict[str, list[tuple[tuple[str, ...], str]]] = {
        "robbie": [
            (("card", "name", "choice", "plan"), "card"),
            (("point", "asks", "question"), "point"),
            (("walk", "step", "roll", "return", "move"), "walk"),
            (("stop", "refuse", "caution", "command"), "stop"),
            (("receiver", "repair", "inspect", "wire", "device"), "inspect"),
        ],
        "fien": [
            (("card", "note", "report", "evidence"), "clipboard"),
            (("point", "show", "warn"), "point"),
            (("low", "grate", "crouch", "inspect"), "crouch"),
            (("stop", "caution", "refuse"), "stop"),
            (("walk", "run", "leave"), "walk"),
        ],
        "mats": [
            (("meter", "measure", "test"), "meter"),
            (("cable", "wire"), "cable"),
            (("repair", "wrench", "tool"), "wrench"),
            (("map", "plan", "route"), "map"),
            (("run", "rush"), "run"),
        ],
        "kroon": [
            (("sensor", "box"), "sensor"),
            (("point", "order", "claim"), "point"),
            (("transmitter", "command", "signal"), "transmitter"),
            (("file", "folder", "paper", "receipt"), "folder"),
            (("leave", "van", "walk away"), "leave"),
        ],
        "noor": [
            (("diagram", "explain", "plan"), "diagram"),
            (("meter", "measure", "test"), "meter"),
            (("repair", "wire", "bridge", "receiver"), "repair"),
            (("permission", "consent", "ask"), "permission"),
            (("folder", "old order", "paper"), "folder"),
        ],
        "aicha": [
            (("water", "sample", "river"), "water"),
            (("plant", "park", "bird", "nest"), "plant"),
            (("compare", "two", "source"), "compare"),
            (("warn", "caution", "risk"), "caution"),
            (("meter", "measure"), "meter"),
        ],
        "sam": [
            (("cable", "rail", "monorail"), "cable"),
            (("lamp", "warning"), "lamp"),
            (("repair", "wrench"), "wrench"),
            (("point", "route"), "point"),
            (("safe", "approve"), "approve"),
        ],
        "lotte": [
            (("plan", "criteria", "public"), "plan"),
            (("listen", "hearing", "residents"), "listen"),
            (("sign", "report"), "sign"),
            (("map", "route"), "map"),
            (("evidence", "sealed", "box"), "evidence"),
        ],
    }
    for tokens, pose in preferred.get(character, []):
        if any(token in words for token in tokens):
            return pose
    return options[(int(page["pageNumber"]) + index * 2) % len(options)]


def choose_props(page: dict[str, Any], limit: int = 2) -> list[str]:
    words = combined_page_words(page)
    selected: list[str] = []
    for tokens, asset_id in PROP_RULES:
        if any(token in words for token in tokens) and asset_id not in selected:
            selected.append(asset_id)
        if len(selected) >= limit:
            break
    return selected


def character_assets(page: dict[str, Any]) -> list[tuple[str, str]]:
    result: list[tuple[str, str]] = []
    resident_index = int(page["pageNumber"]) % len(RESIDENTS)
    for raw in page.get("characters", []):
        character = CHARACTER_ALIASES.get(str(raw).lower(), str(raw).lower())
        if character == "residents":
            for offset in range(2):
                resident = RESIDENTS[(resident_index + offset * 2) % len(RESIDENTS)]
                result.append(("residents", f"characters/residents/{resident}"))
            continue
        if character == "puk":
            result.append(("puk", "characters/puk/neutral"))
            continue
        if character not in POSES:
            continue
        pose = choose_pose(character, page, len(result))
        result.append((character, f"characters/{character}/{pose}"))
    deduplicated: list[tuple[str, str]] = []
    seen: set[str] = set()
    for character, asset_id in result:
        key = asset_id if character == "residents" else character
        if key in seen:
            continue
        seen.add(key)
        deduplicated.append((character, asset_id))
    return deduplicated[:6]


def mood_color(page: dict[str, Any]) -> tuple[int, int, int, int]:
    mood = str(page.get("mood", "")).lower()
    if any(word in mood for word in ("danger", "tense", "threat", "risk", "conflict")):
        return (234, 91, 91, 210)
    if any(word in mood for word in ("calm", "care", "warm", "relief", "choice", "hope")):
        return (255, 194, 71, 210)
    if any(word in mood for word in ("doubt", "question", "uncertain", "mystery")):
        return (114, 210, 211, 210)
    return (83, 196, 205, 210)


def draw_pixel_accents(canvas: Image.Image, page: dict[str, Any], rng: random.Random) -> None:
    draw = ImageDraw.Draw(canvas)
    color = mood_color(page)
    count = 2 + int(page["pageNumber"]) % 4
    for _ in range(count):
        x = rng.randint(18, LOGICAL_PAGE[0] - 22)
        y = rng.randint(16, 70)
        size = rng.choice((2, 3, 4))
        draw.rectangle((x, y + size, x + size * 2, y + size * 2), fill=color)
        draw.rectangle((x + size // 2, y, x + size + size // 2, y + size * 3), fill=color)


def page_composition(page: dict[str, Any], book_slug: str) -> tuple[Image.Image, dict[str, Any]]:
    page_number = int(page["pageNumber"])
    rng = random.Random(f"{book_slug}:{page_number}:{page.get('visual', '')}")
    canvas = Image.new("RGBA", LOGICAL_PAGE, (0, 0, 0, 0))
    draw_pixel_accents(canvas, page, rng)

    environment_id = choose_environment(str(page.get("location", "")), book_slug)
    environment = resize_to_fit(load_component(environment_id), 338, 204)
    env_x = (LOGICAL_PAGE[0] - environment.width) // 2 + rng.randint(-5, 5)
    env_y = 21 + rng.randint(-3, 4)
    canvas.alpha_composite(environment, (env_x, env_y))

    props = choose_props(page)
    prop_boxes = []
    for index, asset_id in enumerate(props):
        prop = resize_to_fit(load_component(asset_id), 72 if len(props) == 1 else 58, 62 if len(props) == 1 else 54)
        center_x = 54 if index == 0 else LOGICAL_PAGE[0] - 54
        bottom_y = 105 + index * 8
        prop_boxes.append({"asset": asset_id, "box": composite_center(canvas, prop, center_x, bottom_y)})

    characters = character_assets(page)
    count = max(1, len(characters))
    base_heights = {1: 150, 2: 132, 3: 112, 4: 96, 5: 82, 6: 72}
    base_height = base_heights[min(count, 6)]
    left = 52 if count <= 3 else 34
    right = LOGICAL_PAGE[0] - left
    centers = [round((left + right) / 2)] if count == 1 else [round(left + index * (right - left) / (count - 1)) for index in range(count)]
    character_boxes = []
    for index, ((character, asset_id), center_x) in enumerate(zip(characters, centers)):
        height_factor = 0.78 if character in ("robbie", "puk") else (1.12 if character in ("kroon", "noor", "aicha", "sam", "lotte") else 1.0)
        target_height = max(42, round(base_height * height_factor))
        target_width = max(40, round((right - left) / max(count, 1) * 1.15))
        sprite = resize_to_fit(load_component(asset_id), target_width, target_height)
        if (center_x > LOGICAL_PAGE[0] / 2 and page_number + index) % 2 == 0:
            sprite = ImageOps.mirror(sprite)
        bottom = 245 - ((page_number + index) % 3) * 2
        box = composite_center(canvas, sprite, center_x + rng.randint(-3, 3), bottom)
        character_boxes.append({"character": character, "asset": asset_id, "box": box})

    return canvas, {
        "pageNumber": page_number,
        "environment": environment_id,
        "props": prop_boxes,
        "characters": character_boxes,
    }


def quantize_and_save(image: Image.Image, output: Path, final_size: tuple[int, int]) -> None:
    upscaled = image.resize(final_size, Image.Resampling.NEAREST)
    quantized = upscaled.quantize(colors=255, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
    output.parent.mkdir(parents=True, exist_ok=True)
    quantized.save(output, format="PNG", optimize=True)


def cover_composition(book_slug: str) -> tuple[Image.Image, dict[str, Any]]:
    canvas = Image.new("RGBA", LOGICAL_COVER, (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    palette = {
        "l1_e3": ((255, 194, 71, 230), "environments/e3/workshop", ["props/story/choice_cards", "props/story/signal_bridge"]),
        "l2_m4": ((93, 199, 171, 230), "environments/e3/market_square", ["props/story/city_map", "props/story/city_sensor"]),
        "l2_e4": ((98, 196, 211, 230), "environments/e4/network_center", ["props/story/message_chain", "props/network/remote_signal"]),
        "l3_m5": ((238, 103, 91, 230), "environments/advanced_l3/mine_valley", ["environments/advanced_l3/portable_core", "props/story/uncertainty_meter"]),
        "l3_e5": ((103, 210, 220, 230), "environments/e4/network_center", ["environments/advanced_l3/portable_core", "props/network/priority_arrows"]),
        "l4_m6": ((255, 184, 67, 230), "environments/advanced_l4/world_control", ["environments/advanced_l3/three_key_console", "environments/advanced_l4/module_yard"]),
        "l4_e6": ((89, 201, 155, 230), "environments/advanced_l4/small_world", ["environments/advanced_l4/tomorrow_tree", "environments/advanced_l3/portable_core"]),
    }
    accent, environment_id, props = palette.get(book_slug, palette["l1_e3"])
    for index in range(9):
        x = 18 + (index * 29) % 220
        y = 18 + (index * 17) % 105
        draw.rectangle((x, y, x + 5, y + 5), fill=accent)
    environment = resize_to_fit(load_component(environment_id), 236, 238)
    canvas.alpha_composite(environment, ((LOGICAL_COVER[0] - environment.width) // 2, 42))
    prop_boxes = []
    for index, asset_id in enumerate(props):
        prop = resize_to_fit(load_component(asset_id), 74, 58)
        center = 55 if index == 0 else 201
        prop_boxes.append({"asset": asset_id, "box": composite_center(canvas, prop, center, 155)})

    cover_characters = [
        ("fien", "characters/fien/point", 62, 354, 136),
        ("robbie", "characters/robbie/card", 128, 360, 124),
        ("mats", "characters/mats/map", 196, 354, 136),
    ]
    character_boxes = []
    for character, asset_id, center, bottom, height in cover_characters:
        sprite = resize_to_fit(load_component(asset_id), 92, height)
        character_boxes.append({"character": character, "asset": asset_id, "box": composite_center(canvas, sprite, center, bottom)})
    return canvas, {"environment": environment_id, "props": prop_boxes, "characters": character_boxes}


def validate_saved_image(path: Path, expected_size: tuple[int, int]) -> dict[str, Any]:
    image = Image.open(path)
    rgba = image.convert("RGBA")
    alpha = rgba.getchannel("A")
    corners = [alpha.getpixel((0, 0)), alpha.getpixel((expected_size[0] - 1, 0)), alpha.getpixel((0, expected_size[1] - 1)), alpha.getpixel((expected_size[0] - 1, expected_size[1] - 1))]
    colors = image.getcolors(maxcolors=1_000_000) or []
    return {
        "path": path.relative_to(ROOT).as_posix(),
        "width": image.width,
        "height": image.height,
        "mode": image.mode,
        "paletteColors": len(colors),
        "transparentCorners": all(value == 0 for value in corners),
        "bytes": path.stat().st_size,
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--book", required=True, choices=["l1_e3", "l2_m4", "l2_e4", "l3_m5", "l3_e5", "l4_m6", "l4_e6"])
    args = parser.parse_args()
    book_slug = args.book
    book_root = ROBBIE_ROOT / book_slug
    book_path = book_root / f"robbie_{book_slug}.json"
    if not LIBRARY_MANIFEST.exists():
        raise SystemExit("hybrid library manifest is missing")
    library = json.loads(LIBRARY_MANIFEST.read_text(encoding="utf-8"))
    if not library.get("passed"):
        raise SystemExit("hybrid library did not pass validation")
    if not book_path.exists():
        raise SystemExit(f"book JSON does not exist: {book_path}")

    book = json.loads(book_path.read_text(encoding="utf-8"))
    story_pages = [page for chapter in book["chapters"] for page in chapter["pages"]]
    if len(story_pages) != 150:
        raise SystemExit(f"expected 150 story pages, found {len(story_pages)}")
    pages = [page for page in story_pages if page.get("illustrationRequired", True) and page.get("illustrationFilename")]
    expected_illustrations = int(book.get("illustrationCount", len(pages)))
    if len(pages) != expected_illustrations:
        raise SystemExit(f"expected {expected_illustrations} illustrated pages, found {len(pages)}")

    image_root = book_root / "images" / "final"
    cover_root = book_root / "cover" / "final"
    image_root.mkdir(parents=True, exist_ok=True)
    cover_root.mkdir(parents=True, exist_ok=True)
    composition_pages = []
    saved_pages = []
    for illustration_index, page in enumerate(pages, start=1):
        logical, composition = page_composition(page, book_slug)
        filename = str(page["illustrationFilename"])
        output = image_root / filename
        quantize_and_save(logical, output, FINAL_PAGE)
        saved = validate_saved_image(output, FINAL_PAGE)
        composition_pages.append(composition)
        saved_pages.append(saved)
        if illustration_index % 25 == 0 or illustration_index == len(pages):
            print(f"composed {illustration_index}/{len(pages)} illustrations")

    cover_logical, cover_composition_data = cover_composition(book_slug)
    cover_path = cover_root / str(book["coverFilename"])
    quantize_and_save(cover_logical, cover_path, FINAL_COVER)
    saved_cover = validate_saved_image(cover_path, FINAL_COVER)

    hashes = [asset["sha256"] for asset in saved_pages]
    failures = []
    for asset in saved_pages:
        if (asset["width"], asset["height"]) != FINAL_PAGE:
            failures.append(f"wrong size {asset['path']}")
        if asset["mode"] != "P":
            failures.append(f"wrong mode {asset['path']}: {asset['mode']}")
        if asset["paletteColors"] > 256:
            failures.append(f"too many colors {asset['path']}")
        if not asset["transparentCorners"]:
            failures.append(f"opaque corner {asset['path']}")
    if len(set(hashes)) != expected_illustrations:
        failures.append(f"expected {expected_illustrations} unique hashes, found {len(set(hashes))}")
    if not saved_cover["transparentCorners"]:
        failures.append("cover has an opaque corner")

    manifest = {
        "version": 1,
        "bookId": book["bookId"],
        "bookSlug": book_slug,
        "workflow": "hybrid reusable pixel components composed at 384x256 and nearest-neighbour upscaled 4x",
        "library": LIBRARY_MANIFEST.relative_to(ROOT).as_posix(),
        "storyPageCount": len(story_pages),
        "illustrationCount": len(saved_pages),
        "uniquePageHashes": len(set(hashes)),
        "cover": saved_cover,
        "passed": not failures,
        "failures": failures,
        "compositions": composition_pages,
        "assets": saved_pages,
        "coverComposition": cover_composition_data,
    }
    manifest_path = book_root / "hybrid_composition_manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        "passed": manifest["passed"],
        "bookId": manifest["bookId"],
        "storyPages": manifest["storyPageCount"],
        "illustrations": manifest["illustrationCount"],
        "uniquePageHashes": manifest["uniquePageHashes"],
        "cover": saved_cover["path"],
        "failures": failures,
    }, ensure_ascii=False, indent=2))
    if failures:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
