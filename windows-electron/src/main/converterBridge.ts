import { app } from 'electron'
import { execFile } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import type {
  ActionResult,
  CharacterConvertResult,
  CharacterIconResult,
  CharacterScanResult,
  CharacterSettingsEdit,
  CharacterCropEdit,
  ConvertResult,
  PackConvertResult,
  PackScanResult,
  SavePackRequest,
  SaveCharacterRequest,
  SaveSingleRequest,
  SingleScanResult,
  WorkerEnvelope
} from './types'

export class ConverterBridge {
  private readonly projectRoot: string

  constructor() {
    this.projectRoot = app.isPackaged ? app.getAppPath() : path.resolve(__dirname, '../..')
  }

  getRuntimeInfo(): { javaRuntimePath: string; workerPath: string } {
    const resolved = this.resolveWorkerExecutable()
    return {
      javaRuntimePath: resolved.command,
      workerPath: resolved.libDir
    }
  }

  smokeTest(): Promise<ActionResult<{ status: string }>> {
    return this.runWorker<{ status: string }>(['smoke-test'])
  }

  async scanSingle(source: string, session: string): Promise<ActionResult<SingleScanResult>> {
    return this.runWorker<SingleScanResult>(['scan-single', '--source', source, '--session', session])
  }

  async convertSingle(params: SaveSingleRequest & { output: string }): Promise<ActionResult<ConvertResult>> {
    return this.runWorker<ConvertResult>([
      'convert-single',
      '--workspace',
      params.scan.workspacePath,
      '--output',
      params.output,
      '--request-json',
      JSON.stringify({
        publisherId: params.metadata.publisherId,
        levelId: params.metadata.levelId,
        title: params.metadata.title,
        artist: params.metadata.artist,
        bpmText: params.metadata.bpmText,
        bpmBase: parseNumber(params.metadata.bpmBase),
        charts: params.metadata.charts.map((chart) => ({
          ratingClass: chart.ratingClass,
          difficulty: chart.difficulty,
          chartConstant: parseNumber(chart.chartConstant),
          charter: chart.charter,
          illustrator: chart.illustrator,
          alias: chart.alias,
          affPath: chart.affPath,
          adopted: chart.adopted
        })),
        resources: params.resources,
        appearance: params.appearance,
        preprocess: params.preprocess
      })
    ])
  }

  async scanOfficialPack(source: string, session: string): Promise<ActionResult<PackScanResult>> {
    return this.runWorker<PackScanResult>(['scan-official-pack', '--source', source, '--session', session])
  }

  async scanArcpkgBundle(sources: string[], session: string): Promise<ActionResult<PackScanResult>> {
    return this.runWorker<PackScanResult>(['scan-arcpkg-bundle', '--sources-json', JSON.stringify(sources), '--session', session])
  }

  async scanExistingPack(base: string, addSources: string[], session: string): Promise<ActionResult<PackScanResult>> {
    const args = ['scan-existing-pack', '--base', base, '--session', session]
    if (addSources.length > 0) {
      args.push('--sources-json', JSON.stringify(addSources))
    }
    return this.runWorker<PackScanResult>(args)
  }

  async savePack(params: SavePackRequest & { output: string }): Promise<ActionResult<PackConvertResult>> {
    const requestJson = JSON.stringify({
      mode: params.scan.mode,
      publisherId: params.settings.publisherId,
      outputFileName: params.settings.outputFileName,
      packName: params.settings.packName,
      packId: params.settings.packId,
      packIdentifier: params.settings.packIdentifier,
      packImagePath: params.settings.packImagePath,
      entries: params.settings.entries.map((entry) => ({
        key: entry.key,
        enabled: entry.enabled,
        title: entry.title,
        artist: entry.artist,
        levelId: entry.levelId,
        charts: entry.charts.map((chart) => ({
          ratingClass: chart.ratingClass,
          enabled: chart.enabled,
          difficulty: chart.difficulty,
          chartConstant: parseNumber(chart.chartConstant),
          charter: chart.charter,
          illustrator: chart.illustrator
        }))
      })),
      appearance: params.settings.appearance,
      preprocess: params.settings.preprocess
    })

    if (params.scan.mode === 'official') {
      if (!params.scan.workspacePath) return { ok: false, error: 'Pack workspace is missing.' }
      return this.runWorker<PackConvertResult>([
        'save-official-pack',
        '--workspace',
        params.scan.workspacePath,
        '--output',
        params.output,
        '--request-json',
        requestJson
      ])
    }
    if (params.scan.mode === 'existing') {
      if (!params.scan.basePackPath) return { ok: false, error: 'Base pack is missing.' }
      const args = [
        'save-existing-pack',
        '--base',
        params.scan.basePackPath,
        '--output',
        params.output,
        '--request-json',
        requestJson
      ]
      if (params.scan.addWorkspacePath) {
        args.splice(3, 0, '--add-workspace', params.scan.addWorkspacePath)
      }
      return this.runWorker<PackConvertResult>(args)
    }
    if (!params.scan.workspacePath) return { ok: false, error: 'Pack workspace is missing.' }
    return this.runWorker<PackConvertResult>([
      'save-arcpkg-bundle',
      '--workspace',
      params.scan.workspacePath,
      '--output',
      params.output,
      '--request-json',
      requestJson
    ])
  }

