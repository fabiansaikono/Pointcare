from pathlib import Path
from datetime import datetime
import shutil
import sys

ROOT = Path.cwd()
STAMP = datetime.now().strftime('%Y%m%d_%H%M%S')
FILES = [
    Path('app/src/main/java/be/saikono/poincare/ui/puzzle/GalaxyconScreen.kt'),
    Path('app/src/main/java/be/saikono/poincare/ui/puzzle/ConstellationsPuzzleScreen.kt'),
]

patch_root = ROOT / 'files'
missing = []
for rel in FILES:
    src = patch_root / rel
    dst = ROOT / rel
    if not src.exists():
        missing.append(f'Patch file missing: {src}')
    if not dst.exists():
        missing.append(f'Target file missing: {dst}')

if missing:
    print('\n'.join(missing))
    print('\nMake sure you expanded the zip into the project root, so the folder C:\\AndroidApps\\Poincare\\files exists.')
    sys.exit(1)

for rel in FILES:
    src = patch_root / rel
    dst = ROOT / rel
    backup = dst.with_name(dst.name + f'.bak_portrait_timer_{STAMP}')
    shutil.copy2(dst, backup)
    shutil.copy2(src, dst)
    print(f'Patched {dst}')
    print(f'Backup  {backup}')

print('\nDone. Now run: .\\gradlew.bat assembleDebug')
