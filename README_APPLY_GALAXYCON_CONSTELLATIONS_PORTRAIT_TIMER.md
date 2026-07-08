# Poincare Galaxycon + Constellations refinement

Run from the project root:

```powershell
cd C:\AndroidApps\Poincare
Expand-Archive -Force .\galaxycon_constellations_portrait_timer_patch.zip -DestinationPath .
py .\apply_galaxycon_constellations_portrait_timer_patch.py
.\gradlew.bat assembleDebug
```

Changes:

- Galaxycon is forced to portrait while the game composable is open.
- Galaxycon keeps three lanes: left, middle, right.
- Galaxycon uses swipe-only movement.
- Galaxycon multiplier timer is visible in the HUD.
- Multipliers last 15 seconds.
- Multiplier timer resets to 15 seconds when a new multiplier is collected.
- Active multiplier is cancelled when the player picks a wrong letter or hits an asteroid.
- Constellations no longer shows the bottom reset/next-mission button. Only the tip button remains while the puzzle is active, and the whole bottom control row disappears when completed.

The script creates `.bak_portrait_timer_...` backups before overwriting files.