  async scanCharacterImage(source: string, session: string): Promise<ActionResult<CharacterScanResult>> {
    return this.runWorker<CharacterScanResult>(['scan-character-image', '--source', source, '--session', session])
  }

  async scanCharacterArcpkg(source: string, session: string): Promise<ActionResult<CharacterScanResult>> {
    return this.runWorker<CharacterScanResult>(['scan-character-arcpkg', '--source', source, '--session', session])
  }

  async generateCharacterIcon(imagePath: string, outputPath: string, crop: CharacterCropEdit): Promise<ActionResult<CharacterIconResult>> {
    return this.runWorker<CharacterIconResult>([
      'generate-character-icon',
      '--request-json',
      JSON.stringify({
        imagePath,
        outputPath,
        centerX: crop.centerX,
        centerY: crop.centerY,
        cropSize: crop.cropSize,
        outputSize: 256
      })
    ])
  }

  async saveCharacter(params: SaveCharacterRequest & { output: string }): Promise<ActionResult<CharacterConvertResult>> {
    const settings: CharacterSettingsEdit = params.settings
    return this.runWorker<CharacterConvertResult>([
      'save-character-package',
      '--output',
      params.output,
      '--request-json',
      JSON.stringify({
        publisherId: settings.publisherId,
        characterId: settings.characterId,
        directory: settings.directory,
        outputFileName: settings.outputFileName,
        defaultName: settings.defaultName,
        zhCnName: settings.zhCnName,
        imagePath: settings.imagePath || params.scan.image?.path,
        iconPath: params.icon?.path || settings.iconPath || params.scan.icon?.path,
        imageFileName: settings.imageFileName,
        iconFileName: settings.iconFileName,
        x: parseNumber(settings.x) ?? 300,
        y: parseNumber(settings.y) ?? 100,
        scale: parseNumber(settings.scale) ?? 0.7
      })
    ])
  }

  private runWorker<T>(args: string[]): Promise<ActionResult<T>> {
    return new Promise((resolve) => {
      let worker: { command: string; prefixArgs: string[]; libDir: string }
      try {
        worker = this.resolveWorkerExecutable()
      } catch (error) {
        resolve({
          ok: false,
          error: error instanceof Error ? error.message : String(error),
          logs: []
        })
        return
      }

      const materialized = this.materializeJsonArgs([...worker.prefixArgs, ...args])
      execFile(
        worker.command,
        materialized.args,
        {
          cwd: app.isPackaged ? process.resourcesPath : this.projectRoot,
          encoding: 'utf8',
          windowsHide: true
        },
        (error, stdout, stderr) => {
          materialized.cleanup()
          if (error) {
            resolve({
              ok: false,
              error: `converter worker failed: ${error.message}`,
              logs: [stderr.trim()].filter(Boolean)
            })
            return
          }
          const trimmed = stdout.trim()
          const jsonStart = trimmed.indexOf('{')
          const payload = jsonStart >= 0 ? trimmed.slice(jsonStart) : trimmed
          try {
            const envelope = JSON.parse(payload) as WorkerEnvelope<T>
            resolve({
              ok: envelope.ok,
              data: envelope.data,
              error: envelope.error,
              warnings: envelope.warnings ?? [],
              logs: [...(envelope.logs ?? []), stderr.trim()].filter(Boolean)
            })
          } catch {
            resolve({
              ok: false,
              error: 'converter worker returned invalid JSON',
              logs: [stdout, stderr].filter(Boolean)
            })
          }
        }
      )
    })
  }

