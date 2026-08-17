# Codex handoff

## Task objective

Resolve the actionable findings from the full Poincare audit while preserving all existing application, curriculum, test, and recovery work and keeping Windows responsive.

## Outcome and important decisions

- Replaced the permanently empty homework/task screen with a versioned, strict, teacher-ID-backed repository contract. The app now loads localized tasks from `poincare/content/homework-tasks-v1.json`; `DEMO-POINCARE` provides two visibly labeled demo assignments.
- Added state management for task loading, locale fallback, deterministic ordering/filtering, load-failure UI, and focused unit and Compose instrumentation coverage.
- Kept remote task delivery honest: `SyncManager` still does not upload or invent server data because no authenticated, idempotent teacher-task backend contract exists. The new repository boundary is ready to receive that implementation.
- Added Compose instrumentation coverage for the task page and shared main-category header; tests compile, but were not executed on a physical device in this task.
- Removed the unused Galaxy legend implementation and obsolete hidden Galaxy strings from the default, Dutch, and French resources.
- Made the current project state locally reproducible on branch `codex/audit-findings-fixes-20260817`: tracked changes plus intended `app`, `docs`, `tools`, and `scripts` artifacts are preserved; unrelated import/update ZIPs and recovery artifacts remain untouched and untracked.
- Added narrow Git LFS rules for large application audio/model/image assets and verified representative OGG and ONNX index objects are LFS pointers. No remote push was performed.
- Removed 22 confirmed stale `.git/objects/**/tmp_obj_*` files (10.27 MiB); Git reports zero remaining garbage objects.
- Hardened the project keep-awake instructions: Windows recovery must feed hook JSON through redirected standard input, record a unique sentinel/worker PID, and remove only that exact sentinel. No permanent power-plan setting was changed.
- Investigated the Android Gradle Plugin 10 migration. The obsolete variant APIs originate in the legacy Kotlin Android/KSP toolchain. A current built-in-Kotlin/KSP attempt was reverted after compilation consumed several GiB for several minutes; the proven configuration was restored to protect device responsiveness.
- Diagnosed the 596.8 MiB debug APK rather than deleting product assets: offline TTS/audio and multi-ABI native libraries account for most of the size. Reducing it requires an explicit offline-content and ABI/release-delivery decision.

## Files or artifacts changed

- Added task data/domain/repository/UI-state files under `app/src/main/java/be/saikono/poincare/data/tasks`, `repository`, and `ui/tasks`.
- Added `app/src/main/assets/poincare/content/homework-tasks-v1.json`.
- Updated `MainActivity.kt`, `TasksScreen.kt`, task tests, Galaxy dead code, and localized Galaxy resources.
- Added `TasksScreenInstrumentedTest.kt` and `MainCategoryHeaderInstrumentedTest.kt`.
- Updated `.gitattributes`, `.gitignore`, `AGENTS.md`, and this handoff.
- Preserved the audited application/curriculum/test state through local Git staging/commit and Git LFS; no remote repository was changed.

## Verification performed

- Passed: focused `TasksScreenTest` after the repository integration.
- Passed: complete `testDebugUnitTest --no-daemon --max-workers=2`: 400 tests in 88 suites, 0 failures, 0 errors, 0 skipped (`BUILD SUCCESSFUL` in 3m 22s).
- Passed: `compileDebugAndroidTestKotlin --no-daemon --max-workers=2` (`BUILD SUCCESSFUL` in 1m 14s); the new instrumentation tests compile.
- Passed: `assembleDebug --no-daemon --max-workers=2` (`BUILD SUCCESSFUL` in 1m 26s; 6 executed, 31 up-to-date).
- Passed: `git diff --cached --check`, with no whitespace errors after treating third-party eSpeak runtime data byte-for-byte and preserving valid Markdown hard breaks.
- Passed: representative staged OGG and ONNX files are 130/133-byte Git LFS pointers; no suspicious credential/key filenames were staged.
- Not run: Android instrumentation tests on an emulator/device, screenshot comparison, physical-device smoke test, installation, or remote task-server integration.

## Remaining risks, blockers, or follow-up work

- Real teacher assignments still require a defined authenticated backend/API and a repository implementation behind the new `HomeworkTaskRepository` boundary.
- The local LFS-backed preservation commit must be pushed together with its LFS objects when the team is ready; a clean-clone verification is only meaningful after that push.
- AGP 10/built-in Kotlin migration remains isolated build-tool debt. Retry it separately with a resource budget or on CI; do not combine it with feature work on this laptop.
- APK size remains a product/release decision: choose which TTS/audio stays offline and which ABIs/release splits are required before reducing assets.
- Device UI behavior still needs a focused smoke matrix for tasks, portrait/landscape headers, Galaxy pan/zoom, locked selections, and story themes.
- Automatic Codex lifecycle activation was not reliably present at task start; the exact recovery path works, but the desktop host should still be checked independently.

## Task-stop resource state and Git scan

- Final controlled Git scan: `PENDING_FINAL_SCAN`.
- Final keep-awake/process state: `PENDING_FINAL_CLEANUP`.

## Exact recommended next action

Define the authenticated teacher-task response contract and connect it to `HomeworkTaskRepository`; then run the two new Compose instrumentation tests and the focused task/Galaxy smoke matrix on one physical device.
