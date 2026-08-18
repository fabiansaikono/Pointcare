# Poincaré

Poincaré is an offline-first Android learning app with a web-based Curriculum Room.

## Source of truth

`curriculum-library` owns the canonical curriculum. Editorial changes are stored by the Curriculum Room; generated source data, story books, AVI metadata and release validation live there as well. Android assets are release outputs and must not be edited as curriculum source.

Release flow:

1. Generate or edit content in `curriculum-library`.
2. Run its validation and production build.
3. Export one versioned, checksummed release bundle to Android.
4. Build and test the Android app, then install that exact release.

Learner progress is stored locally on the device. There is no remote progress-sync claim until an authenticated, idempotent backend exists.

## Common commands

```powershell
cd curriculum-library
npm run data:sync
npm test
npm run data:export-android -- <release-snapshot.json>

cd ..
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

See `docs/CODEX_HANDOFF.md` for the latest task handoff and `.openai/hosting.json` for Curriculum Room hosting configuration.

## Architecture references

- `docs/MOBILE_APP_ARCHITECTURE.md` — Android app shell, navigation, profile/parent/task flows, and build-stack contract.
- `docs/GALAXY_NAVIGATION_ARCHITECTURE.md` — dynamic Galaxy model, sphere-group layout, rendering, and interaction contract.
- `docs/CURRICULUM_ARCHITECTURE_PLAYBOOK.md` — normative build and grade-rollout contract.
- `docs/GENERIC_DESCRIPTION_CURRICULUM.md` — product principles and quality framework.
- `docs/lesson_v2/SECTIONED_LESSON_ARCHITECTURE.md` — exact three-part lesson data/runtime contract.
- `docs/CURRICULUM_STRUCTURE_GRADE1_MIGRATION.md` — Grade 1 structure migration and the template for later grades.

The exact deterministic geometry used inside classic Galaxy sphere groups is specified in `context/GalaxyMap/poincare_galaxy_navigation_prompt.md`. It is a geometry source, not a second curriculum or navigation-state source.

## Android build baseline

The app uses AGP 9.2.1 with Gradle 9.4.1 and AGP's built-in Kotlin support. Kotlin compiler plugins are pinned to 2.2.10, KSP to 2.3.11, and Java/Kotlin bytecode to JVM 11. The legacy `kotlin-android` plugin and AGP built-in-Kotlin opt-outs must not be reintroduced.
