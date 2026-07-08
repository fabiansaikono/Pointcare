# Poincaré constellation + Galaxycon logic patch

Run from the Android project root:

```powershell
cd C:\AndroidApps\Poincare
Expand-Archive -Force .\poincare_constellation_galaxy_logic_patch.zip .
py .\apply_constellation_galaxy_logic_patch.py
.\gradlew.bat assembleDebug
```

## Changes

### Constellations
- Adds a clear connection checklist next to/below the star field.
- Every required independent line is shown as its own value pair, for example `4 ↔ 8` with `4 × 2 = 8`.
- Completed pairs are greyed out with a checkmark.
- The next pair is highlighted, but no guide/solution line is drawn before the user forms it.
- Tapping a pair in the checklist highlights only the two stars to connect.
- Tapping the tip button highlights the next missing pair and gives the value rule.
- After a correct line, selection resets and the next missing line becomes active, which is clearer for constellations with separate independent lines.

### Galaxycon
- Keeps three lanes/positions: left, middle, right in portrait; top, middle, bottom in landscape.
- Uses swipe-only lane changes. Portrait uses horizontal swipes; landscape uses vertical swipes.
- Removes the middle-lane colour block.
- Removes the destination planet graphic during gameplay so it no longer overlaps the top HUD.
- Keeps portrait gameplay top-to-bottom and landscape gameplay right-to-left.
- Letter colours update dynamically after each correct pickup: the currently needed letters turn PlanetGold and the other letters stay StarWhite.
- Dynamic letter logic also applies to collisions, so an old on-screen letter that becomes the new required letter can be collected correctly.
