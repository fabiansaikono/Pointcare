# Lesson architecture v2 build and size report

## Baseline

- Local branch: `feature/firebase-progress`
- Git commit at inspection: `48303da81142ff9dead621b7e8018cef15a8bf70`
- Live-source decision: the local workspace was newer than the inspected canonical Drive snapshot and was used as the implementation source.
- Existing full unit suite: PASS before the v2 implementation.
- Baseline debug APK: 536,406,411 bytes.
- Baseline APK SHA-256: `7efdb0912d718723971ec0e1125d78bb4216fe8aba4309ecb95e1c01394aef61`.
- Baseline assets: 8,948 files, 423,622,365 bytes.

## Final validation

- Catalogue audit: PASS, 40/40 checks.
- Unit tests: PASS, 284 tests.
- Android migration-test compilation: PASS (`:app:compileDebugAndroidTestKotlin`).
- Normal APK build: PASS (`:app:assembleDebug --no-daemon --no-configuration-cache`).
- Device installation: PASS on OPPO CPH2653 (`feed09ac`), version `1.3.4` / code `34`; existing app data retained.

## Size comparison

| Item | Before | After | Difference |
|---|---:|---:|---:|
| Debug APK | 536,406,411 bytes | 537,958,463 bytes | +1,552,052 bytes (+0.2893%) |
| Source assets | 423,622,365 bytes | 424,969,809 bytes | +1,347,444 bytes (+0.3181%) |
| Asset files | 8,948 | 8,955 | +7 |

Final APK SHA-256: `46e3f775ddc39e64e4a76550b21b40a279a53f3a744019b10537d3d6ea92c50b`.

The seven added runtime assets are the compact v2 pilot catalogue, the SVG and PNG season visual, and four lesson-specific transparent theory PNGs. No research-package snapshot or duplicate split/monolithic catalogue was added to the APK.
