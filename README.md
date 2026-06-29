# EtoileBridge

EtoileBridge is an ArcCreate package conversion and editing toolkit for Windows and Android.

Current version: `v1.2.26629`

This project is not affiliated with lowiro or the official ArcCreate team.

## Supported Workflows

- Single song conversion to ArcCreate `.arcpkg`
- Pack editing and pack conversion workflows
- Character / partner package editing
- ArcCreate appearance options and shared AFF preprocessing options
- ArcCreate Result screen preview with real Result texture layers
- GitHub update checking

## Downloads

Download the latest release from:

https://github.com/ZeErQi27/EtoileBridge/releases

Windows is distributed as a portable zip package. Extract the zip, then run `etoile_bridge.exe`.

Android is distributed as a signed release APK.

## Build

Use Flutter 3.44 or newer with Windows desktop and Android support enabled.

```powershell
flutter pub get
flutter analyze
flutter test
flutter build windows
flutter build apk --release
```

Android release signing is intentionally loaded from a local secrets directory and is not committed to this repository.