  private resolveWorkerExecutable(): { command: string; prefixArgs: string[]; libDir: string } {
    const mainClass = 'com.zeerqi27.etoilebridge.electron.worker.WorkerMainKt'
    const javaName = process.platform === 'win32' ? 'java.exe' : 'java'
    const packagedJava = path.join(process.resourcesPath, 'runtime', 'bin', javaName)
    const packagedLib = path.join(process.resourcesPath, 'converter-worker', 'lib')

    if (app.isPackaged) {
      if (!fs.existsSync(packagedJava)) {
        throw new Error(`Bundled Java runtime not found: ${packagedJava}. Please reinstall EtoileBridge.`)
      }
      if (!fs.existsSync(packagedLib)) {
        throw new Error(`Bundled converter worker classpath not found: ${packagedLib}. Please reinstall EtoileBridge.`)
      }
      return this.javaWorkerCommand(packagedJava, packagedLib, mainClass)
    }

    const devLib = path.join(this.projectRoot, 'converter-worker', 'build', 'install', 'converter-worker', 'lib')
    if (!fs.existsSync(devLib)) {
      throw new Error(`converter worker classpath not found: ${devLib}. Run npm run worker:build.`)
    }

    const candidates = [
      process.env.ETOILEBRIDGE_JAVA_HOME ? path.join(process.env.ETOILEBRIDGE_JAVA_HOME, 'bin', javaName) : '',
      process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', javaName) : '',
      findOnPath(javaName),
      path.join(this.projectRoot, 'build', 'runtime', 'bin', javaName)
    ].filter(Boolean)

    for (const candidate of candidates) {
      if (fs.existsSync(candidate)) {
        return this.javaWorkerCommand(candidate, devLib, mainClass)
      }
    }

    throw new Error(
      'Java runtime not found. Set ETOILEBRIDGE_JAVA_HOME or JAVA_HOME, install java on PATH, or run npm run prepare:runtime.'
    )
  }

  private javaWorkerCommand(javaPath: string, libDir: string, mainClass: string): { command: string; prefixArgs: string[]; libDir: string } {
    const jars = fs
      .readdirSync(libDir)
      .filter((file) => file.endsWith('.jar'))
      .map((file) => path.join(libDir, file))
      .sort()

    if (jars.length === 0) {
      throw new Error(`converter worker libraries are missing: ${libDir}`)
    }

    return {
      command: javaPath,
      prefixArgs: [
        '-Dfile.encoding=UTF-8',
        '-Dsun.stdout.encoding=UTF-8',
        '-Dsun.stderr.encoding=UTF-8',
        '-cp',
        jars.join(path.delimiter),
        mainClass
      ],
      libDir
    }
  }

  private materializeJsonArgs(args: string[]): { args: string[]; cleanup: () => void } {
    const jsonFlags = new Set(['--request-json', '--sources-json'])
    let tempDir = ''
    const nextArgs = [...args]

    for (let index = 0; index < nextArgs.length - 1; index += 1) {
      const flag = nextArgs[index]
      if (!jsonFlags.has(flag)) continue
      if (!tempDir) {
        tempDir = fs.mkdtempSync(path.join(app.getPath('temp'), 'etoilebridge-worker-args-'))
      }
      const file = path.join(tempDir, `${flag.slice(2)}-${index}.json`)
      fs.writeFileSync(file, nextArgs[index + 1], { encoding: 'utf8' })
      nextArgs[index] = `${flag}-file`
      nextArgs[index + 1] = file
    }

    return {
      args: nextArgs,
      cleanup: () => {
        if (tempDir) {
          fs.rmSync(tempDir, { force: true, recursive: true })
        }
      }
    }
  }
}

function parseNumber(value: string): number | undefined {
  if (!value.trim()) return undefined
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : undefined
}

function findOnPath(executable: string): string {
  const pathValue = process.env.PATH || ''
  const entries = pathValue.split(path.delimiter).filter(Boolean)
  for (const entry of entries) {
    const candidate = path.join(entry, executable)
    if (fs.existsSync(candidate)) return candidate
  }
  return ''
}
