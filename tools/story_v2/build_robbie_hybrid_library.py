from __future__ import annotations

import json
from collections import deque
from pathlib import Path
from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2]
ROBBIE = ROOT / "story_import" / "generated" / "robbie"
LIBRARY = ROBBIE / "hybrid_library"
FINAL = LIBRARY / "final"
COMPONENTS = LIBRARY / "components"
MANIFEST = LIBRARY / "library_manifest.json"
QA_SHEET = LIBRARY / "qa" / "components_contact_sheet.png"


ATLASES = {
    "robbie_pose_atlas.png": (3, 2, "characters/robbie", ["neutral", "point", "card", "walk", "stop", "inspect"]),
    "fien_pose_atlas.png": (3, 2, "characters/fien", ["neutral", "point", "clipboard", "crouch", "stop", "walk"]),
    "mats_pose_atlas.png": (3, 2, "characters/mats", ["neutral", "meter", "cable", "wrench", "map", "run"]),
    "kroon_pose_atlas.png": (3, 2, "characters/kroon", ["neutral", "sensor", "point", "transmitter", "folder", "leave"]),
    "noor_pose_atlas.png": (3, 2, "characters/noor", ["neutral", "diagram", "meter", "repair", "permission", "folder"]),
    "aicha_pose_atlas.png": (3, 2, "characters/aicha", ["neutral", "meter", "plant", "water", "compare", "caution"]),
    "sam_pose_atlas.png": (3, 2, "characters/sam", ["neutral", "wrench", "lamp", "cable", "point", "approve"]),
    "lotte_pose_atlas.png": (3, 2, "characters/lotte", ["neutral", "plan", "listen", "sign", "map", "evidence"]),
    "resident_atlas.png": (3, 2, "characters/residents", ["school_child", "cargo_parent", "older_resident", "wheelchair_user", "waste_worker", "care_worker"]),
    "e3_environment_atlas.png": (3, 2, "environments/e3", ["workshop", "market_square", "kroon_van", "hearing_hall", "kroon_lab", "old_shed"]),
    "m4_environment_atlas.png": (3, 2, "environments/m4", ["school_street", "care_center", "remote_street", "canal_lock", "night_park", "canal_bridge"]),
    "e4_environment_atlas.png": (3, 2, "environments/e4", ["network_center", "source_wall", "flood_station", "privacy_alcove", "monorail", "hidden_transmitter"]),
    "story_prop_atlas.png": (4, 3, "props/story", ["choice_cards", "signal_bridge", "red_test_lamp", "learning_copy", "city_sensor", "city_map", "canal_controls", "empty_nest", "waste_bin", "privacy_lock", "message_chain", "uncertainty_meter"]),
    "network_prop_atlas.png": (4, 3, "props/network", ["old_new_cassette", "clock_pair", "rain_sun", "private_cabinet", "source_card", "shortened_copy", "priority_arrows", "public_services", "flood_warning", "safe_meter_pair", "public_logbook", "remote_signal"]),
    "l3_advanced_atlas.png": (4, 3, "environments/advanced_l3", ["world_map", "black_river", "forest_gap", "mine_entry", "wetland", "data_room", "shrink_lab", "greenhouse", "mine_valley", "cracked_field", "portable_core", "three_key_console"]),
    "l4_advanced_atlas.png": (4, 3, "environments/advanced_l4", ["world_control", "module_yard", "protected_forest", "reversible_test", "council_chamber", "sabotaged_core", "windrand_trail", "windrand_factory", "forest_sphere", "small_world", "restoration_site", "tomorrow_tree"]),
}

MANUAL_CROPS = {
    "network_prop_atlas.png": [
        (0, 0, 520, 350),
        (520, 0, 950, 350),
        (950, 0, 1536, 350),
        (0, 350, 420, 690),
        (420, 350, 740, 690),
        (740, 350, 1070, 690),
        (1070, 350, 1536, 690),
        (0, 690, 430, 1024),
        (430, 690, 720, 1024),
        (720, 690, 1030, 1024),
        (1030, 690, 1280, 1024),
        (1280, 690, 1536, 1024),
    ],
}


