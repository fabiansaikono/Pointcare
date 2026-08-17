# Performance maintenance inventory — 2026-08-16

## Purpose

Reduce repeated Codex/Git workspace indexing without deleting project source or user-facing artifacts.

## Baseline inventory

| Path | Files observed | Approximate size | Decision |
| --- | ---: | ---: | --- |
| `.tmp/` | 2,246 physical files plus dependency junction contents | 1,031.8 MB | Preserve the directory contents, ignore it, and remove only confirmed caches/dependency links. |
| `.cache/` | 0 physical files at inventory time | 0 MB | Remove; regeneratable runtime cache location. |
| `.tmp.driveupload/` | 2,340 hard-link staging files | 97.4 MB | Remove; confirmed upload staging. |
| `.tmp.drivedownload/` | 0 files | 0 MB | Remove; empty download staging. |
| `.kotlin/` | 3 files | 0.3 MB | Remove; regeneratable Kotlin cache. |
| `__pycache__/` | 2 files | less than 0.1 MB | Remove; regeneratable Python bytecode. |
| `outputs/` | 73 physical files plus a dependency junction | 859.4 MB | Preserve and ignore; contains XLSX, ZIP, JSON, PNG, HTML and archive deliverables. Remove only dependency links. |
| `.poincare_repair_backups/` | 84 files | 194.6 MB | Preserve and ignore as recovery material. |
| `app/src/main/assets/` | 10,809 files | 573.3 MB | Preserve; application source assets, including offline TTS and curriculum content. |
| `deliverables/` | 33 files | 134.7 MB | Preserve; user-facing artifacts. |
| `curriculum-library/` | 35,643 files | 2,457.5 MB | Preserve and ignore in the outer repository; it is a separate nested Git checkout. |
| `story_import/` | 1,253 files | 565.6 MB | Preserve; project import material. |
| `poincare_update/` | 322 files | 235.0 MB | Preserve; project update package/material. |

## Confirmed disposable targets

- Root runtime/cache directories: `.cache/`, `.tmp.driveupload/`, `.tmp.drivedownload/`, `.kotlin/`, and `__pycache__/`.
- Dependency directories named `node_modules` beneath `.tmp/` and `outputs/`.
- Nested runtime cache directories named `.cache` beneath `.tmp/`.
- Obsolete keep-awake heartbeat files and the old ad-hoc keep-awake script beneath `.tmp/`.

Several `node_modules` paths are NTFS junctions to a user-level Codex runtime cache. Maintenance must remove only the junction itself and must never recurse into its external target.

## Explicitly preserved artifacts

- All application code and assets under `app/`.
- All curriculum-library, story-import and Poincaré update materials.
- All user-facing outputs, deliverables, exports, previews and archives.
- All Poincaré repair backups.
- All non-cache reference packages under `.tmp/`, including curriculum and lesson-validation material.

## Maintenance outcome

- Added narrow root-level ignore rules for runtime caches/staging, task output, recovery backups, dependency directories, and the separate curriculum checkout.
- Removed only the confirmed disposable targets listed above. Junctions were removed as links without traversing or deleting their external runtime-cache targets.
- Reduced the complete untracked-file enumeration from 67,290 to 12,764 files (81.0%). The remaining files are predominantly intentional application assets, source, curriculum imports, update material, tools, documentation, and deliverables.
- Reduced measured untracked enumeration time from 2.04 seconds to 0.18 seconds in the same workspace snapshot.
- Conservatively compacted Git with six pack threads and `git gc --no-prune`; no recoverable object was pruned. The repository now has zero loose objects, 10,193 packed objects in two packs (196.30 MiB), and 13 retained temporary garbage objects (3.66 MiB).
- Hardened the global keep-awake worker with an owner-process lease, PID-reuse protection, stale-sentinel cleanup, an eight-hour maximum lease, error cleanup, and a hidden detached worker that does not retain hook I/O handles.
- Validated two sequential isolated Start/Stop cycles. Each created one worker, associated it with the current Codex owner process, stopped it, and left zero sentinels. A separate orphan-owner test also exited and removed its sentinel.

## Remaining integration check

The hook scripts pass their isolated lifecycle tests, but this task initially started without a lifecycle sentinel. Codex requires a non-managed command hook to be reviewed/trusted. Review the configured hook through `/hooks`, then verify that two real consecutive tasks each report an active sentinel. The bundled CLI could not be launched from the restricted WindowsApps path during this maintenance task, so trust state was not changed programmatically.
