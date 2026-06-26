import { app, BrowserWindow, dialog, ipcMain, Menu, nativeImage, protocol, shell } from 'electron'
import path from 'node:path'
import fs from 'node:fs/promises'
import { randomUUID } from 'node:crypto'
import { ElectronCacheManager } from './cache'
import { ConverterBridge } from './converterBridge'
import type {
  ActionResult,
  AboutInfo,
  AppSettingsInfo,
  CacheInfo,
  CharacterConvertResult,
  CharacterIconResult,
  CharacterScanResult,
  ConvertResult,
  PackConvertResult,
  PackScanResult,
  ResourceInfo,
  SavePackRequest,
  SaveCharacterRequest,
  SaveSingleRequest,
  SelectedResource,
  SingleScanResult,
  UiLanguage,
  UpdateInfo
} from './types'

protocol.registerSchemesAsPrivileged([
  {
    scheme: 'etoile-preview',
    privileges: {
      standard: true,
      secure: true,
      supportFetchAPI: true,
      corsEnabled: false
    }
  }
])

let mainWindow: BrowserWindow | null = null
const cacheManager = new ElectronCacheManager()
const converter = new ConverterBridge()
let previewRegistry: PreviewRegistry
let settings: AppSettings = {}
const knownInputPaths = new Set<string>()

type AppSettings = {
  lastSaveDirectory?: string
  language?: UiLanguage
}

