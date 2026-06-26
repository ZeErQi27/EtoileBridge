# EtoileBridge Electron

Electron technical validation for the EtoileBridge Windows desktop UI.

## Stack

- Electron
- TypeScript
- React
- Vite
- Kotlin/JVM converter worker
- Copied `converter-core` module

## Project Layout

```text
EtoileBridgeElectron/
  converter-core/       Kotlin/JVM conversion core copy
  converter-worker/     Controlled local JSON worker around converter-core
  resources/            Desktop icon resources
  src/main/             Electron main process, IPC, cache, worker bridge
  src/preload/          Safe renderer API bridge
  src/renderer/         React UI
```

## Development

The worker is launched through a JVM command with an explicit runtime path.
Packaged builds should place a bundled runtime at:

```text
resources/runtime/bin/java.exe
```

For development, set one of these environment variables before running:

```powershell
$env:ETOILEBRIDGE_JAVA_HOME = "C:\Path\To\JDK"
```

Then:

```powershell
npm install
npm run worker:build
npm run dev
```

Build-only check:

```powershell
npm run build
npx tsc --noEmit
.\gradlew.bat :converter-core:test :converter-worker:test
```

## Cache

The Electron prototype stores managed session workspaces under:

```text
%LOCALAPPDATA%\EtoileBridgeElectron\cache
```

Only directories named `session-*` directly under that cache root are cleaned.
User input files, output files, sample folders, and source trees are never
deleted by the cache manager.
