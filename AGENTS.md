# Poincare Codex working agreements

These instructions apply to every Codex task in this repository.

## Work activity lifecycle

- Global Codex lifecycle hooks start a Windows keep-awake worker when a user turn begins and release it when the turn stops.
- Do not change the permanent Windows power plan.
- Do not start a second keep-awake process manually unless the lifecycle hook is being diagnosed.
- If the keep-awake hook reports a warning, continue safe work and mention the warning in the final response.

## Task-start status confirmation

At the start of every new Codex task in this repository, before substantive local work:

- Check whether the lifecycle keep-awake sentinel and worker are active.
- Tell the user whether keep-awake is active, inactive, or running in a diagnosed recovery mode.
- Confirm that the resource-safety settings below are in effect: one resource-intensive command at a time, conservative worker limits (preferably at most 70% of CPU cores), no parallel heavy builds/tests/emulators, and smallest relevant validation first.
- Before starting any heavy operation, check that no other resource-intensive local operation is already running and restate the worker/resource limit that will be used.
- If keep-awake is inactive, do not change the Windows power plan. Diagnose the lifecycle hook before using at most one temporary recovery worker, and report that recovery status to the user.
  - On Windows, invoke `Start-WorkActivity.ps1` with hook-event JSON written to a child PowerShell process through redirected standard input. Do not pipe a PowerShell object directly to the `.ps1`; that does not populate `[Console]::In` and produces a false `No hook event JSON` failure.
  - Give the recovery event a task-unique session/turn key, record the exact sentinel path and worker PID, and remove only that sentinel during task-stop cleanup.

## Resource safety — important

This computer must remain responsive while you work.

For all local operations:

- Never run more than one resource-intensive command at the same time.
- Do not launch multiple builds, test suites, emulators, browsers, or analysis jobs in parallel.
- Prefer targeted/incremental builds and tests over full-project builds.
- When a tool supports worker/thread limits, use conservative limits:
  - preferably max 70% of available CPU cores;
  - never use unlimited/default maximum parallelism for heavy workloads.
- Avoid memory-intensive operations when a lower-memory alternative exists.
- Break very large operations into smaller sequential steps.
- Do not start multiple local agents for compute-heavy work.
- If an operation is likely to consume very high CPU, RAM, GPU, or disk I/O for several minutes, warn the user first or use a lower-resource approach.
- System responsiveness is more important than completing the task as quickly as possible.

For validation:

- Run the smallest relevant test/build first.
- Only run a complete build/test suite when it is actually necessary.

## Task-stop resource cleanup

Before every final response:

- Record a lightweight Git-scan snapshot: count live `git` processes before and after one timed `git status --short --untracked-files=normal`, record the status-entry count and elapsed time, and include those figures in the task handoff. Do not use `--untracked-files=all` for this routine check.
- If Git processes remain after the timed status command, record their process IDs and parent process IDs before deciding whether any process is safe to stop. Never terminate all Git processes blindly.
- Check that no build, test, emulator, ADB, browser, or helper process started by the task remains running unless the user explicitly asked it to remain available.
- Remove task-specific staging files and dependency copies that are confirmed disposable; never remove or ignore application source, curriculum material, deliverables, or recovery artifacts merely because they are large.
- Keep reusable dependency/runtime caches outside the repository. If a tool creates a local cache, ensure it is covered by a narrow ignore rule.
- If a diagnosed temporary keep-awake recovery worker was used, remove its exact sentinel and verify that its worker exits. Do not change the permanent Windows power plan.
- Report the final keep-awake state and whether any resource-intensive task process remains.

## Required handoff

Before the final response for every task, update `docs/CODEX_HANDOFF.md` with a concise, self-contained handoff. Include:

1. Task objective.
2. Outcome and important decisions.
3. Files or artifacts changed. Write `None` when nothing changed.
4. Verification performed and its result. Distinguish passed, failed, and not run.
5. Remaining risks, blockers, or follow-up work.
6. The exact recommended next action.

Keep the handoff useful to a new Codex task that has no transcript. Preserve unrelated user work. Never record secrets, credentials, access tokens, or private personal data.

The global `Stop` lifecycle hook automatically saves the final response to the global Codex handoff log and the latest snapshot for this working directory. The repository log at `docs/handoffs/CODEX_TASK_LOG.md` is retained as historical installation evidence; do not edit or truncate it during normal task work.