async function createWindow(): Promise<void> {
  const iconPath = app.isPackaged
    ? path.join(process.resourcesPath, 'icon_windows.png')
    : path.join(app.getAppPath(), 'src', 'renderer', 'src', 'assets', 'icon_windows.png')
  Menu.setApplicationMenu(null)
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 940,
    minHeight: 680,
    title: 'EtoileBridge',
    icon: nativeImage.createFromPath(iconPath),
    backgroundColor: '#f7f4ee',
    webPreferences: {
      preload: path.join(__dirname, '../preload/index.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  })

  if (process.env.ELECTRON_RENDERER_URL) {
    await mainWindow.loadURL(process.env.ELECTRON_RENDERER_URL)
  } else {
    await mainWindow.loadFile(path.join(__dirname, '../renderer/index.html'))
  }
}

app.whenReady().then(async () => {
  previewRegistry = new PreviewRegistry(cacheManager.cacheRoot)
  protocol.handle('etoile-preview', async (request) => previewRegistry.handle(request.url))
  await cacheManager.ensureRoot()
  await cacheManager.cleanupStale()
  settings = await loadSettings()
  if (process.argv.includes('--smoke-test')) {
    const runtimeInfo = converter.getRuntimeInfo()
    const result = await converter.smokeTest()
    if (!result.ok) {
      console.error(JSON.stringify({ ok: false, runtimeInfo, error: result.error, logs: result.logs ?? [] }))
      app.exit(1)
      return
    }
    console.log(JSON.stringify({ ok: true, runtimeInfo, data: result.data, logs: result.logs ?? [] }))
    app.exit(0)
    return
  }
  await createWindow()
})

app.on('window-all-closed', async () => {
  await cacheManager.cleanupActiveSessions()
  if (process.platform !== 'darwin') app.quit()
})

app.on('before-quit', () => {
  void cacheManager.cleanupActiveSessions()
})

ipcMain.handle('cache:info', async (): Promise<CacheInfo> => ({
  root: cacheManager.cacheRoot,
  sizeBytes: await cacheManager.sizeBytes(),
  lastCleanup: cacheManager.lastCleanup
}))

ipcMain.handle('cache:cleanup', async (): Promise<CacheInfo> => {
  await cacheManager.cleanupInactiveSessions(0)
  return {
    root: cacheManager.cacheRoot,
    sizeBytes: await cacheManager.sizeBytes(),
    lastCleanup: cacheManager.lastCleanup
  }
})

ipcMain.handle('settings:get', async (): Promise<AppSettingsInfo> => ({
  language: settings.language ?? 'zh-CN',
  lastSaveDirectory: settings.lastSaveDirectory,
  settingsPath: settingsFilePath()
}))

ipcMain.handle('settings:setLanguage', async (_event, language: UiLanguage): Promise<AppSettingsInfo> => {
  settings = { ...settings, language: language === 'en' ? 'en' : 'zh-CN' }
  await saveSettings().catch(() => undefined)
  return {
    language: settings.language ?? 'zh-CN',
    lastSaveDirectory: settings.lastSaveDirectory,
    settingsPath: settingsFilePath()
  }
})

ipcMain.handle('settings:getCacheInfo', async (): Promise<CacheInfo> => ({
  root: cacheManager.cacheRoot,
  sizeBytes: await cacheManager.sizeBytes(),
  lastCleanup: cacheManager.lastCleanup
}))

ipcMain.handle('settings:clearCache', async (): Promise<CacheInfo> => {
  await cacheManager.cleanupInactiveSessions(0)
  return {
    root: cacheManager.cacheRoot,
    sizeBytes: await cacheManager.sizeBytes(),
    lastCleanup: cacheManager.lastCleanup
  }
})

ipcMain.handle('settings:getAboutInfo', async (): Promise<AboutInfo> => {
  const packageJson = await readPackageJson()
  let runtimeInfo: { javaRuntimePath: string; workerPath: string }
  try {
    runtimeInfo = converter.getRuntimeInfo()
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    runtimeInfo = {
      javaRuntimePath: message,
      workerPath: message
    }
  }
  return {
    appName: 'EtoileBridge',
    version: packageJson.version ?? '0.0.0',
    electron: process.versions.electron ?? '',
    chromium: process.versions.chrome ?? '',
    node: process.versions.node ?? '',
    packaged: app.isPackaged,
    resourcesPath: process.resourcesPath,
    cacheRoot: cacheManager.cacheRoot,
    userDataPath: app.getPath('userData'),
    javaRuntimePath: runtimeInfo.javaRuntimePath,
    workerPath: runtimeInfo.workerPath,
    description: 'ArcCreate package toolkit'
  }
})

ipcMain.handle('settings:checkUpdates', async (): Promise<UpdateInfo> => ({
  configured: false,
  message: 'No update source configured yet.'
}))

ipcMain.handle('single:chooseZipAndScan', async (): Promise<ActionResult<SingleScanResult>> => {
  const result = await dialog.showOpenDialog(mainWindow!, {
    title: '选择 ZIP',
    properties: ['openFile'],
    filters: [{ name: 'ZIP', extensions: ['zip'] }]
  })
  if (result.canceled || result.filePaths.length === 0) return canceled()
  return scanSingle(result.filePaths[0])
})

ipcMain.handle('single:chooseFolderAndScan', async (): Promise<ActionResult<SingleScanResult>> => {
  const result = await dialog.showOpenDialog(mainWindow!, {
    title: '选择歌曲文件夹',
    properties: ['openDirectory']
  })
  if (result.canceled || result.filePaths.length === 0) return canceled()
  return scanSingle(result.filePaths[0])
})

ipcMain.handle('single:rescan', async (_event, sourcePath: string): Promise<ActionResult<SingleScanResult>> => {
  if (!sourcePath) return { ok: false, error: '没有可重新扫描的输入。' }
  return scanSingle(sourcePath)
})

ipcMain.handle('resource:choose', async (_event, kind: keyof SaveSingleRequest['resources']): Promise<ActionResult<SelectedResource>> => {
  const options = resourceDialogOptions(kind)
  const result = await dialog.showOpenDialog(mainWindow!, options)
  if (result.canceled || result.filePaths.length === 0) return { ok: true, data: { canceled: true } }
  const file = result.filePaths[0]
  const info = await describeFile(file, 'Manual override')
  if (isPreviewable(file)) {
    info.previewUrl = previewRegistry.register(file, { selectedUserFile: true })
  }
  return { ok: true, data: info }
})

ipcMain.handle('pack:chooseOfficialZipAndScan', async (): Promise<ActionResult<PackScanResult>> => {
  const result = await dialog.showOpenDialog(mainWindow!, {
    title: '选择官方曲包 ZIP',
    properties: ['openFile'],
    filters: [{ name: 'ZIP', extensions: ['zip'] }]
  })
  if (result.canceled || result.filePaths.length === 0) return canceled()
  return scanOfficialPack(result.filePaths[0])
})

ipcMain.handle('pack:chooseOfficialFolderAndScan', async (): Promise<ActionResult<PackScanResult>> => {
  const result = await dialog.showOpenDialog(mainWindow!, {
    title: '选择官方曲包文件夹',
    properties: ['openDirectory']
  })
  if (result.canceled || result.filePaths.length === 0) return canceled()
  return scanOfficialPack(result.filePaths[0])
})

ipcMain.handle('pack:chooseArcpkgFilesAndScan', async (): Promise<ActionResult<PackScanResult>> => {
  const result = await dialog.showOpenDialog(mainWindow!, {
    title: '选择 arcpkg 文件',
    properties: ['openFile', 'multiSelections'],
    filters: [{ name: 'ArcCreate Package', extensions: ['arcpkg'] }]
  })
  if (result.canceled || result.filePaths.length === 0) return canceled()
  return scanArcpkgBundle(result.filePaths)
})

ipcMain.handle('pack:chooseArcpkgFolderAndScan', async (): Promise<ActionResult<PackScanResult>> => {
  const result = await dialog.showOpenDialog(mainWindow!, {
    title: '选择 arcpkg 文件夹',
    properties: ['openDirectory']
  })
  if (result.canceled || result.filePaths.length === 0) return canceled()
  return scanArcpkgBundle(result.filePaths)
})

ipcMain.handle('pack:chooseExistingBaseAndScan', async (): Promise<ActionResult<PackScanResult>> => {
  const result = await dialog.showOpenDialog(mainWindow!, {
    title: '选择已有曲包 arcpkg',
    properties: ['openFile'],
    filters: [{ name: 'ArcCreate Package', extensions: ['arcpkg'] }]
  })
  if (result.canceled || result.filePaths.length === 0) return canceled()
  return scanExistingPack(result.filePaths[0], [])
})

ipcMain.handle('pack:chooseExistingAddFilesAndScan', async (_event, basePackPath: string): Promise<ActionResult<PackScanResult>> => {
  if (!basePackPath) return { ok: false, error: '请先选择已有曲包。' }
  const result = await dialog.showOpenDialog(mainWindow!, {
    title: '选择要追加的 arcpkg',
    properties: ['openFile', 'multiSelections'],
    filters: [{ name: 'ArcCreate Package', extensions: ['arcpkg'] }]
  })
  if (result.canceled || result.filePaths.length === 0) return canceled()
  return scanExistingPack(basePackPath, result.filePaths)
})

ipcMain.handle('pack:chooseExistingAddFolderAndScan', async (_event, basePackPath: string): Promise<ActionResult<PackScanResult>> => {
  if (!basePackPath) return { ok: false, error: '请先选择已有曲包。' }
  const result = await dialog.showOpenDialog(mainWindow!, {
    title: '选择追加 arcpkg 文件夹',
    properties: ['openDirectory']
  })
  if (result.canceled || result.filePaths.length === 0) return canceled()
  return scanExistingPack(basePackPath, result.filePaths)
})

ipcMain.handle('pack:chooseCover', async (): Promise<ActionResult<SelectedResource>> => {
  const result = await dialog.showOpenDialog(mainWindow!, {
    title: '选择曲包封面',
    properties: ['openFile'],
    filters: [{ name: 'Images', extensions: ['png', 'jpg', 'jpeg', 'webp'] }]
  })
  if (result.canceled || result.filePaths.length === 0) return { ok: true, data: { canceled: true } }
  const file = result.filePaths[0]
  knownInputPaths.add(path.resolve(file))
  const info = await describeFile(file, 'Manual pack image')
  if (isPreviewable(file)) {
    info.previewUrl = previewRegistry.register(file, { selectedUserFile: true })
  }
  return { ok: true, data: info }
})

ipcMain.handle('character:chooseImageAndScan', async (): Promise<ActionResult<CharacterScanResult>> => {
  const result = await dialog.showOpenDialog(mainWindow!, {
    title: 'Select character image',
    properties: ['openFile'],
    filters: [{ name: 'Images', extensions: ['png', 'jpg', 'jpeg', 'webp'] }]
  })
  if (result.canceled || result.filePaths.length === 0) return canceled()
  return scanCharacterImage(result.filePaths[0])
})

ipcMain.handle('character:chooseArcpkgAndScan', async (): Promise<ActionResult<CharacterScanResult>> => {
  const result = await dialog.showOpenDialog(mainWindow!, {
    title: 'Select character arcpkg',
    properties: ['openFile'],
    filters: [{ name: 'ArcCreate Package', extensions: ['arcpkg'] }]
  })
  if (result.canceled || result.filePaths.length === 0) return canceled()
  return scanCharacterArcpkg(result.filePaths[0])
})

ipcMain.handle('character:chooseImage', async (): Promise<ActionResult<SelectedResource>> => {
  const result = await dialog.showOpenDialog(mainWindow!, {
    title: 'Select character image',
    properties: ['openFile'],
    filters: [{ name: 'Images', extensions: ['png', 'jpg', 'jpeg', 'webp'] }]
  })
  if (result.canceled || result.filePaths.length === 0) return { ok: true, data: { canceled: true } }
  const file = result.filePaths[0]
  knownInputPaths.add(path.resolve(file))
  const info = await describeFile(file, 'Manual character image')
  if (isPreviewable(file)) {
    info.previewUrl = previewRegistry.register(file, { selectedUserFile: true })
  }
  return { ok: true, data: info }
})

ipcMain.handle('character:generateIcon', async (_event, scan: CharacterScanResult, crop: { centerX: number; centerY: number; cropSize: number }): Promise<ActionResult<CharacterIconResult>> => {
  const imagePath = scan.image?.path
  if (!imagePath) return { ok: false, error: 'Character image is missing.' }
  const sessionPath = path.dirname(path.dirname(scan.workspacePath))
  const outputPath = path.join(scan.workspacePath, 'generated-character-icon.png')
  const result = await converter.generateCharacterIcon(imagePath, outputPath, crop)
  if (!result.ok || !result.data) return result
  return {
    ...result,
    data: {
      ...result.data,
      icon: addPreview(result.data.icon, sessionPath) ?? result.data.icon
    }
  }
})

ipcMain.handle('path:openInputLocation', async (_event, sourcePath: string): Promise<ActionResult<boolean>> => {
  const normalized = path.resolve(sourcePath || '')
  if (!knownInputPaths.has(normalized)) return { ok: false, error: 'Path is not a known selected input.' }
  const stat = await fs.stat(normalized).catch(() => null)
  if (!stat) return { ok: false, error: 'Path does not exist.' }
  if (stat.isDirectory()) {
    const error = await shell.openPath(normalized)
    return error ? { ok: false, error } : { ok: true, data: true }
  }
  shell.showItemInFolder(normalized)
  return { ok: true, data: true }
})

ipcMain.handle('pack:save', async (_event, request: SavePackRequest): Promise<ActionResult<PackConvertResult>> => savePack(request))

ipcMain.handle('character:save', async (_event, request: SaveCharacterRequest): Promise<ActionResult<CharacterConvertResult>> => saveCharacter(request))

ipcMain.handle('single:save', async (_event, request: SaveSingleRequest): Promise<ActionResult<ConvertResult>> => {
  if (!request.scan.workspacePath) return { ok: false, error: '请先扫描输入。' }
  const result = await dialog.showSaveDialog(mainWindow!, {
    title: '保存 arcpkg',
    defaultPath: await suggestedOutputPath(request),
    filters: [{ name: 'ArcCreate Package', extensions: ['arcpkg'] }],
    properties: ['createDirectory', 'showOverwriteConfirmation']
  })
  if (result.canceled || !result.filePath) return { ok: true, warnings: ['已取消保存。'] }

  const outputPath = ensureArcpkgExtension(result.filePath)
  const overwriteAlreadyConfirmedByNativeDialog = process.platform === 'win32'
  if (!overwriteAlreadyConfirmedByNativeDialog && await exists(outputPath)) {
    const overwrite = await dialog.showMessageBox(mainWindow!, {
      type: 'warning',
      buttons: ['覆盖', '取消'],
      defaultId: 1,
      cancelId: 1,
      title: '确认覆盖',
      message: '目标文件已存在。是否覆盖？',
      detail: outputPath
    })
    if (overwrite.response !== 0) return { ok: true, warnings: ['已取消覆盖。'] }
  }

  const tempOutputDir = path.join(path.dirname(outputPath), `.etoilebridge-tmp-${randomUUID()}`)
  const tempOutput = path.join(tempOutputDir, path.basename(outputPath))
  await fs.mkdir(tempOutputDir, { recursive: true })
  const converted = await converter.convertSingle({ ...request, output: tempOutput })
  if (converted.ok && converted.data?.outputPath) {
    const producedOutput = converted.data.outputPath
    const stat = await fs.stat(producedOutput).catch(() => null)
    if (!stat || stat.size <= 0) {
      await removeIfExists(tempOutputDir)
      return { ok: false, error: '输出文件不存在或为空。', logs: converted.logs }
    }
    try {
      await replaceFileSafely(producedOutput, outputPath)
    } catch (error) {
      await removeIfExists(tempOutputDir)
      return { ok: false, error: `写入目标文件失败：${error instanceof Error ? error.message : String(error)}`, logs: converted.logs }
    }
    await removeIfExists(tempOutputDir)
    const finalStat = await fs.stat(outputPath).catch(() => null)
    if (!finalStat || finalStat.size <= 0) {
      return { ok: false, error: '保存后的目标文件不存在或为空。', logs: converted.logs }
    }
    const sessionPath = path.dirname(path.dirname(request.scan.workspacePath))
    await cacheManager.cleanupSession(sessionPath)
    previewRegistry.clearSession(sessionPath)
    await rememberSaveDirectory(path.dirname(outputPath))
    return {
      ...converted,
      data: {
        ...converted.data,
        outputPath,
        sizeBytes: finalStat.size,
        workspaceCleaned: true
      }
    }
  }
  await removeIfExists(tempOutputDir)
  return converted
})

async function scanSingle(sourcePath: string): Promise<ActionResult<SingleScanResult>> {
  knownInputPaths.add(path.resolve(sourcePath))
  const session = await cacheManager.createSession()
  const result = await converter.scanSingle(sourcePath, session)
  if (!result.ok || !result.data) return result
  return {
    ...result,
    data: registerScanPreviews(result.data)
  }
}

async function scanOfficialPack(sourcePath: string): Promise<ActionResult<PackScanResult>> {
  knownInputPaths.add(path.resolve(sourcePath))
  const session = await cacheManager.createSession()
  const result = await converter.scanOfficialPack(sourcePath, session)
  return registerPackResult(result)
}

async function scanArcpkgBundle(sourcePaths: string[]): Promise<ActionResult<PackScanResult>> {
  sourcePaths.forEach((source) => knownInputPaths.add(path.resolve(source)))
  const session = await cacheManager.createSession()
  const result = await converter.scanArcpkgBundle(sourcePaths, session)
  return registerPackResult(result)
}

async function scanExistingPack(basePackPath: string, addSourcePaths: string[]): Promise<ActionResult<PackScanResult>> {
  knownInputPaths.add(path.resolve(basePackPath))
  addSourcePaths.forEach((source) => knownInputPaths.add(path.resolve(source)))
  const session = await cacheManager.createSession()
  const result = await converter.scanExistingPack(basePackPath, addSourcePaths, session)
  return registerPackResult(result)
}

async function scanCharacterImage(sourcePath: string): Promise<ActionResult<CharacterScanResult>> {
  knownInputPaths.add(path.resolve(sourcePath))
  const session = await cacheManager.createSession()
  const result = await converter.scanCharacterImage(sourcePath, session)
  return registerCharacterResult(result)
}

async function scanCharacterArcpkg(sourcePath: string): Promise<ActionResult<CharacterScanResult>> {
  knownInputPaths.add(path.resolve(sourcePath))
  const session = await cacheManager.createSession()
  const result = await converter.scanCharacterArcpkg(sourcePath, session)
  return registerCharacterResult(result)
}

function registerPackResult(result: ActionResult<PackScanResult>): ActionResult<PackScanResult> {
  if (!result.ok || !result.data) return result
  return {
    ...result,
    data: registerPackPreviews(result.data)
  }
}

function registerPackPreviews(scan: PackScanResult): PackScanResult {
  const sessionPath =
    scan.workspacePath ? path.dirname(path.dirname(scan.workspacePath)) :
      scan.addWorkspacePath ? path.dirname(path.dirname(scan.addWorkspacePath)) :
        undefined
  return {
    ...scan,
    packImage: addPreview(scan.packImage, sessionPath),
    entries: scan.entries.map((entry) => ({
      ...entry,
      jacket: addPreview(entry.jacket, sessionPath),
      background: addPreview(entry.background, sessionPath)
    }))
  }
}

function registerCharacterResult(result: ActionResult<CharacterScanResult>): ActionResult<CharacterScanResult> {
  if (!result.ok || !result.data) return result
  const sessionPath = path.dirname(path.dirname(result.data.workspacePath))
  return {
    ...result,
    data: {
      ...result.data,
      image: addPreview(result.data.image, sessionPath),
      icon: addPreview(result.data.icon, sessionPath)
    }
  }
}

async function savePack(request: SavePackRequest): Promise<ActionResult<PackConvertResult>> {
  const result = await dialog.showSaveDialog(mainWindow!, {
    title: '保存 arcpkg',
    defaultPath: await suggestedPackOutputPath(request),
    filters: [{ name: 'ArcCreate Package', extensions: ['arcpkg'] }],
    properties: ['createDirectory', 'showOverwriteConfirmation']
  })
  if (result.canceled || !result.filePath) return { ok: true, warnings: ['已取消保存。'] }

  const outputPath = ensureArcpkgExtension(result.filePath)
  const overwriteAlreadyConfirmedByNativeDialog = process.platform === 'win32'
  if (!overwriteAlreadyConfirmedByNativeDialog && await exists(outputPath)) {
    const overwrite = await dialog.showMessageBox(mainWindow!, {
      type: 'warning',
      buttons: ['覆盖', '取消'],
      defaultId: 1,
      cancelId: 1,
      title: '确认覆盖',
      message: '目标文件已存在。是否覆盖？',
      detail: outputPath
    })
    if (overwrite.response !== 0) return { ok: true, warnings: ['已取消覆盖。'] }
  }

  const tempOutputDir = path.join(path.dirname(outputPath), `.etoilebridge-tmp-${randomUUID()}`)
  const tempOutput = path.join(tempOutputDir, path.basename(outputPath))
  await fs.mkdir(tempOutputDir, { recursive: true })
  const converted = await converter.savePack({ ...request, output: tempOutput })
  if (converted.ok && converted.data?.outputPath) {
    const producedOutput = converted.data.outputPath
    const stat = await fs.stat(producedOutput).catch(() => null)
    if (!stat || stat.size <= 0) {
      await removeIfExists(tempOutputDir)
      return { ok: false, error: '输出文件不存在或为空。', logs: converted.logs }
    }
    try {
      await replaceFileSafely(producedOutput, outputPath)
    } catch (error) {
      await removeIfExists(tempOutputDir)
      return { ok: false, error: `写入目标文件失败：${error instanceof Error ? error.message : String(error)}`, logs: converted.logs }
    }
    await removeIfExists(tempOutputDir)
    const finalStat = await fs.stat(outputPath).catch(() => null)
    if (!finalStat || finalStat.size <= 0) {
      return { ok: false, error: '保存后的目标文件不存在或为空。', logs: converted.logs }
    }
    await cleanupPackSessions(request.scan)
    await rememberSaveDirectory(path.dirname(outputPath))
    knownInputPaths.add(path.resolve(outputPath))
    return {
      ...converted,
      data: {
        ...converted.data,
        outputPath,
        sizeBytes: finalStat.size,
        workspaceCleaned: true
      }
    }
  }
  await removeIfExists(tempOutputDir)
  return converted
}

async function saveCharacter(request: SaveCharacterRequest): Promise<ActionResult<CharacterConvertResult>> {
  const result = await dialog.showSaveDialog(mainWindow!, {
    title: 'Save arcpkg',
    defaultPath: await suggestedCharacterOutputPath(request),
    filters: [{ name: 'ArcCreate Package', extensions: ['arcpkg'] }],
    properties: ['createDirectory', 'showOverwriteConfirmation']
  })
  if (result.canceled || !result.filePath) return { ok: true, warnings: ['Save canceled.'] }

  const outputPath = ensureArcpkgExtension(result.filePath)
  const overwriteAlreadyConfirmedByNativeDialog = process.platform === 'win32'
  if (!overwriteAlreadyConfirmedByNativeDialog && await exists(outputPath)) {
    const overwrite = await dialog.showMessageBox(mainWindow!, {
      type: 'warning',
      buttons: ['Overwrite', 'Cancel'],
      defaultId: 1,
      cancelId: 1,
      title: 'Confirm overwrite',
      message: 'The target file already exists. Overwrite it?',
      detail: outputPath
    })
    if (overwrite.response !== 0) return { ok: true, warnings: ['Overwrite canceled.'] }
  }

  const tempOutputDir = path.join(path.dirname(outputPath), `.etoilebridge-tmp-${randomUUID()}`)
  const tempOutput = path.join(tempOutputDir, path.basename(outputPath))
  await fs.mkdir(tempOutputDir, { recursive: true })
  const converted = await converter.saveCharacter({ ...request, output: tempOutput })
  if (converted.ok && converted.data?.outputPath) {
    const producedOutput = converted.data.outputPath
    const stat = await fs.stat(producedOutput).catch(() => null)
    if (!stat || stat.size <= 0) {
      await removeIfExists(tempOutputDir)
      return { ok: false, error: 'Output file is missing or empty.', logs: converted.logs }
    }
    try {
      await replaceFileSafely(producedOutput, outputPath)
    } catch (error) {
      await removeIfExists(tempOutputDir)
      return { ok: false, error: `Unable to write target file: ${error instanceof Error ? error.message : String(error)}`, logs: converted.logs }
    }
    await removeIfExists(tempOutputDir)
    const finalStat = await fs.stat(outputPath).catch(() => null)
    if (!finalStat || finalStat.size <= 0) {
      return { ok: false, error: 'Saved file is missing or empty.', logs: converted.logs }
    }
    const sessionPath = path.dirname(path.dirname(request.scan.workspacePath))
    await cacheManager.cleanupSession(sessionPath)
    previewRegistry.clearSession(sessionPath)
    await rememberSaveDirectory(path.dirname(outputPath))
    knownInputPaths.add(path.resolve(outputPath))
    return {
      ...converted,
      data: {
        ...converted.data,
        outputPath,
        sizeBytes: finalStat.size,
        workspaceCleaned: true
      }
    }
  }
  await removeIfExists(tempOutputDir)
  return converted
}

async function cleanupPackSessions(scan: PackScanResult): Promise<void> {
  const sessions = new Set<string>()
  if (scan.workspacePath) sessions.add(path.dirname(path.dirname(scan.workspacePath)))
  if (scan.addWorkspacePath) sessions.add(path.dirname(path.dirname(scan.addWorkspacePath)))
  for (const sessionPath of sessions) {
    await cacheManager.cleanupSession(sessionPath)
    previewRegistry.clearSession(sessionPath)
  }
}

function registerScanPreviews(scan: SingleScanResult): SingleScanResult {
  const sessionPath = path.dirname(path.dirname(scan.workspacePath))
  return {
    ...scan,
    jacket: addPreview(scan.jacket, sessionPath),
    background: addPreview(scan.background, sessionPath)
  }
}

function addPreview(resource: ResourceInfo | undefined, sessionPath?: string): ResourceInfo | undefined {
  if (!resource?.path || !isPreviewable(resource.path)) return resource
  return {
    ...resource,
    previewUrl: previewRegistry.register(resource.path, sessionPath ? { sessionPath } : { selectedUserFile: true })
  }
}

async function describeFile(filePath: string, source: string): Promise<ResourceInfo> {
  const stat = await fs.stat(filePath)
  const image = isPreviewable(filePath) ? nativeImage.createFromPath(filePath) : null
  const size = image && !image.isEmpty() ? image.getSize() : null
  return {
    path: filePath,
    name: path.basename(filePath),
    source,
    sizeBytes: stat.size,
    width: size?.width,
    height: size?.height
  }
}

function resourceDialogOptions(kind: keyof SaveSingleRequest['resources']): Electron.OpenDialogOptions {
  const imageFilters = [{ name: 'Images', extensions: ['png', 'jpg', 'jpeg', 'webp'] }]
  switch (kind) {
    case 'audioPath':
      return { title: '选择音频', properties: ['openFile'], filters: [{ name: 'Audio', extensions: ['ogg', 'wav'] }] }
    case 'jacketPath':
      return { title: '选择曲绘', properties: ['openFile'], filters: imageFilters }
    case 'backgroundPath':
      return { title: '选择背景', properties: ['openFile'], filters: imageFilters }
    case 'songlistPath':
      return { title: '选择 songlist', properties: ['openFile'], filters: [{ name: 'songlist', extensions: ['json', 'txt', 'slst'] }] }
    case 'packlistPath':
      return { title: '选择 packlist', properties: ['openFile'], filters: [{ name: 'packlist', extensions: ['json', 'txt'] }] }
    default:
      return { title: '选择文件', properties: ['openFile'] }
  }
}

function sanitizeFileName(name: string): string {
  return name.replace(/[\\/:*?"<>|]+/g, '_').trim()
}

async function suggestedOutputPath(request: SaveSingleRequest): Promise<string> {
  const baseName = sanitizeFileName(request.metadata.levelId || request.scan.songId || 'song') || 'song'
  const directory = await resolveInitialSaveDirectory(request)
  return path.join(directory, `${baseName.replace(/\.arcpkg$/i, '')}.arcpkg`)
}

async function suggestedPackOutputPath(request: SavePackRequest): Promise<string> {
  const baseName = sanitizeFileName(
    request.settings.outputFileName ||
      `${request.settings.publisherId || 'etoilebridge'}.${request.settings.packId || request.scan.packId || 'pack'}.arcpkg`
  )
  const directory = await resolveInitialPackSaveDirectory(request)
  return path.join(directory, `${baseName.replace(/\.arcpkg$/i, '')}.arcpkg`)
}

async function suggestedCharacterOutputPath(request: SaveCharacterRequest): Promise<string> {
  const baseName = sanitizeFileName(
    request.settings.outputFileName ||
      `${request.settings.publisherId || 'etoilebridge'}.${request.settings.characterId || request.scan.characterId || 'character'}.arcpkg`
  )
  const directory = await resolveInitialCharacterSaveDirectory(request)
  return path.join(directory, `${baseName.replace(/\.arcpkg$/i, '')}.arcpkg`)
}

async function resolveInitialSaveDirectory(request: SaveSingleRequest): Promise<string> {
  const candidates = [
    settings.lastSaveDirectory,
    app.getPath('downloads'),
    inputParentDirectory(request.scan.sourcePath),
    app.getPath('home')
  ].filter(Boolean) as string[]
  for (const candidate of candidates) {
    const resolved = path.resolve(candidate)
    if (isInsideOrSame(resolved, cacheManager.cacheRoot)) continue
    const stat = await fs.stat(resolved).catch(() => null)
    if (stat?.isDirectory()) return resolved
  }
  return app.getPath('home')
}

async function resolveInitialPackSaveDirectory(request: SavePackRequest): Promise<string> {
  const sourcePath = request.scan.sourcePath || request.scan.basePackPath
  const candidates = [
    settings.lastSaveDirectory,
    app.getPath('downloads'),
    inputParentDirectory(sourcePath),
    app.getPath('home')
  ].filter(Boolean) as string[]
  for (const candidate of candidates) {
    const resolved = path.resolve(candidate)
    if (isInsideOrSame(resolved, cacheManager.cacheRoot)) continue
    const stat = await fs.stat(resolved).catch(() => null)
    if (stat?.isDirectory()) return resolved
  }
  return app.getPath('home')
}

async function resolveInitialCharacterSaveDirectory(request: SaveCharacterRequest): Promise<string> {
  const candidates = [
    settings.lastSaveDirectory,
    app.getPath('downloads'),
    inputParentDirectory(request.scan.sourcePath),
    app.getPath('home')
  ].filter(Boolean) as string[]
  for (const candidate of candidates) {
    const resolved = path.resolve(candidate)
    if (isInsideOrSame(resolved, cacheManager.cacheRoot)) continue
    const stat = await fs.stat(resolved).catch(() => null)
    if (stat?.isDirectory()) return resolved
  }
  return app.getPath('home')
}

function inputParentDirectory(sourcePath: string | undefined): string | undefined {
  if (!sourcePath) return undefined
  return path.extname(sourcePath) ? path.dirname(sourcePath) : sourcePath
}

function ensureArcpkgExtension(filePath: string): string {
  return filePath.toLowerCase().endsWith('.arcpkg') ? filePath : `${filePath}.arcpkg`
}

async function exists(filePath: string): Promise<boolean> {
  try {
    await fs.access(filePath)
    return true
  } catch {
    return false
  }
}

async function removeIfExists(filePath: string): Promise<void> {
  await fs.rm(filePath, { force: true, recursive: true }).catch(() => undefined)
}

async function replaceFileSafely(tempPath: string, targetPath: string): Promise<void> {
  if (!(await exists(targetPath))) {
    await fs.rename(tempPath, targetPath)
    return
  }

  const backupPath = `${targetPath}.bak-${randomUUID()}`
  await fs.rename(targetPath, backupPath)
  try {
    await fs.rename(tempPath, targetPath)
    await removeIfExists(backupPath)
  } catch (error) {
    await fs.rename(backupPath, targetPath).catch(() => undefined)
    throw error
  }
}

async function loadSettings(): Promise<AppSettings> {
  const text = await fs.readFile(settingsFilePath(), 'utf8').catch(() => '')
  if (!text) return {}
  try {
    const parsed = JSON.parse(text) as AppSettings
    if (!parsed || typeof parsed !== 'object') return {}
    return {
      lastSaveDirectory: typeof parsed.lastSaveDirectory === 'string' ? parsed.lastSaveDirectory : undefined,
      language: parsed.language === 'en' ? 'en' : 'zh-CN'
    }
  } catch {
    return {}
  }
}

async function saveSettings(): Promise<void> {
  const file = settingsFilePath()
  await fs.mkdir(path.dirname(file), { recursive: true })
  await fs.writeFile(file, JSON.stringify(settings, null, 2), 'utf8')
}

async function readPackageJson(): Promise<{ version?: string }> {
  const candidates = [
    path.join(app.getAppPath(), 'package.json'),
    path.join(process.cwd(), 'package.json')
  ]
  for (const candidate of candidates) {
    const text = await fs.readFile(candidate, 'utf8').catch(() => '')
    if (!text) continue
    try {
      return JSON.parse(text) as { version?: string }
    } catch {
      return {}
    }
  }
  return {}
}

async function rememberSaveDirectory(directory: string): Promise<void> {
  const resolved = path.resolve(directory)
  const stat = await fs.stat(resolved).catch(() => null)
  if (!stat?.isDirectory()) return
  if (isInsideOrSame(resolved, cacheManager.cacheRoot)) return
  settings = { ...settings, lastSaveDirectory: resolved }
  await saveSettings().catch(() => undefined)
}

function settingsFilePath(): string {
  return path.join(app.getPath('userData'), 'settings.json')
}

function canceled<T>(): ActionResult<T> {
  return { ok: true, warnings: ['已取消选择。'] }
}

function isPreviewable(filePath: string): boolean {
  return ['.png', '.jpg', '.jpeg', '.webp'].includes(path.extname(filePath).toLowerCase())
}

function mimeFor(filePath: string): string {
  switch (path.extname(filePath).toLowerCase()) {
    case '.png':
      return 'image/png'
    case '.jpg':
    case '.jpeg':
      return 'image/jpeg'
    case '.webp':
      return 'image/webp'
    default:
      return 'application/octet-stream'
  }
}

type PreviewScope = {
  sessionPath?: string
  selectedUserFile?: boolean
}

class PreviewRegistry {
  private readonly entries = new Map<string, { filePath: string; scope: PreviewScope }>()

  constructor(private readonly cacheRoot: string) {}

  register(filePath: string, scope: PreviewScope): string {
    const id = randomUUID()
    this.entries.set(id, { filePath: path.resolve(filePath), scope })
    return `etoile-preview://image/${id}`
  }

  clearSession(sessionPath: string): void {
    const normalized = path.resolve(sessionPath)
    for (const [id, entry] of this.entries) {
      if (entry.scope.sessionPath && path.resolve(entry.scope.sessionPath) === normalized) {
        this.entries.delete(id)
      }
    }
  }

  async handle(url: string): Promise<Response> {
    const id = new URL(url).pathname.replace(/^\//, '')
    const entry = this.entries.get(id)
    if (!entry) return new Response('Preview not found.', { status: 404 })
    if (!this.isAllowed(entry)) return new Response('Preview path is not allowed.', { status: 403 })
    try {
      const bytes = await fs.readFile(entry.filePath)
      return new Response(bytes, {
        headers: {
          'content-type': mimeFor(entry.filePath),
          'cache-control': 'no-store'
        }
      })
    } catch {
      return new Response('Preview file is missing.', { status: 404 })
    }
  }

  private isAllowed(entry: { filePath: string; scope: PreviewScope }): boolean {
    const file = path.resolve(entry.filePath)
    if (entry.scope.selectedUserFile) return true
    if (entry.scope.sessionPath && isInside(file, entry.scope.sessionPath)) return true
    return isInside(file, this.cacheRoot)
  }
}

function isInside(filePath: string, rootPath: string): boolean {
  const root = path.resolve(rootPath)
  const file = path.resolve(filePath)
  const relative = path.relative(root, file)
  return Boolean(relative) && !relative.startsWith('..') && !path.isAbsolute(relative)
}

function isInsideOrSame(filePath: string, rootPath: string): boolean {
  return path.resolve(filePath) === path.resolve(rootPath) || isInside(filePath, rootPath)
}
