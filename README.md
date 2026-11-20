# EtoileBridge

EtoileBridge is an ArcCreate package conversion and editing toolkit for Windows and Android.

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
For Windows, install the .NET 10 SDK (or point `ETOILEBRIDGE_DOTNET` to it),
Visual Studio's C++ desktop workload, and JDK 21. The Windows companion uses
the [Etoile.Lite v0.1.7 parser](https://github.com/freeze-dolphin/Etoile.Lite/tree/v0.1.7)
and ships self-contained, so end users do not have to install .NET.
Android continues to use the Kotlin converter.

```powershell
flutter pub get
flutter analyze
flutter test
.\android\gradlew.bat -p native/converter-worker :converter-worker:installDist
flutter build windows
flutter build apk --release
```

To build the full Windows portable bundle, point
`ETOILEBRIDGE_ELECTRON_RESOURCES_DIR` to the legacy packaged resources
containing `converter-worker/` and `runtime/`. The app still uses that worker
for pack scans, editing existing packages, and character workflows.

Android release signing is intentionally loaded from a local secrets directory and is not committed to this repository.
