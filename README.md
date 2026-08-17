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

- `docs/CURRICULUM_ARCHITECTURE_PLAYBOOK.md` — normative build and grade-rollout contract.
- `docs/GENERIC_DESCRIPTION_CURRICULUM.md` — product principles and quality framework.
- `docs/lesson_v2/SECTIONED_LESSON_ARCHITECTURE.md` — exact three-part lesson data/runtime contract.
- `docs/CURRICULUM_STRUCTURE_GRADE1_MIGRATION.md` — Grade 1 structure migration and the template for later grades.
