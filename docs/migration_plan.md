# EtoileBridge Flutter Migration Plan

Version: `1.2.26629`

## Goal

Move EtoileBridge toward one Flutter UI for Android and Windows while keeping the released Android and Electron projects intact as stable references.

## Reference Priority

- Windows desktop behavior follows `EtoileBridgeElectron`.
- Mobile layout and interaction polish follows `EtoileBridge`.
- `EtoileBridgeWindows` remains historical reference only.

## Converter Strategy

Recommended near-term plan: **Plan B**.

- Flutter owns UI, state, i18n, navigation, settings, previews, and validation surfaces.
- Android calls the existing Kotlin core through platform channels.
- Windows calls the existing Kotlin/JVM converter worker through a bundled runtime, following the Electron release model.
- Dart converter-core migration is deferred until regression tests are broad enough.

## Stages

1. `5.1` Single Song real platform bridge.
2. `5.2` Pack Editor real scan/save bridge.
3. `5.3` Character Editor real scan/icon/save bridge.
4. `5.4` Android and Windows Flutter packaging validation.

## Platform Abstractions

- `PlatformFilePicker`
- `PlatformSaveDialog`
- `PlatformCacheService`
- `PlatformOpenLocation`
- `PlatformWorkerBridge`
- `PlatformPaths`

All are mock-backed in 5.0 and ready for platform-channel implementations.
