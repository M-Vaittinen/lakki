# Project context for automated contributors

## Android versions and tooling
- AGP: 8.13.2
- Kotlin: 2.0.21
- Compile SDK: 36
- Target SDK: 36
- Min SDK: 24

## Key library versions
- Play Services Location: 21.3.0
- MapLibre Android SDK: 12.3.1
- AndroidX Lifecycle Runtime KTX: 2.10.0
- AndroidX Activity Compose: 1.12.2
- Compose BOM: 2024.09.00

## Notes
- Lint is strict with `@RequiresPermission` APIs at these SDK levels. Prefer explicit permission checks plus
  `@SuppressLint("MissingPermission")` when a runtime check is already present, and guard with
  `SecurityException` where appropriate.
