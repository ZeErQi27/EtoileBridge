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
Install .NET SDK 10.0.404 or newer within .NET 10 (or point
`ETOILEBRIDGE_DOTNET` at that SDK's `dotnet` executable). For Windows, also
install Visual Studio's C++ desktop workload and JDK 21. Android needs the
Android NDK and CMake 3.22.1.

Windows and 64-bit Android use the same
[Etoile.Lite v0.1.7 parser](https://github.com/freeze-dolphin/Etoile.Lite/tree/v0.1.7).
The engine is bundled; users do not have to install .NET. The 32-bit ARM
Android app retains the Kotlin converter.

```powershell
flutter pub get
flutter analyze
flutter test
.\android\gradlew.bat -p native/converter-worker :converter-worker:installDist
flutter build windows
flutter build apk --release --build-name 1.2.261120fixedbug
```

To build the full Windows portable bundle, point
`ETOILEBRIDGE_ELECTRON_RESOURCES_DIR` to the legacy packaged resources
containing `converter-worker/` and `runtime/`. The app still uses that worker
for pack scans, editing existing packages, and character workflows.

Android release signing is intentionally loaded from a local secrets directory and is not committed to this repository.

Run the Android export tests on a connected 64-bit device or emulator:

```powershell
.\android\gradlew.bat -p android :app:connectedDebugAndroidTest
```
