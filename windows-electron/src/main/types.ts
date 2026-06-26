export type WorkerEnvelope<T> = {
  ok: boolean
  data?: T
  error?: string
  warnings?: string[]
  logs?: string[]
}

export type AffScan = {
  ratingClass: number
  path: string
  name: string
  adopted: boolean
  warning?: string
}

export type ResourceInfo = {
  path?: string
  name?: string
  source?: string
  sizeBytes?: number
  width?: number
  height?: number
  previewUrl?: string
}

export type SourceKind = 'official-song' | 'official-pack' | 'arccreate-project' | 'arccreate-arcpkg' | 'unknown'

export type ChartInfo = {
  ratingClass: number
  difficulty?: string
  chartConstant?: number
  rating?: number
  ratingPlus?: boolean
  charter?: string
  illustrator?: string
  alias?: string
  affPath?: string
  affName?: string
}

export type SingleScanResult = {
  sourcePath: string
  sourceKind: SourceKind
  inputType: 'ZIP' | 'Folder' | 'File'
  workspacePath: string
  songId?: string
  title?: string
  artist?: string
  bpmText?: string
  bpmBase?: number
  difficulty?: string
  charts: ChartInfo[]
  audio?: ResourceInfo
  jacket?: ResourceInfo
  background?: ResourceInfo
  songlist?: ResourceInfo
  packlist?: ResourceInfo
  affFiles: AffScan[]
  warnings?: string[]
  logs?: string[]
}

export type ConvertResult = {
  outputPath: string
  songId?: string
  sizeBytes?: number
  workspaceCleaned?: boolean
}

export type PackMode = 'official' | 'bundle' | 'existing'

export type PackChartInfo = {
  ratingClass: number
  chartPath?: string
  difficulty?: string
  chartConstant?: number
  charter?: string
  illustrator?: string
  enabled: boolean
  canConvert: boolean
  warnings?: string[]
  failureReason?: string
}

export type PackLevelInfo = {
  key: string
  sourceFile?: string
  directory?: string
  identifier?: string
  songId?: string
  title?: string
  artist?: string
  levelId?: string
  difficultySummary: string
  chartCount: number
  resourceStatus: string
  jacket?: ResourceInfo
  background?: ResourceInfo
  enabled: boolean
  canConvert: boolean
  charts: PackChartInfo[]
  warnings?: string[]
  failureReason?: string
}

export type PackSourceReport = {
  sourceFile: string
  readable: boolean
  levelCount: number
  packEntryCount: number
  packName?: string
  packImagePath?: string
  packImageExists?: boolean
  packLevelIdentifierCount?: number
  packMatchesIndexLevels?: boolean
  failureReason?: string
}

export type PackScanResult = {
  mode: PackMode
  sourcePath?: string
  basePackPath?: string
  addWorkspacePath?: string
  workspacePath?: string
  publisherId?: string
  packName?: string
  packId?: string
  packIdentifier?: string
  packDirectory?: string
  packImage?: ResourceInfo
  entries: PackLevelInfo[]
  sourceReports?: PackSourceReport[]
  existingLevelCount: number
  addedLevelCount: number
  finalLevelCount: number
  renamedConflictCount: number
  warnings?: string[]
  logs?: string[]
}

export type PackSettingsEdit = {
  publisherId: string
  outputFileName: string
  packName: string
  packId: string
  packIdentifier?: string
  packDirectory?: string
  packImagePath?: string
  entries: Array<{
    key: string
    enabled: boolean
    title: string
    artist: string
    levelId: string
    charts: Array<{
      ratingClass: number
      enabled: boolean
      difficulty: string
      chartConstant: string
      charter: string
      illustrator: string
    }>
  }>
  appearance: AppearanceEdit
  preprocess: PreprocessEdit
}

export type PackValidationResult = {
  valid: boolean
  packEntryCount: number
  levelEntryCount: number
  packName?: string
  packIdentifier?: string
  packImageExists: boolean
  levelIdentifiersMatch: boolean
  errors?: string[]
  warnings?: string[]
  logs?: string[]
}