def relative(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def trim_alpha(image: Image.Image, padding: int = 8) -> Image.Image:
    rgba = image.convert("RGBA")
    alpha = rgba.getchannel("A")
    bbox = alpha.point(lambda value: 255 if value > 8 else 0).getbbox()
    if bbox is None:
        raise ValueError("empty atlas cell")
    left = max(0, bbox[0] - padding)
    top = max(0, bbox[1] - padding)
    right = min(rgba.width, bbox[2] + padding)
    bottom = min(rgba.height, bbox[3] + padding)
    return rgba.crop((left, top, right, bottom))


def clear_atlas_cell_edge(image: Image.Image, edge: int = 14) -> Image.Image:
    rgba = image.convert("RGBA")
    alpha = rgba.getchannel("A")
    draw = ImageDraw.Draw(alpha)
    draw.rectangle((0, 0, rgba.width - 1, edge - 1), fill=0)
    draw.rectangle((0, rgba.height - edge, rgba.width - 1, rgba.height - 1), fill=0)
    draw.rectangle((0, 0, edge - 1, rgba.height - 1), fill=0)
    draw.rectangle((rgba.width - edge, 0, rgba.width - 1, rgba.height - 1), fill=0)
    rgba.putalpha(alpha)
    return rgba


def remove_tiny_components(image: Image.Image) -> Image.Image:
    rgba = image.convert("RGBA")
    alpha_image = rgba.getchannel("A")
    alpha = alpha_image.tobytes()
    width, height = rgba.size
    visited = bytearray(width * height)
    components: list[list[int]] = []
    for start, value in enumerate(alpha):
        if value <= 8 or visited[start]:
            continue
        visited[start] = 1
        queue: deque[int] = deque([start])
        component: list[int] = []
        while queue:
            current = queue.popleft()
            component.append(current)
            x = current % width
            y = current // width
            for ny in range(max(0, y - 1), min(height, y + 2)):
                base = ny * width
                for nx in range(max(0, x - 1), min(width, x + 2)):
                    neighbor = base + nx
                    if not visited[neighbor] and alpha[neighbor] > 8:
                        visited[neighbor] = 1
                        queue.append(neighbor)
        components.append(component)
    if not components:
        return rgba
    largest = max(len(component) for component in components)
    minimum = max(48, round(largest * 0.008))
    mutable_alpha = bytearray(alpha)
    for component in components:
        if len(component) < minimum:
            for pixel in component:
                mutable_alpha[pixel] = 0
    rgba.putalpha(Image.frombytes("L", (width, height), bytes(mutable_alpha)))
    return rgba


def alpha_stats(image: Image.Image) -> dict[str, int | float]:
    rgba = image.convert("RGBA")
    alpha = list(rgba.getchannel("A").get_flattened_data())
    total = len(alpha)
    transparent = sum(value == 0 for value in alpha)
    partial = sum(0 < value < 255 for value in alpha)
    opaque = sum(value == 255 for value in alpha)
    return {
        "pixels": total,
        "transparent": transparent,
        "partial": partial,
        "opaque": opaque,
        "coverage": round((partial + opaque) / max(total, 1), 4),
    }


def main() -> None:
    COMPONENTS.mkdir(parents=True, exist_ok=True)
    assets: list[dict[str, object]] = []
    failures: list[str] = []

    for filename, (columns, rows, group, names) in ATLASES.items():
        atlas_path = FINAL / filename
        if not atlas_path.exists():
            failures.append(f"missing atlas {relative(atlas_path)}")
            continue
        atlas = Image.open(atlas_path).convert("RGBA")
        if atlas.size != (1536, 1024):
            failures.append(f"unexpected size {atlas.size} for {filename}")
        target_dir = COMPONENTS / group
        target_dir.mkdir(parents=True, exist_ok=True)
        for index, name in enumerate(names):
            if filename in MANUAL_CROPS:
                left, top, right, bottom = MANUAL_CROPS[filename][index]
                cell_source = atlas.crop((left, top, right, bottom))
            else:
                column = index % columns
                row = index // columns
                left = round(column * atlas.width / columns)
                right = round((column + 1) * atlas.width / columns)
                top = round(row * atlas.height / rows)
                bottom = round((row + 1) * atlas.height / rows)
                cell_source = clear_atlas_cell_edge(atlas.crop((left, top, right, bottom)), edge=28)
            try:
                cell = trim_alpha(remove_tiny_components(cell_source))
            except ValueError:
                failures.append(f"empty cell {filename} #{index + 1}")
                continue
            output = target_dir / f"{name}.png"
            cell.save(output, format="PNG", optimize=True)
            stats = alpha_stats(cell)
            if stats["coverage"] < 0.03:
                failures.append(f"very low coverage {output}")
            assets.append({
                "id": f"{group}/{name}",
                "kind": group.split("/", 1)[0],
                "group": group,
                "name": name,
                "sourceAtlas": relative(atlas_path),
                "path": relative(output),
                "width": cell.width,
                "height": cell.height,
                "alpha": stats,
            })

    standalone = {
        "props/story/garden_grate": ROBBIE / "references" / "final" / "garden_grate_reference.png",
        "characters/puk/neutral": ROBBIE / "references" / "final" / "puk_character_reference.png",
        "props/story/kroon_factory_mark": ROBBIE / "references" / "final" / "kroon_factory_mark_reference.png",
    }
    for asset_id, source in standalone.items():
        if not source.exists():
            failures.append(f"missing standalone {relative(source)}")
            continue
        image = trim_alpha(Image.open(source).convert("RGBA"))
        output = COMPONENTS / f"{asset_id}.png"
        output.parent.mkdir(parents=True, exist_ok=True)
        image.save(output, format="PNG", optimize=True)
        assets.append({
            "id": asset_id,
            "kind": asset_id.split("/", 1)[0],
            "group": asset_id.rsplit("/", 1)[0],
            "name": asset_id.rsplit("/", 1)[1],
            "sourceAtlas": relative(source),
            "path": relative(output),
            "width": image.width,
            "height": image.height,
            "alpha": alpha_stats(image),
        })

    manifest = {
        "version": 1,
        "workflow": "built-in image generation on chroma, local alpha removal, deterministic atlas slicing and page compositing",
        "generatedAtlases": len(ATLASES),
        "generatedComponents": len(assets),
        "expectedComponents": sum(len(spec[3]) for spec in ATLASES.values()) + len(standalone),
        "passed": not failures and len(assets) == sum(len(spec[3]) for spec in ATLASES.values()) + len(standalone),
        "failures": failures,
        "assets": assets,
    }
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    columns = 6
    cell_width = 220
    image_height = 150
    label_height = 30
    rows = (len(assets) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * cell_width, rows * (image_height + label_height)), "#20242c")
    draw = ImageDraw.Draw(sheet)
    for index, asset in enumerate(assets):
        component = Image.open(ROOT / str(asset["path"])).convert("RGBA")
        component.thumbnail((cell_width - 16, image_height - 16), Image.Resampling.NEAREST)
        x = (index % columns) * cell_width
        y = (index // columns) * (image_height + label_height)
        checker = Image.new("RGB", (cell_width, image_height), "#d8d8d8")
        checker_draw = ImageDraw.Draw(checker)
        for cy in range(0, image_height, 16):
            for cx in range(0, cell_width, 16):
                if (cx // 16 + cy // 16) % 2:
                    checker_draw.rectangle((cx, cy, cx + 15, cy + 15), fill="#eeeeee")
        px = (cell_width - component.width) // 2
        py = (image_height - component.height) // 2
        checker.paste(component, (px, py), component)
        sheet.paste(checker, (x, y))
        draw.rectangle((x, y + image_height, x + cell_width, y + image_height + label_height), fill="#11151b")
        label = str(asset["id"])
        if len(label) > 32:
            label = label[:29] + "..."
        draw.text((x + 6, y + image_height + 8), label, fill="white")
    QA_SHEET.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(QA_SHEET, format="PNG", optimize=True)

    print(json.dumps({key: manifest[key] for key in ["passed", "generatedAtlases", "generatedComponents", "expectedComponents", "failures"]}, ensure_ascii=False, indent=2))
    if not manifest["passed"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
