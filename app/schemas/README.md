# Room schema history

Room exports the canonical database schema to this directory during KSP processing.

* Version 8 is the first schema committed after schema export became mandatory.
* Version 7 predates exported schema history. Its production shape is covered by
  `PoincareDatabaseMigrationTest`, which creates representative version-7 tables,
  seeds profiles, progress, rewards, and sync queue records, then opens the database
  through the real registered migration chain and current Room schema validation.
* Version 9 adds dedicated `lesson_progress` and `lesson_attempts` tables while preserving
  every existing version-8 table and row. The 8→9 migration is executed on an authorized
  Android device or emulator before Step 2B.2 can report success.
* Every later database version must add its generated JSON schema and a migration test.

Do not delete historical JSON schemas after they have been released.