export type PackConvertResult = {
  outputPath: string
  sizeBytes: number
  convertedCount: number
  skippedCount: number
  workspaceCleaned?: boolean
  validation?: PackValidationResult
}

export type CharacterScanResult = {
  sourcePath: string
  sourceKind: 'image' | 'arcpkg'
  inputType: 'ZIP' | 'Folder' | 'File'
  workspacePath: string
  publisherId: string
  characterId: string
  directory: string
  identifier: string
  outputFileName: string
  defaultName: string
  zhCnName?: string
  imagePath?: string
  iconPath?: string
  image?: ResourceInfo
  icon?: ResourceInfo
  imageHasAlpha?: boolean
  x: number
  y: number
  scale: number
  warnings?: string[]
  logs?: string[]
}

export type CharacterSettingsEdit = {
  publisherId: string
  characterId: string
  directory: string
  outputFileName: string
  defaultName: string
  zhCnName: string
  imagePath?: string
  iconPath?: string
  imageFileName?: string
  iconFileName?: string
  x: string
  y: string
  scale: string
}

export type CharacterValidationResult = {
  valid: boolean
  characterEntryCount: number
  identifier?: string
  directory?: string
  defaultName?: string
  imageExists: boolean
  iconExists: boolean
  errors?: string[]
  warnings?: string[]
  logs?: string[]
}

export type CharacterConvertResult = {
  outputPath: string
  sizeBytes: number
  identifier: string
  directory: string
  workspaceCleaned?: boolean
  validation?: CharacterValidationResult
}

export type CharacterIconResult = {
  iconPath: string
  icon: ResourceInfo
  warnings?: string[]
  logs?: string[]
}

export type CharacterCropEdit = {
  centerX: number
  centerY: number
  cropSize: number
}

export type SaveCharacterRequest = {
  scan: CharacterScanResult
  settings: CharacterSettingsEdit
  icon?: ResourceInfo
}

export type SingleMetadataEdit = {
  publisherId: string
  levelId: string
  title: string
  artist: string
  bpmText: string
  bpmBase: string
  charts: Array<{
    ratingClass: number
    difficulty: string
    chartConstant: string
    charter: string
    illustrator: string
    alias: string
    affPath?: string
    adopted: boolean
  }>
  showAlias: boolean
}

export type AppearanceEdit = {
  particle: string
  accent: string
  track: string
  singleLine: string
}

export type PreprocessEdit = {
  deleteDesignantLine: boolean
  fixZeroDurationArcTap: boolean
  fixReversedArcTime: boolean
  expandArcResolution: boolean
}

export type ResourceOverrides = {
  audioPath?: string
  jacketPath?: string
  backgroundPath?: string
  songlistPath?: string
  packlistPath?: string
}

export type SaveSingleRequest = {
  scan: SingleScanResult
  metadata: SingleMetadataEdit
  appearance: AppearanceEdit
  preprocess: PreprocessEdit
  resources: ResourceOverrides
}

export type SavePackRequest = {
  scan: PackScanResult
  settings: PackSettingsEdit
}

export type SelectedResource = ResourceInfo & {
  canceled?: boolean
}

export type ActionResult<T> = {
  ok: boolean
  data?: T
  error?: string
  warnings?: string[]
  logs?: string[]
}

export type CacheInfo = {
  root: string
  sizeBytes: number
  lastCleanup: string
}

export type UiLanguage = 'zh-CN' | 'en'

export type AppSettingsInfo = {
  language: UiLanguage
  lastSaveDirectory?: string
  settingsPath: string
}

export type AboutInfo = {
  appName: string
  version: string
  electron: string
  chromium: string
  node: string
  packaged: boolean
  resourcesPath: string
  cacheRoot: string
  userDataPath: string
  javaRuntimePath: string
  workerPath: string
  description: string
}

export type UpdateInfo = {
  configured: boolean
  message: string
}
