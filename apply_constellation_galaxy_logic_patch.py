from pathlib import Path
from datetime import datetime

PATCH_TAG = "constellation_galaxy_logic_20260708"


def find_root() -> Path:
    cwd = Path.cwd()
    candidates = [cwd, Path(__file__).resolve().parent]
    candidates.extend(cwd.parents)
    for candidate in candidates:
        if (candidate / "app" / "src" / "main").exists():
            return candidate
    raise SystemExit(
        "Could not find the Android project root. Run this from C:\\AndroidApps\\Poincare "
        "or from the folder that contains app/src/main."
    )


def backup(path: Path) -> None:
    if not path.exists():
        return
    suffix = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_path = path.with_suffix(path.suffix + f".bak_{PATCH_TAG}_{suffix}")
    backup_path.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")


def write_replacement(root: Path, rel: str, source: Path) -> None:
    target = root / rel
    if not source.exists():
        raise SystemExit(f"Patch file missing: {source}")
    new_text = source.read_text(encoding="utf-8")
    if target.exists() and target.read_text(encoding="utf-8") == new_text:
        print(f"No changes needed in {target.name}")
        return
    target.parent.mkdir(parents=True, exist_ok=True)
    backup(target)
    target.write_text(new_text, encoding="utf-8")
    print(f"Replaced {target.name}")


def patch_file(path: Path, patcher, label: str) -> None:
    if not path.exists():
        print(f"SKIP missing {path} ({label})")
        return
    original = path.read_text(encoding="utf-8")
    updated = patcher(original)
    if updated == original:
        print(f"No changes needed in {path.name} ({label})")
        return
    backup(path)
    path.write_text(updated, encoding="utf-8")
    print(f"Patched {path.name} ({label})")


def patch_constellation_models(text: str) -> str:
    updated = text
    # The newer constellation screen uses value labels on target stars.
    if "val label: String?" not in updated:
        updated = updated.replace(
            "val order: Int? = null,\n    val brightness: Float = 1f",
            "val order: Int? = null,\n    val label: String? = order?.toString(),\n    val brightness: Float = 1f"
        )

    replacements = {
        'firstStarSelected = "Glisse vers une autre étoile."': 'firstStarSelected = "Choisis une étoile dans la liste des lignes."',
        'firstStarSelected = "Tape une deuxième étoile ou glisse vers elle."': 'firstStarSelected = "Choisis une étoile dans la liste des lignes."',
        'correctConnection = "Bien joué ! Cette ligne fait partie de la constellation."': 'correctConnection = "Bien joué ! Passe à la ligne suivante."',
        'wrongConnection = "Cette ligne ne fait pas partie de la constellation."': 'wrongConnection = "Cette ligne n’est pas dans la liste."',
        'tipConnect = "Relie ces deux étoiles"': 'tipConnect = "Indice avec les valeurs"',
        'tipConnect = "Indice avec les valeurs"': 'tipConnect = "Indice avec les valeurs"',

        'firstStarSelected = "Drag to another star."': 'firstStarSelected = "Choose a star from the connection list."',
        'firstStarSelected = "Tap a second star or drag to it."': 'firstStarSelected = "Choose a star from the connection list."',
        'correctConnection = "Nice! That line belongs to the constellation."': 'correctConnection = "Nice! Now do the next line."',
        'wrongConnection = "That line is not part of the constellation."': 'wrongConnection = "That line is not in the list."',
        'tipConnect = "Connect these two stars"': 'tipConnect = "Tip with the values"',
        'tipConnect = "Tip with the values"': 'tipConnect = "Tip with the values"',

        'firstStarSelected = "Sleep naar een andere ster."': 'firstStarSelected = "Kies een ster uit de lijst met verbindingen."',
        'firstStarSelected = "Tik een tweede ster aan of sleep ernaartoe."': 'firstStarSelected = "Kies een ster uit de lijst met verbindingen."',
        'correctConnection = "Goed zo! Die lijn hoort bij de constellatie."': 'correctConnection = "Goed zo! Maak nu de volgende lijn."',
        'wrongConnection = "Die lijn hoort niet bij de constellatie."': 'wrongConnection = "Die lijn staat niet in de lijst."',
        'tipConnect = "Verbind deze twee sterren"': 'tipConnect = "Tip met de waarden"',
        'tipConnect = "Tip met de waarden"': 'tipConnect = "Tip met de waarden"',
    }
    for before, after in replacements.items():
        updated = updated.replace(before, after)
    return updated


def main() -> None:
    root = find_root()
    patch_root = Path(__file__).resolve().parent
    kotlin_rel = Path("app/src/main/java/be/saikono/poincare/ui/puzzle")
    source_root = patch_root / "files" / kotlin_rel

    write_replacement(root, str(kotlin_rel / "ConstellationsPuzzleScreen.kt"), source_root / "ConstellationsPuzzleScreen.kt")
    write_replacement(root, str(kotlin_rel / "GalaxyconScreen.kt"), source_root / "GalaxyconScreen.kt")
    patch_file(root / kotlin_rel / "ConstellationPuzzleModels.kt", patch_constellation_models, "clearer constellation copy + value labels")

    print("\nConstellation + Galaxycon logic patch applied.")
    print("Build with: .\\gradlew.bat assembleDebug")


if __name__ == "__main__":
    main()
