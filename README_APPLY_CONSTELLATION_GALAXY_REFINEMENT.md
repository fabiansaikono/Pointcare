# Poincaré constellation + Galaxycon refinement patch

Run from the Android project root:

```powershell
cd C:\AndroidApps\Poincare
Expand-Archive -Force .\poincare_constellation_galaxy_refinement_patch.zip .
py .\apply_constellation_galaxy_refinement_patch.py
.\gradlew.bat assembleDebug
```

## Changes

### Constellations
- Removes preview/guide connection lines before a connection is actually formed.
- Keeps only selected/formed connections visible.
- Tip now gives a concrete value hint, for example `connect 4 with 8; 4 × 2 = 8`.
- Supports both tap-tap and drag-to-connect interaction.
- Keeps the selected first star highlighted, so the next action is clearer.
- Removes the internal puzzle title.

### Galaxycon
- Portrait: top-to-bottom gameplay with three rocket positions: left, middle, right.
- Landscape: right-to-left gameplay, using the screen width.
- Swipe-only lane changes; no arrow buttons.
- Rocket is shown without a surrounding control circle.
- Planet is drawn as an irregular planet shape rather than a plain big circle.
- Letters have no boxes; correct letters are PlanetGold, wrong letters are StarWhite.
- Multipliers use PlanetBlue.
- Debris is drawn as asteroid-shaped rocks.
