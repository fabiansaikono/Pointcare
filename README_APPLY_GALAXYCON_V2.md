# Galaxycon patch v2

This is a more flexible installer for the Galaxycon puzzle game.

Run from the root of the Pointcare repository:

```powershell
py tools/install_galaxycon_patch_v2.py
```

The installer:

- copies `GalaxyconModels.kt` and `GalaxyconScreen.kt` into `ui/puzzle`
- adds the Galaxycon tile to the Puzzles category
- adds Galaxycon as a generated puzzle next to Sudoku and Math Crosswords
- saves Galaxycon result and sticker metadata in progress events
- creates `.bak_galaxycon` backups for modified Kotlin files

After applying, open Android Studio, sync Gradle, and build.
