import {
  AlertTriangle,
  Archive,
  CheckCircle2,
  ChevronRight,
  FileArchive,
  FolderOpen,
  ImageIcon,
  InfoIcon,
  Languages,
  Music2,
  RefreshCw,
  Save,
  Settings,
  Trash2,
  UserRound
} from 'lucide-react'
import { createContext, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import type { CSSProperties, ChangeEvent, PointerEvent, ReactElement, ReactNode, WheelEvent } from 'react'
import type {
  ActionResult,
  AppearanceEdit,
  AboutInfo,
  AppSettingsInfo,
  CacheInfo,
  CharacterConvertResult,
  CharacterCropEdit,
  CharacterIconResult,
  CharacterScanResult,
  CharacterSettingsEdit,
  ChartInfo,
  ConvertResult,
  PackChartInfo,
  PackConvertResult,
  PackLevelInfo,
  PackMode,
  PackScanResult,
  PackSettingsEdit,
  PreprocessEdit,
  ResourceInfo,
  ResourceOverrides,
  SavePackRequest,
  SaveCharacterRequest,
  SaveSingleRequest,
  SelectedResource,
  SingleMetadataEdit,
  SingleScanResult,
  UiLanguage,
  UpdateInfo
} from '../../main/types'
import {
  CHARACTER_IMAGE_HEIGHT,
  androidPortedResultLayout,
  heightOf,
  mapCharacterToResultCanvas,
  rectCss,
  resultPreviewBackLayers,
  resultPreviewFrontLayers,
  widthOf
} from './arccreateResultLayout'
import type { PreviewBounds, ResultAssetLayerKey, ResultPreviewLayer } from './arccreateResultLayout'
import acResultBackgroundArrow from './assets/ac_result_background_arrow.png'
import acResultClearGlow from './assets/ac_result_clear_glow.png'
import acResultJacketBackground from './assets/ac_result_jacket_background.png'
import acResultJudgementTable from './assets/ac_result_judgement_table.png'
import acResultJudgementTableHighlight from './assets/ac_result_judgement_table_highlight.png'
import acResultPlayRetryBackground from './assets/ac_result_play_retry_background.png'
import acResultPlayRetryFrame from './assets/ac_result_play_retry_frame.png'
import acResultScoreFrame from './assets/ac_result_score_frame.png'
import iconUrl from './assets/icon_windows.png'
import { useEdgeAwareCards } from './edgeAwareCards'

type Page = 'single' | 'pack' | 'character'
type ResourceKind = keyof ResourceOverrides

const pageDefinitions: Array<{ id: Page; zh: string; en: string; icon: ReactElement }> = [
  { id: 'single', zh: '单曲转换', en: 'Single Song', icon: <Music2 size={20} /> },
  { id: 'pack', zh: '曲包编辑', en: 'Pack Editor', icon: <Archive size={20} /> },
  { id: 'character', zh: '搭档编辑', en: 'Character Editor', icon: <UserRound size={20} /> }
]

const i18n = {
  'zh-CN': {
    locale: 'zh-CN',
    technicalPreview: 'ArcCreate 打包工具',
    settings: '设置',
    cacheLoading: '缓存状态读取中',
    language: '语言',
    languageHint: '切换界面语言，不会重置当前页面状态。',
    clearCache: '清理缓存',
    clearCacheHint: '清理旧 session 和非活动缓存，不会删除当前已导入内容。',
    about: '关于',
    aboutHint: '查看版本、运行时和缓存目录。',
    checkUpdates: '检查更新',
    checkUpdatesHint: '当前暂未配置更新源。',
    clearDone: '清理完成',
    clearFailed: '清理失败',
    clearing: '清理中...',
    close: '关闭',
    continue: '继续',
    cacheRoot: 'cache 路径',
    userDataPath: 'userData 路径',
    packaged: 'Packaged',
    resourcesPath: 'resourcesPath',
    javaRuntimePath: 'Java runtime',
    workerPath: 'converter-worker',
    description: '项目说明',
    input: '输入',
    status: '状态',
    overview: '概览',
    chartInfo: '谱面信息',
    resources: '资源',
    convertAndSave: '转换与保存',
    saveArcpkg: '保存 arcpkg',
    chooseZip: '选择 ZIP',
    chooseSongFolder: '选择歌曲文件夹',
    scan: '扫描',
    currentInput: '当前输入',
    inputType: '输入类型',
    currentWorkspace: '当前 workspace',
    title: '曲名',
    artistComposer: '曲师 / composer',
    difficulty: '难度',
    audio: '音频',
    jacket: '曲绘',
    background: '背景',
    recognized: '已识别',
    unrecognized: '未识别',
    saveHint: '打开保存文件对话框选择完整 .arcpkg 路径；同名覆盖只确认一次。',
    jumpedToSave: '已定位到保存区域。',
    incompleteSingle: '请确认 metadata、音频和至少一个采用的 AFF 已完整。',
    savePath: '保存路径',
    fileSize: '文件大小',
    packInput: '曲包输入',
    officialPackMode: '官方曲包转 ArcCreate',
    bundleMode: '多个 arcpkg 打包',
    existingPackMode: '编辑已有曲包',
    chooseOfficialZip: '选择官方曲包 ZIP',
    chooseOfficialFolder: '选择官方曲包文件夹',
    chooseArcpkgFiles: '选择多个 arcpkg',
    chooseArcpkgFolder: '选择 arcpkg 文件夹',
    chooseExistingPack: '选择已有曲包',
    addArcpkg: '追加 arcpkg',
    addFolder: '追加文件夹',
    packSettings: '曲包设置',
    outputFileName: '输出文件名',
    packName: '曲包名称 packName',
    publisherId: '发布者 ID',
    packId: '曲包识别码 packId',
    identifierPreview: 'identifier 预览',
    directoryPreview: 'directory 预览',
    packCover: '曲包封面',
    notDetectedManual: '未识别，可手动选择',
    replace: '更换',
    manualSelect: '手动选择',
    clear: '清除',
    statsAndConflicts: '统计与冲突',
    existingLevels: '已有曲目',
    addedLevels: '追加曲目',
    finalLevels: '最终曲目',
    renamedConflicts: '冲突修正',
    sourceSummary: '来源 arcpkg 摘要',
    noSources: '暂无来源',
    levelList: '曲目列表',
    compactLevelListHint: '曲目较多，默认显示紧凑列表。',
    expand: '展开',
    collapse: '收起',
    expandAll: '全部展开',
    collapseAll: '全部收起',
    scanForLevelList: '扫描后会显示曲目列表。',
    packageVerb: '打包',
    adopt: '采用',
    ignore: '忽略',
    resource: 'resource',
    statusLabel: 'status',
    savePackReadyWarning: '请先扫描曲包并确认 packId 和至少一个启用曲目。',
    validatorWarningsLogs: 'validator / warnings / logs',
    warningList: 'warning 列表',
    workerLogs: 'worker 日志',
    noWarnings: '暂无 warning',
    noLogs: '暂无日志',
    metadata: '谱面信息',
    levelId: '谱面识别码',
    bpmBaseLabel: 'baseBpm（ArcCreate 数值 BPM）',
    bpmTextLabel: 'bpmText（显示文本）',
    adoptedAff: '采用的 AFF',
    ignoredAff: '忽略的 AFF',
    unnamedDifficulty: '未命名难度',
    appearance: 'ArcCreate 外观',
    particle: 'Particle / 粒子特效',
    accent: 'Accent / 判定线和连击数',
    track: 'Track / 轨道',
    singleLine: 'Single Line / 单曲装饰线',
    preprocess: '预处理选项',
    deleteDesignant: '删除 designant 行',
    fixZeroArcTap: '修复 0ms arc+arctap',
    fixReversedArc: '修复 arc 起止时间反向',
    expandTiminggroup: '展开 timinggroup arcresolution',
    statusLog: 'warnings / error / 日志 / 详细信息',
    processing: '处理中...',
    workspaceInfo: 'workspace 信息',
    validatorNoErrors: 'validator 无错误',
    fileName: '文件名',
    source: '来源',
    imageSize: '图片尺寸',
    detailedPath: '详细路径',
    type: '类型',
    done: '完成',
    failed: '失败',
    openLocation: '打开位置',
    characterInput: '搭档输入',
    chooseCharacterImage: '选择搭档 PNG / JPG',
    chooseCharacterArcpkg: '选择已有搭档 arcpkg',
    characterInfo: '搭档信息',
    characterId: '搭档识别码',
    defaultName: 'name.default',
    zhCnName: 'name.zh-cn',
    characterImage: '搭档立绘',
    characterIcon: '搭档 icon',
    iconCrop: 'icon 裁切',
    cropHint: '导入搭档立绘后可以裁切 icon。',
    resetCrop: '重置裁切',
    generateIcon: '生成 icon',
    resultPreview: 'ArcCreate 结果页位置预览',
    previewDebug: '预览调试信息',
    sampleDefault: '样例默认',
    reset: '重置',
    center: '居中',
    fitHeight: '适配高度',
    fitWidth: '适配宽度',
    outsideCharacter: '角色当前在预览画布外，请调整 x / y / scale。',
    characterSaveWarning: '请确认立绘、icon、name.default、publisherId 和 characterId 已完整。',
    placeholderState: '当前页面已保留状态；后续功能会继续迁移。',
    resultSaved: '已保存',
    resultFailed: '转换失败',
    statusNoInput: '未选择输入',
    statusNeedMetadata: '需要补充 metadata / 资源',
    statusReady: '可转换',
    statusSavedDescription: '输出文件已写入，并清理当前 session。',
    statusNoInputDescription: '选择 ZIP 或歌曲文件夹后开始扫描。',
    statusNeedMetadataDescription: '请补齐必要字段、音频和 AFF 映射。',
    statusReadyDescription: '已扫描，可以保存 arcpkg。',
    seeLogs: '请查看日志。'
  },
  en: {
    locale: 'en',
    technicalPreview: 'ArcCreate package toolkit',
    settings: 'Settings',
    cacheLoading: 'Reading cache status',
    language: 'Language',
    languageHint: 'Change the UI language without resetting page state.',
    clearCache: 'Clear cache',
    clearCacheHint: 'Clear old sessions and inactive cache without deleting current imports.',
    about: 'About',
    aboutHint: 'View version, runtime, and cache paths.',
    checkUpdates: 'Check updates',
    checkUpdatesHint: 'No update source is configured yet.',
    clearDone: 'Cache cleared',
    clearFailed: 'Clear failed',
    clearing: 'Clearing...',
    close: 'Close',
    continue: 'Continue',
    cacheRoot: 'Cache path',
    userDataPath: 'User data path',
    packaged: 'Packaged',
    resourcesPath: 'resourcesPath',
    javaRuntimePath: 'Java runtime',
    workerPath: 'converter-worker',
    description: 'Description',
    input: 'Input',
    status: 'Status',
    overview: 'Overview',
    chartInfo: 'Chart Info',
    resources: 'Resources',
    convertAndSave: 'Convert & Save',
    saveArcpkg: 'Save arcpkg',
    chooseZip: 'Select ZIP',
    chooseSongFolder: 'Select song folder',
    scan: 'Scan',
    currentInput: 'Current Input',
    inputType: 'Input Type',
    currentWorkspace: 'Current Workspace',
    title: 'Title',
    artistComposer: 'Artist / Composer',
    difficulty: 'Difficulty',
    audio: 'Audio',
    jacket: 'Jacket',
    background: 'Background',
    recognized: 'Detected',
    unrecognized: 'Not detected',
    saveHint: 'Choose a full .arcpkg path in the save dialog. Existing files are confirmed only once.',
    jumpedToSave: 'Save section is now highlighted.',
    incompleteSingle: 'Please complete metadata, audio, and at least one adopted AFF.',
    savePath: 'Save Path',
    fileSize: 'File Size',
    packInput: 'Pack Input',
    officialPackMode: 'Convert Official Pack',
    bundleMode: 'Bundle arcpkg Files',
    existingPackMode: 'Edit Existing Pack',
    chooseOfficialZip: 'Select official pack ZIP',
    chooseOfficialFolder: 'Select official pack folder',
    chooseArcpkgFiles: 'Select arcpkg files',
    chooseArcpkgFolder: 'Select arcpkg folder',
    chooseExistingPack: 'Select existing pack',
    addArcpkg: 'Add arcpkg',
    addFolder: 'Add folder',
    packSettings: 'Pack Settings',
    outputFileName: 'Output File Name',
    packName: 'Pack Name',
    publisherId: 'Publisher ID',
    packId: 'Pack ID',
    identifierPreview: 'Identifier Preview',
    directoryPreview: 'Directory Preview',
    packCover: 'Pack Cover',
    notDetectedManual: 'Not detected; choose manually',
    replace: 'Replace',
    manualSelect: 'Choose manually',
    clear: 'Clear',
    statsAndConflicts: 'Stats & Conflicts',
    existingLevels: 'Existing Levels',
    addedLevels: 'Added Levels',
    finalLevels: 'Final Levels',
    renamedConflicts: 'Renamed Conflicts',
    sourceSummary: 'Source arcpkg Summary',
    noSources: 'No sources',
    levelList: 'Level List',
    compactLevelListHint: 'Many levels detected. Showing compact rows by default.',
    expand: 'Expand',
    collapse: 'Collapse',
    expandAll: 'Expand All',
    collapseAll: 'Collapse All',
    scanForLevelList: 'Scan to show the level list.',
    packageVerb: 'Bundle',
    adopt: 'Adopt',
    ignore: 'Ignore',
    resource: 'Resource',
    statusLabel: 'Status',
    savePackReadyWarning: 'Scan a pack and confirm packId and at least one enabled level.',
    validatorWarningsLogs: 'Validator / Warnings / Logs',
    warningList: 'Warnings',
    workerLogs: 'Worker Logs',
    noWarnings: 'No warnings',
    noLogs: 'No logs',
    metadata: 'Chart Info',
    levelId: 'Level ID',
    bpmBaseLabel: 'baseBpm (ArcCreate numeric BPM)',
    bpmTextLabel: 'bpmText (display text)',
    adoptedAff: 'Adopted AFF',
    ignoredAff: 'Ignored AFF',
    unnamedDifficulty: 'Unnamed difficulty',
    appearance: 'ArcCreate Appearance',
    particle: 'Particle',
    accent: 'Accent',
    track: 'Track',
    singleLine: 'Single Line',
    preprocess: 'Preprocess Options',
    deleteDesignant: 'Delete designant lines',
    fixZeroArcTap: 'Fix 0ms arc+arctap',
    fixReversedArc: 'Fix reversed arc time',
    expandTiminggroup: 'Expand timinggroup arcresolution',
    statusLog: 'Warnings / Error / Logs / Details',
    processing: 'Processing...',
    workspaceInfo: 'Workspace',
    validatorNoErrors: 'No validator errors',
    fileName: 'File Name',
    source: 'Source',
    imageSize: 'Image Size',
    detailedPath: 'Detailed Path',
    type: 'Type',
    done: 'Done',
    failed: 'Failed',
    openLocation: 'Open Location',
    characterInput: 'Character Input',
    chooseCharacterImage: 'Select character PNG / JPG',
    chooseCharacterArcpkg: 'Select existing character arcpkg',
    characterInfo: 'Character Info',
    characterId: 'Character ID',
    defaultName: 'name.default',
    zhCnName: 'name.zh-cn',
    characterImage: 'Character Image',
    characterIcon: 'Character icon',
    iconCrop: 'Icon Crop',
    cropHint: 'Import a character image to crop an icon.',
    resetCrop: 'Reset Crop',
    generateIcon: 'Generate icon',
    resultPreview: 'ArcCreate Result Position Preview',
    previewDebug: 'Preview Debug Info',
    sampleDefault: 'Sample Default',
    reset: 'Reset',
    center: 'Center',
    fitHeight: 'Fit Height',
    fitWidth: 'Fit Width',
    outsideCharacter: 'The character is outside the result canvas. Adjust x / y / scale.',
    characterSaveWarning: 'Please complete image, icon, name.default, publisherId, and characterId.',
    placeholderState: 'This page keeps its state; more features will be migrated later.',
    resultSaved: 'Saved',
    resultFailed: 'Conversion failed',
    statusNoInput: 'No input selected',
    statusNeedMetadata: 'Metadata / resources needed',
    statusReady: 'Ready to convert',
    statusSavedDescription: 'The output file was written and the current session was cleaned.',
    statusNoInputDescription: 'Select a ZIP or song folder to scan.',
    statusNeedMetadataDescription: 'Complete required fields, audio, and AFF mapping.',
    statusReadyDescription: 'Scanned and ready to save arcpkg.',
    seeLogs: 'See logs.'
  }
} as const

type UiCopy = (typeof i18n)[UiLanguage]
const I18nContext = createContext<UiCopy>(i18n['zh-CN'])

function useCopy(): UiCopy {
  return useContext(I18nContext)
}

const appearanceOptions = {
  particle: [
    ['INHERIT', 'Inherit / 不变'],
    ['LIGHT', 'Light / 光芒侧'],
    ['CONFLICT', 'Conflict / 纷争侧'],
    ['MIRAI_LIGHT', 'Mirai Light'],
    ['MIRAI_CONFLICT', 'Mirai Conflict'],
    ['COLORLESS', 'Colorless / 消色侧']
  ],
  accent: [
    ['INHERIT', 'Inherit / 不变'],
    ['LIGHT', 'Light / 光芒侧'],
    ['CONFLICT', 'Conflict / 纷争侧'],
    ['DYNAMIX', 'Dynamix'],
    ['COLORLESS', 'Colorless / 消色侧']
  ],
  track: [
    ['INHERIT', 'Inherit / 不变'],
    ['LIGHT', 'Light / 光芒侧'],
    ['CONFLICT', 'Conflict / 纷争侧'],
    ['BLACK', 'Black'],
    ['NIJUUSEI', 'Nijuusei'],
    ['REI', 'Rei'],
    ['DARK_VS', 'DarkVs'],
    ['TEMPEST', 'Tempest'],
    ['FINALE', 'Finale'],
    ['PENTIMENT', 'Pentiment'],
    ['ARCANA', 'Arcana'],
    ['COLORLESS', 'Colorless / 消色侧']
  ],
  singleLine: [
    ['NONE', 'None / 不启用'],
    ['LIGHT', 'Light / 光芒侧'],
    ['CONFLICT', 'Conflict / 纷争侧'],
    ['NEO', 'Neo']
  ]
} as const

export function App(): ReactElement {
  const [page, setPage] = useState<Page>('single')
  const [cache, setCache] = useState<CacheInfo | null>(null)
  const [settingsInfo, setSettingsInfo] = useState<AppSettingsInfo | null>(null)
  const [settingsOpen, setSettingsOpen] = useState(false)
  const pageHostRef = useRef<HTMLElement | null>(null)
  const language = settingsInfo?.language ?? 'zh-CN'
  const text = i18n[language]
  const localizedPages = pageDefinitions.map((item) => ({ ...item, label: language === 'en' ? item.en : item.zh }))

  useEdgeAwareCards(page, pageHostRef)

  useEffect(() => {
    void window.etoileBridge.cacheInfo().then(setCache)
    void window.etoileBridge.getSettings().then(setSettingsInfo)
  }, [])

  return (
    <I18nContext.Provider value={text}>
    <div className="appShell">
      <aside className="sidebar">
        <div className="brand">
          <img src={iconUrl} alt="" className="brandIcon" />
          <div>
            <div className="brandTitle">EtoileBridge</div>
            <div className="brandSub">{text.technicalPreview}</div>
          </div>
        </div>

        <nav className="navList">
          {localizedPages.map((item) => (
            <button key={item.id} className={`navItem ${page === item.id ? 'selected' : ''}`} onClick={() => setPage(item.id)}>
              {item.icon}
              <span>{item.label}</span>
            </button>
          ))}
        </nav>

        <div className="sidebarFooter">
          <button
            className="tonalButton full"
            onClick={() => setSettingsOpen(true)}
          >
            <Settings size={18} />
            {text.settings}
          </button>
          <div className="cacheInfo">
            <div>{formatBytes(cache?.sizeBytes ?? 0)}</div>
            <div title={cache?.root}>{cache?.lastCleanup ?? text.cacheLoading}</div>
          </div>
        </div>
      </aside>

      <main className="content">
        <header className="topArea">
          <div>
            <h1>EtoileBridge</h1>
            <p>{localizedPages.find((item) => item.id === page)?.label}</p>
          </div>
        </header>

        <div className="pageHostWrap">
          <div className="topEdgeGuard" aria-hidden="true" />
          <section className="pageHost" ref={pageHostRef}>
            <div className={`pagePane ${page === 'single' ? 'active' : ''}`} aria-hidden={page !== 'single'}>
              <SingleSongPage onCacheRefresh={setCache} />
            </div>
            <div className={`pagePane ${page === 'pack' ? 'active' : ''}`} aria-hidden={page !== 'pack'}>
              <PackEditorPage onCacheRefresh={setCache} />
            </div>
            <div className={`pagePane ${page === 'character' ? 'active' : ''}`} aria-hidden={page !== 'character'}>
              <CharacterEditorPage onCacheRefresh={setCache} />
            </div>
          </section>
        </div>
      </main>
      {settingsOpen && (
        <SettingsDialog
          language={language}
          cache={cache}
          copy={text}
          onClose={() => setSettingsOpen(false)}
          onLanguageChange={async (nextLanguage) => {
            const nextSettings = await window.etoileBridge.setLanguage(nextLanguage)
            setSettingsInfo(nextSettings)
          }}
          onCacheRefresh={setCache}
        />
      )}
    </div>
    </I18nContext.Provider>
  )
}

function SettingsDialog({
  language,
  cache,
  copy,
  onClose,
  onLanguageChange,
  onCacheRefresh
}: {
  language: UiLanguage
  cache: CacheInfo | null
  copy: (typeof i18n)[UiLanguage]
  onClose: () => void
  onLanguageChange: (language: UiLanguage) => Promise<void>
  onCacheRefresh: (cache: CacheInfo) => void
}): ReactElement {
  const [clearing, setClearing] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [about, setAbout] = useState<AboutInfo | null>(null)
  const [update, setUpdate] = useState<UpdateInfo | null>(null)
  const [languageMenuOpen, setLanguageMenuOpen] = useState(false)

  const clearCache = async (): Promise<void> => {
    setClearing(true)
    setMessage(null)
    try {
      const next = await window.etoileBridge.clearCacheSafe()
      onCacheRefresh(next)
      setMessage(`${copy.clearDone}: ${formatBytes(next.sizeBytes)}`)
    } catch (error) {
      setMessage(`${copy.clearFailed}: ${error instanceof Error ? error.message : String(error)}`)
    } finally {
      setClearing(false)
    }
  }

  return (
    <ModalPortal title={copy.settings} closeLabel={copy.close} panelClassName="settingsDialog" onClose={onClose}>
        <div className="settingsSection">
          <div className="settingsItem">
            <Languages size={22} />
            <div>
              <strong>{copy.language}</strong>
              <p className="hint">{copy.languageHint}</p>
            </div>
            <div className="languageMenu">
              <button className="languageMenuButton" onClick={() => setLanguageMenuOpen((open) => !open)}>
                {language === 'zh-CN' ? '简体中文' : 'English'}
                <ChevronRight size={16} />
              </button>
              {languageMenuOpen && (
                <div className="languagePopover">
                  {([
                    ['zh-CN', '简体中文'],
                    ['en', 'English']
                  ] as Array<[UiLanguage, string]>).map(([id, label]) => (
                    <button
                      key={id}
                      className={language === id ? 'selected' : ''}
                      onClick={async () => {
                        await onLanguageChange(id)
                        setLanguageMenuOpen(false)
                      }}
                    >
                      <span>{label}</span>
                      {language === id && <CheckCircle2 size={16} />}
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>

          <div className="settingsItem">
            <Trash2 size={22} />
            <div>
              <strong>{copy.clearCache}</strong>
              <p className="hint">{copy.clearCacheHint}</p>
              <p className="hint">{cache?.root}</p>
            </div>
            <button className="tonalButton" disabled={clearing} onClick={() => void clearCache()}>
              {clearing ? copy.clearing : copy.clearCache}
            </button>
          </div>

          <div className="settingsItem">
            <InfoIcon size={22} />
            <div>
              <strong>{copy.about}</strong>
              <p className="hint">{copy.aboutHint}</p>
            </div>
            <button className="textButton" onClick={async () => setAbout(await window.etoileBridge.getAboutInfo())}>{copy.about}</button>
          </div>

          <div className="settingsItem">
            <RefreshCw size={22} />
            <div>
              <strong>{copy.checkUpdates}</strong>
              <p className="hint">{copy.checkUpdatesHint}</p>
            </div>
            <button className="textButton" onClick={async () => setUpdate(await window.etoileBridge.checkUpdates())}>{copy.checkUpdates}</button>
          </div>
        </div>

        {message && <div className="resultPanel success">{message}</div>}
        {update && <div className="resultPanel">{update.message}</div>}
        {about && (
          <div className="resultPanel">
            <Info label="EtoileBridge Electron" value={about.version} />
            <Info label="Electron" value={about.electron} />
            <Info label="Chromium" value={about.chromium} />
              <Info label="Node" value={about.node} />
              <Info label={copy.packaged} value={String(about.packaged)} />
              <Info label={copy.resourcesPath} value={about.resourcesPath} />
              <Info label={copy.cacheRoot} value={about.cacheRoot} />
              <Info label={copy.userDataPath} value={about.userDataPath} />
              <Info label={copy.javaRuntimePath} value={about.javaRuntimePath} />
              <Info label={copy.workerPath} value={about.workerPath} />
              <Info label={copy.description} value={about.description} />
          </div>
        )}
    </ModalPortal>
  )
}

function ModalPortal({
  title,
  closeLabel = 'Close',
  panelClassName = '',
  actions,
  children,
  onClose
}: {
  title: string
  closeLabel?: string
  panelClassName?: string
  actions?: ReactNode
  children: ReactNode
  onClose: () => void
}): ReactElement {
  const [closing, setClosing] = useState(false)
  const closeTimer = useRef<number | null>(null)

  function requestClose(): void {
    if (closing) return
    setClosing(true)
    closeTimer.current = window.setTimeout(onClose, 180)
  }

  useEffect(() => {
    const previousOverflow = document.body.style.overflow
    document.body.classList.add('modalOpen')
    document.body.style.overflow = 'hidden'
    const handler = (event: KeyboardEvent): void => {
      if (event.key === 'Escape') requestClose()
    }
    window.addEventListener('keydown', handler)
    return () => {
      window.removeEventListener('keydown', handler)
      document.body.classList.remove('modalOpen')
      document.body.style.overflow = previousOverflow
      if (closeTimer.current != null) {
        window.clearTimeout(closeTimer.current)
      }
    }
  }, [])

  return createPortal(
    <div className={`modalRoot ${closing ? 'closing' : ''}`} role="presentation" onMouseDown={requestClose}>
      <div className="modalBackdrop" />
      <section
        className={`modalPanel ${panelClassName} ${closing ? 'closing' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="dialogHeader">
          <h2>{title}</h2>
          <div className="buttonRow right">
            {actions}
            <button className="textButton" onClick={requestClose}>{closeLabel}</button>
          </div>
        </div>
        {children}
      </section>
    </div>,
    document.body
  )
}

function SingleSongPage({ onCacheRefresh }: { onCacheRefresh: (cache: CacheInfo) => void }): ReactElement {
  const copy = useCopy()
  const saveRef = useRef<HTMLDivElement | null>(null)
  const [scan, setScan] = useState<SingleScanResult | null>(null)
  const [metadata, setMetadata] = useState<SingleMetadataEdit>(emptyMetadata())
  const [appearance, setAppearance] = useState<AppearanceEdit>(defaultAppearance())
  const [preprocess, setPreprocess] = useState<PreprocessEdit>(defaultPreprocess())
  const [manualResources, setManualResources] = useState<Partial<Record<ResourceKind, ResourceInfo>>>({})
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<ActionResult<unknown> | null>(null)
  const [detailsImage, setDetailsImage] = useState<{ title: string; resource: ResourceInfo; kind: ResourceKind } | null>(null)
  const [saveHintVisible, setSaveHintVisible] = useState(false)
  const saveHintTimer = useRef<number | null>(null)

  const resources = useMemo(
    () => ({
      audioPath: manualResources.audioPath ?? scan?.audio,
      jacketPath: manualResources.jacketPath ?? scan?.jacket,
      backgroundPath: manualResources.backgroundPath ?? scan?.background,
      songlistPath: manualResources.songlistPath ?? scan?.songlist,
      packlistPath: manualResources.packlistPath ?? scan?.packlist
    }),
    [manualResources, scan]
  )
  const warnings = [...(scan?.warnings ?? []), ...(message?.warnings ?? [])]
  const logs = [...(scan?.logs ?? []), ...(message?.logs ?? [])]
  const ready = Boolean(scan?.workspacePath && metadata.levelId && metadata.title && metadata.artist && resources.audioPath?.path && metadata.charts.some((chart) => chart.adopted))
  const status = computeStatus(scan, ready, message, copy)
  const savedData = message?.data && typeof message.data === 'object' && 'outputPath' in message.data ? (message.data as ConvertResult) : null

  useEffect(() => {
    return () => {
      if (saveHintTimer.current !== null) {
        window.clearTimeout(saveHintTimer.current)
      }
    }
  }, [])

  async function runScan(action: () => Promise<ActionResult<SingleScanResult>>): Promise<void> {
    setBusy(true)
    setMessage(null)
    try {
      const result = await action()
      setMessage(result)
      if (result.ok && result.data) {
        setScan(result.data)
        setMetadata(metadataFromScan(result.data))
        setManualResources({})
      }
    } finally {
      setBusy(false)
    }
  }

  async function chooseResource(kind: ResourceKind): Promise<void> {
    const result = await window.etoileBridge.chooseResource(kind)
    setMessage(result)
    if (result.ok && result.data && !result.data.canceled) {
      setManualResources((current) => ({ ...current, [kind]: result.data as SelectedResource }))
    }
  }

  async function save(): Promise<void> {
    if (!scan) return
    setBusy(true)
    setMessage(null)
    const request: SaveSingleRequest = {
      scan,
      metadata,
      appearance,
      preprocess,
      resources: {
        audioPath: manualResources.audioPath?.path,
        jacketPath: manualResources.jacketPath?.path,
        backgroundPath: manualResources.backgroundPath?.path,
        songlistPath: manualResources.songlistPath?.path,
        packlistPath: manualResources.packlistPath?.path
      }
    }
    try {
      const result = await window.etoileBridge.saveSingle(request)
      setMessage(result)
      if (result.ok && result.data?.workspaceCleaned) {
        onCacheRefresh(await window.etoileBridge.cacheInfo())
      }
    } finally {
      setBusy(false)
    }
  }

  async function openInputLocation(): Promise<void> {
    if (!scan?.sourcePath) return
    setMessage(await window.etoileBridge.openInputLocation(scan.sourcePath))
  }

  function jumpToSaveSection(): void {
    saveRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    setSaveHintVisible(true)
    if (saveHintTimer.current !== null) {
      window.clearTimeout(saveHintTimer.current)
    }
    saveHintTimer.current = window.setTimeout(() => {
      setSaveHintVisible(false)
      saveHintTimer.current = null
    }, 1400)
  }

  return (
    <>
      <div className="grid">
        <div className="column">
          <Card title={copy.input}>
            <div className="buttonRow">
              <button className="filledButton" disabled={busy} onClick={() => runScan(() => window.etoileBridge.chooseZipAndScan())}>
                <FileArchive size={18} />
                {copy.chooseZip}
              </button>
              <button className="tonalButton" disabled={busy} onClick={() => runScan(() => window.etoileBridge.chooseFolderAndScan())}>
                <FolderOpen size={18} />
                {copy.chooseSongFolder}
              </button>
              <button className="textButton" disabled={busy || !scan?.sourcePath} onClick={() => scan?.sourcePath && runScan(() => window.etoileBridge.rescanSingle(scan.sourcePath))}>
                <RefreshCw size={17} />
                {copy.scan}
              </button>
            </div>
            <PathAction label={copy.currentInput} value={scan?.sourcePath} onOpen={openInputLocation} />
            <Info label={copy.inputType} value={scan?.inputType ?? '-'} />
            <details>
              <summary>{copy.currentWorkspace}</summary>
              <PathBlock value={scan?.workspacePath ?? '-'} />
            </details>
          </Card>

          <Card title={copy.status}>
            <div className={`statusPill ${status.level}`}>
              {status.level === 'good' ? <CheckCircle2 size={18} /> : status.level === 'bad' ? <AlertTriangle size={18} /> : <InfoIcon size={18} />}
              <span>{status.label}</span>
            </div>
            <p className="hint">{status.description}</p>
            <button className="tonalButton" disabled={!scan} onClick={jumpToSaveSection}>
              {copy.continue}
              <ChevronRight size={17} />
            </button>
          </Card>

          <Card title={copy.overview}>
            <Info label="songId" value={scan?.songId ?? '-'} />
            <Info label={copy.title} value={scan?.title ?? '-'} />
            <Info label={copy.artistComposer} value={scan?.artist ?? '-'} />
            <Info label="baseBpm" value={scan?.bpmBase != null ? String(scan.bpmBase) : copy.unrecognized} />
            <Info label="bpmText" value={scan?.bpmText ?? copy.unrecognized} />
            <Info label={copy.difficulty} value={scan?.difficulty ?? '-'} />
            <Info label={copy.audio} value={resources.audioPath?.path ? copy.recognized : copy.unrecognized} />
            <Info label={copy.jacket} value={resources.jacketPath?.path ? copy.recognized : copy.unrecognized} />
            <Info label={copy.background} value={resources.backgroundPath?.path ? copy.recognized : copy.unrecognized} />
          </Card>

          <MetadataCard metadata={metadata} onChange={setMetadata} />
          <AppearanceCard value={appearance} onChange={setAppearance} />
          <PreprocessCard value={preprocess} onChange={setPreprocess} />
        </div>

        <div className="column">
          <ResourcesCard
            resources={resources}
            manualResources={manualResources}
            onChoose={chooseResource}
            onClear={(kind) => setManualResources((current) => ({ ...current, [kind]: undefined }))}
            onImageOpen={(title, resource, kind) => setDetailsImage({ title, resource, kind })}
          />
          <AffMappingCard metadata={metadata} onChange={setMetadata} ignoredCount={(scan?.affFiles.length ?? 0) - metadata.charts.filter((chart) => chart.adopted).length} />
          <Card title={copy.convertAndSave} className={saveHintVisible ? 'saveSectionPulse' : undefined}>
            <div ref={saveRef} />
            {saveHintVisible && <p className="okText jumpHint">{copy.jumpedToSave}</p>}
            <button className="filledButton large" disabled={busy || !ready} onClick={save}>
              <Save size={18} />
              {copy.saveArcpkg}
            </button>
            <p className="hint">{copy.saveHint}</p>
            {!ready && <p className="warningText">{copy.incompleteSingle}</p>}
            {savedData && (
              <div className="resultPanel success">
                <Info label={copy.savePath} value={savedData.outputPath} />
                <Info label={copy.fileSize} value={formatBytes(Number(savedData.sizeBytes ?? 0))} />
              </div>
            )}
          </Card>

          <StatusLogCard busy={busy} message={message} warnings={warnings} logs={logs} workspace={scan?.workspacePath} />
        </div>
      </div>

      {detailsImage && (
        <ImageDialog
          image={detailsImage}
          onClose={() => setDetailsImage(null)}
          onChoose={async (kind) => {
            await chooseResource(kind)
            setDetailsImage(null)
          }}
        />
      )}
    </>
  )
}

function PackEditorPage({ onCacheRefresh }: { onCacheRefresh: (cache: CacheInfo) => void }): ReactElement {
  const copy = useCopy()
  const [mode, setMode] = useState<PackMode>('official')
  const [scan, setScan] = useState<PackScanResult | null>(null)
  const [settings, setSettings] = useState<PackSettingsEdit>(emptyPackSettings())
  const [expandedPackEntries, setExpandedPackEntries] = useState<Set<string>>(new Set())
  const [manualCover, setManualCover] = useState<ResourceInfo | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<ActionResult<unknown> | null>(null)
  const [detailsImage, setDetailsImage] = useState<{ title: string; resource: ResourceInfo; canReplaceCover?: boolean } | null>(null)

  const cover = manualCover ?? scan?.packImage
  const warnings = [...(scan?.warnings ?? []), ...(message?.warnings ?? [])]
  const logs = [...(scan?.logs ?? []), ...(message?.logs ?? [])]
  const savedData = message?.data && typeof message.data === 'object' && 'outputPath' in message.data ? (message.data as PackConvertResult) : null
  const ready = Boolean(scan && settings.packId && settings.entries.some((entry) => entry.enabled))

  async function runScan(action: () => Promise<ActionResult<PackScanResult>>): Promise<void> {
    setBusy(true)
    setMessage(null)
    try {
      const result = await action()
      setMessage(result)
      if (result.ok && result.data) {
        const nextSettings = packSettingsFromScan(result.data)
        setScan(result.data)
        setSettings(nextSettings)
        setExpandedPackEntries(defaultPackExpandedKeys(nextSettings.entries))
        setManualCover(null)
      }
    } finally {
      setBusy(false)
    }
  }

  async function chooseCover(): Promise<void> {
    const result = await window.etoileBridge.choosePackCover()
    setMessage(result)
    if (result.ok && result.data && !result.data.canceled) {
      setManualCover(result.data)
      setSettings((current) => ({ ...current, packImagePath: result.data?.path }))
    }
  }

  async function openSourceLocation(): Promise<void> {
    const source = firstPackSourcePath(scan)
    if (!source) return
    setMessage(await window.etoileBridge.openInputLocation(source))
  }

  async function openSavedLocation(): Promise<void> {
    if (!savedData?.outputPath) return
    setMessage(await window.etoileBridge.openInputLocation(savedData.outputPath))
  }

  async function save(): Promise<void> {
    if (!scan) return
    setBusy(true)
    setMessage(null)
    const request: SavePackRequest = {
      scan,
      settings: {
        ...settings,
        packImagePath: manualCover?.path ?? settings.packImagePath
      }
    }
    try {
      const result = await window.etoileBridge.savePack(request)
      setMessage(result)
      if (result.ok && result.data?.workspaceCleaned) {
        onCacheRefresh(await window.etoileBridge.cacheInfo())
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <div className="grid">
        <div className="column">
          <Card title={copy.packInput}>
            <div className="segmented">
              <button className={mode === 'official' ? 'selected' : ''} onClick={() => setMode('official')}>{copy.officialPackMode}</button>
              <button className={mode === 'bundle' ? 'selected' : ''} onClick={() => setMode('bundle')}>{copy.bundleMode}</button>
              <button className={mode === 'existing' ? 'selected' : ''} onClick={() => setMode('existing')}>{copy.existingPackMode}</button>
            </div>

            {mode === 'official' && (
              <div className="buttonRow">
                <button className="filledButton" disabled={busy} onClick={() => runScan(() => window.etoileBridge.chooseOfficialPackZipAndScan())}>
                  <FileArchive size={18} />
                  {copy.chooseOfficialZip}
                </button>
                <button className="tonalButton" disabled={busy} onClick={() => runScan(() => window.etoileBridge.chooseOfficialPackFolderAndScan())}>
                  <FolderOpen size={18} />
                  {copy.chooseOfficialFolder}
                </button>
              </div>
            )}
            {mode === 'bundle' && (
              <div className="buttonRow">
                <button className="filledButton" disabled={busy} onClick={() => runScan(() => window.etoileBridge.chooseArcpkgFilesAndScan())}>{copy.chooseArcpkgFiles}</button>
                <button className="tonalButton" disabled={busy} onClick={() => runScan(() => window.etoileBridge.chooseArcpkgFolderAndScan())}>{copy.chooseArcpkgFolder}</button>
              </div>
            )}
            {mode === 'existing' && (
              <div className="buttonRow">
                <button className="filledButton" disabled={busy} onClick={() => runScan(() => window.etoileBridge.chooseExistingPackAndScan())}>{copy.chooseExistingPack}</button>
                <button className="tonalButton" disabled={busy || !scan?.basePackPath} onClick={() => scan?.basePackPath && runScan(() => window.etoileBridge.chooseExistingPackAddFilesAndScan(scan.basePackPath!))}>{copy.addArcpkg}</button>
                <button className="tonalButton" disabled={busy || !scan?.basePackPath} onClick={() => scan?.basePackPath && runScan(() => window.etoileBridge.chooseExistingPackAddFolderAndScan(scan.basePackPath!))}>{copy.addFolder}</button>
              </div>
            )}

            <PathAction label={copy.currentInput} value={firstPackSourcePath(scan)} onOpen={openSourceLocation} />
            <details>
              <summary>workspace / add workspace</summary>
              <PathBlock value={[scan?.workspacePath, scan?.addWorkspacePath].filter(Boolean).join('\n') || '-'} />
            </details>
          </Card>

          <Card title={copy.packSettings}>
            <div className="formGrid">
              <TextInput label={copy.outputFileName} value={settings.outputFileName} onChange={(value) => setSettings({ ...settings, outputFileName: value })} />
              <TextInput label={copy.packName} value={settings.packName} onChange={(value) => setSettings({ ...settings, packName: value })} />
              <TextInput
                label={copy.packId}
                value={settings.packId}
                onChange={(value) => {
                  const packId = sanitizeUiId(value)
                  setSettings({
                    ...settings,
                    packId,
                    packIdentifier: buildPackIdentifier(settings.publisherId, packId)
                  })
                }}
              />
              <Info label={copy.identifierPreview} value={settings.packIdentifier || buildPackIdentifier(settings.publisherId, settings.packId)} />
              <Info label={copy.directoryPreview} value={settings.packDirectory || settings.packId || 'pack'} />
            </div>
            <div className="resourceCard">
              <div className="resourceHeader">
                <div>
                  <strong>{copy.packCover}</strong>
                  <span className={cover?.path ? 'okText' : 'mutedText'}>{cover?.path ? copy.recognized : copy.notDetectedManual}</span>
                </div>
                <div className="buttonRow right">
                  <button className="textButton" onClick={chooseCover}>{cover?.path ? copy.replace : copy.manualSelect}</button>
                  {manualCover && <button className="textButton" onClick={() => { setManualCover(null); setSettings((current) => ({ ...current, packImagePath: undefined })) }}>{copy.clear}</button>}
                </div>
              </div>
              <ImagePreview title={copy.packCover} resource={cover} variant="portrait" onOpen={() => cover?.previewUrl && setDetailsImage({ title: copy.packCover, resource: cover, canReplaceCover: true })} />
              <ResourceMeta resource={cover} />
            </div>
          </Card>

          <Card title={copy.statsAndConflicts}>
            <div className="summaryGrid">
              <Info label={copy.existingLevels} value={String(scan?.existingLevelCount ?? 0)} />
              <Info label={copy.addedLevels} value={String(scan?.addedLevelCount ?? scan?.entries.length ?? 0)} />
              <Info label={copy.finalLevels} value={String(scan?.finalLevelCount ?? scan?.entries.length ?? 0)} />
              <Info label={copy.renamedConflicts} value={String(scan?.renamedConflictCount ?? 0)} />
              <Info label="warnings" value={String(warnings.length)} />
            </div>
            {scan?.sourceReports?.length ? (
              <details>
                <summary>{copy.sourceSummary}</summary>
                <LogList items={scan.sourceReports.map((report) => `${basename(report.sourceFile)}: levels=${report.levelCount}, packs=${report.packEntryCount}${report.failureReason ? `, ${report.failureReason}` : ''}`)} empty={copy.noSources} />
              </details>
            ) : null}
          </Card>
        </div>

        <div className="column">
          <PackEntriesCard
            entries={settings.entries}
            scanEntries={scan?.entries ?? []}
            expandedKeys={expandedPackEntries}
            onExpandedKeysChange={setExpandedPackEntries}
            onChange={(entries) => setSettings({ ...settings, entries })}
            onImageOpen={(title, resource) => setDetailsImage({ title, resource })}
          />
          <Card title={copy.saveArcpkg}>
            <button className="filledButton large" disabled={busy || !ready} onClick={save}>
              <Save size={18} />
              {copy.saveArcpkg}
            </button>
            {!ready && <p className="warningText">{copy.savePackReadyWarning}</p>}
            {savedData && (
              <div className="resultPanel success">
                <Info label={copy.savePath} value={savedData.outputPath} />
                <Info label={copy.fileSize} value={formatBytes(savedData.sizeBytes)} />
                <Info label="converted" value={String(savedData.convertedCount)} />
                <Info label="skipped" value={String(savedData.skippedCount)} />
                <button className="textButton compactButton" onClick={openSavedLocation}>{copy.openLocation}</button>
              </div>
            )}
          </Card>

          <Card title={copy.validatorWarningsLogs}>
            {busy && <p className="hint">{copy.processing}</p>}
            {message && <ResultPanel result={message} />}
            {savedData?.validation && <PackValidationPanel result={savedData.validation} />}
            <details>
              <summary>{copy.warningList}</summary>
              <LogList items={warnings} empty={copy.noWarnings} />
            </details>
            <details>
              <summary>{copy.workerLogs}</summary>
              <LogList items={logs} empty={copy.noLogs} />
            </details>
          </Card>
        </div>
      </div>

      {detailsImage && (
        <GenericImageDialog
          title={detailsImage.title}
          resource={detailsImage.resource}
          onClose={() => setDetailsImage(null)}
          actions={detailsImage.canReplaceCover ? <button className="tonalButton" onClick={async () => { await chooseCover(); setDetailsImage(null) }}>{copy.replace}</button> : undefined}
        />
      )}
    </>
  )
}

function CharacterEditorPage({ onCacheRefresh }: { onCacheRefresh: (cache: CacheInfo) => void }): ReactElement {
  const copy = useCopy()
  const [scan, setScan] = useState<CharacterScanResult | null>(null)
  const [settings, setSettings] = useState<CharacterSettingsEdit>(emptyCharacterSettings())
  const [crop, setCrop] = useState<CharacterCropEdit>({ centerX: 0.5, centerY: 0.35, cropSize: 0.45 })
  const [manualImage, setManualImage] = useState<ResourceInfo | null>(null)
  const [generatedIcon, setGeneratedIcon] = useState<CharacterIconResult | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<ActionResult<unknown> | null>(null)
  const [detailsImage, setDetailsImage] = useState<{ title: string; resource: ResourceInfo } | null>(null)

  const image = manualImage ?? scan?.image
  const icon = generatedIcon?.icon ?? scan?.icon
  const warnings = [...(scan?.warnings ?? []), ...(generatedIcon?.warnings ?? []), ...(message?.warnings ?? [])]
  const logs = [...(scan?.logs ?? []), ...(generatedIcon?.logs ?? []), ...(message?.logs ?? [])]
  const savedData = message?.data && typeof message.data === 'object' && 'outputPath' in message.data ? (message.data as CharacterConvertResult) : null
  const ready = Boolean(scan && image?.path && icon?.path && settings.defaultName.trim() && settings.publisherId.trim() && settings.characterId.trim())

  async function runScan(action: () => Promise<ActionResult<CharacterScanResult>>): Promise<void> {
    setBusy(true)
    setMessage(null)
    try {
      const result = await action()
      setMessage(result)
      if (result.ok && result.data) {
        setScan(result.data)
        setSettings(characterSettingsFromScan(result.data))
        setGeneratedIcon(null)
        setManualImage(null)
        setCrop({ centerX: 0.5, centerY: 0.35, cropSize: 0.45 })
      }
    } finally {
      setBusy(false)
    }
  }

  async function chooseImage(): Promise<void> {
    const result = await window.etoileBridge.chooseCharacterImage()
    setMessage(result)
    if (result.ok && result.data && !result.data.canceled) {
      const next = result.data
      setManualImage(next)
      setSettings((current) => ({
        ...current,
        imagePath: next.path,
        imageFileName: next.name || current.imageFileName
      }))
    }
  }

  async function generateIcon(): Promise<void> {
    if (!scan || !image?.path) return
    setBusy(true)
    setMessage(null)
    try {
      const result = await window.etoileBridge.generateCharacterIcon({ ...scan, image }, crop)
      setMessage(result)
      if (result.ok && result.data) {
        setGeneratedIcon(result.data)
        setSettings((current) => ({
          ...current,
          iconPath: result.data?.icon.path,
          iconFileName: result.data?.icon.name || current.iconFileName || `${current.characterId}_icon.png`
        }))
      }
    } finally {
      setBusy(false)
    }
  }

  async function save(): Promise<void> {
    if (!scan) return
    setBusy(true)
    setMessage(null)
    const request: SaveCharacterRequest = {
      scan: { ...scan, image, icon },
      settings: {
        ...settings,
        imagePath: image?.path ?? settings.imagePath,
        iconPath: icon?.path ?? settings.iconPath
      },
      icon
    }
    try {
      const result = await window.etoileBridge.saveCharacter(request)
      setMessage(result)
      if (result.ok && result.data?.workspaceCleaned) {
        onCacheRefresh(await window.etoileBridge.cacheInfo())
      }
    } finally {
      setBusy(false)
    }
  }

  async function openSourceLocation(): Promise<void> {
    if (!scan?.sourcePath) return
    setMessage(await window.etoileBridge.openInputLocation(scan.sourcePath))
  }

  async function openSavedLocation(): Promise<void> {
    if (!savedData?.outputPath) return
    setMessage(await window.etoileBridge.openInputLocation(savedData.outputPath))
  }

  return (
    <>
      <div className="grid">
        <div className="column">
          <Card title={copy.characterInput}>
            <div className="buttonRow">
              <button className="filledButton" disabled={busy} onClick={() => runScan(() => window.etoileBridge.chooseCharacterImageAndScan())}>
                <ImageIcon size={18} />
                {copy.chooseCharacterImage}
              </button>
              <button className="tonalButton" disabled={busy} onClick={() => runScan(() => window.etoileBridge.chooseCharacterArcpkgAndScan())}>
                <FileArchive size={18} />
                {copy.chooseCharacterArcpkg}
              </button>
            </div>
            <PathAction label={copy.currentInput} value={scan?.sourcePath} onOpen={openSourceLocation} />
            <Info label={copy.inputType} value={scan?.sourceKind ?? '-'} />
            <details>
              <summary>workspace</summary>
              <PathBlock value={scan?.workspacePath ?? '-'} />
            </details>
          </Card>

          <Card title={copy.characterInfo}>
            <div className="formGrid">
              <TextInput label={copy.outputFileName} value={settings.outputFileName} onChange={(value) => setSettings({ ...settings, outputFileName: value })} />
              <TextInput label={copy.publisherId} value={settings.publisherId} onChange={(value) => setSettings({ ...settings, publisherId: sanitizeUiId(value) })} />
              <TextInput label={copy.characterId} value={settings.characterId} onChange={(value) => {
                const characterId = sanitizeUiId(value)
                setSettings({ ...settings, characterId, directory: characterId || settings.directory, outputFileName: `${settings.publisherId || 'etoilebridge'}.${characterId || 'character'}.arcpkg` })
              }} />
              <TextInput label="directory" value={settings.directory} onChange={(value) => setSettings({ ...settings, directory: sanitizeUiId(value) })} />
              <Info label={copy.identifierPreview} value={`${settings.publisherId || 'etoilebridge'}.${settings.characterId || 'character'}`} />
              <TextInput label={copy.defaultName} value={settings.defaultName} onChange={(value) => setSettings({ ...settings, defaultName: value })} />
              <TextInput label={copy.zhCnName} value={settings.zhCnName} onChange={(value) => setSettings({ ...settings, zhCnName: value })} />
              <TextInput label="imagePath" value={settings.imageFileName ?? ''} onChange={(value) => setSettings({ ...settings, imageFileName: value })} />
              <TextInput label="iconPath" value={settings.iconFileName ?? ''} onChange={(value) => setSettings({ ...settings, iconFileName: value })} />
            </div>
          </Card>

          <Card title={copy.characterImage}>
            <div className="resourceHeader">
              <div>
                <strong>{image?.name ?? copy.characterImage}</strong>
                <span className={image?.path ? 'okText' : 'mutedText'}>{image?.path ? copy.recognized : copy.unrecognized}</span>
              </div>
              <button className="textButton" onClick={chooseImage}>{image?.path ? copy.replace : copy.manualSelect}</button>
            </div>
            <ImagePreview title={copy.characterImage} resource={image} variant="character" onOpen={() => image?.previewUrl && setDetailsImage({ title: copy.characterImage, resource: image })} />
            <ResourceMeta resource={image} />
            <Info label="Alpha" value={scan?.imageHasAlpha == null ? '-' : scan.imageHasAlpha ? 'Yes' : 'No'} />
          </Card>

          <CharacterIconCropCard image={image} icon={icon} crop={crop} onCropChange={setCrop} onGenerate={generateIcon} disabled={busy || !image?.path} onIconOpen={() => icon?.previewUrl && setDetailsImage({ title: copy.characterIcon, resource: icon })} />
        </div>

        <div className="column">
          <CharacterResultPreviewCard image={image} settings={settings} onChange={setSettings} />

          <Card title={copy.saveArcpkg}>
            <button className="filledButton large" disabled={busy || !ready} onClick={save}>
              <Save size={18} />
              {copy.saveArcpkg}
            </button>
            {!ready && <p className="warningText">{copy.characterSaveWarning}</p>}
            {savedData && (
              <div className="resultPanel success">
                <Info label={copy.savePath} value={savedData.outputPath} />
                <Info label={copy.fileSize} value={formatBytes(savedData.sizeBytes)} />
                <Info label="identifier" value={savedData.identifier} />
                <Info label="directory" value={savedData.directory} />
                {savedData.validation && <CharacterValidationPanel result={savedData.validation} />}
                <button className="textButton compactButton" onClick={openSavedLocation}>{copy.openLocation}</button>
              </div>
            )}
          </Card>

          <StatusLogCard busy={busy} message={message} warnings={warnings} logs={logs} workspace={scan?.workspacePath} />
        </div>
      </div>

      {detailsImage && <GenericImageDialog title={detailsImage.title} resource={detailsImage.resource} onClose={() => setDetailsImage(null)} />}
    </>
  )
}

function CharacterIconCropCard({
  image,
  icon,
  crop,
  onCropChange,
  onGenerate,
  disabled,
  onIconOpen
}: {
  image?: ResourceInfo
  icon?: ResourceInfo
  crop: CharacterCropEdit
  onCropChange: (value: CharacterCropEdit) => void
  onGenerate: () => void
  disabled: boolean
  onIconOpen: () => void
}): ReactElement {
  const copy = useCopy()
  const cropSourceRef = useRef<HTMLDivElement | null>(null)
  const dragRef = useRef<{ mode: 'move' | 'resize' } | null>(null)
  const cropRef = useRef(crop)
  cropRef.current = crop
  const setCropValue = (patch: Partial<CharacterCropEdit>): void => onCropChange({ ...crop, ...patch })
  const imageWidth = image?.width || 1
  const imageHeight = image?.height || 1
  const minSide = Math.min(imageWidth, imageHeight)
  const cropWidthNorm = clamp((crop.cropSize * minSide) / imageWidth, 0.001, 1)
  const cropHeightNorm = clamp((crop.cropSize * minSide) / imageHeight, 0.001, 1)
  const cropStyle = {
    width: `${100 / cropWidthNorm}%`,
    height: `${100 / cropHeightNorm}%`,
    left: `${50 - (crop.centerX * 100) / cropWidthNorm}%`,
    top: `${50 - (crop.centerY * 100) / cropHeightNorm}%`
  }
  const cropBoxStyle: CSSProperties = {
    width: `${cropWidthNorm * 100}%`,
    height: `${cropHeightNorm * 100}%`,
    left: `${crop.centerX * 100}%`,
    top: `${crop.centerY * 100}%`
  }
  const applyPointer = (event: PointerEvent<HTMLDivElement>, mode: 'move' | 'resize'): void => {
    event.preventDefault()
    event.stopPropagation()
    const rect = cropSourceRef.current?.getBoundingClientRect()
    if (!rect) return
    const pointerX = clamp((event.clientX - rect.left) / rect.width, 0, 1)
    const pointerY = clamp((event.clientY - rect.top) / rect.height, 0, 1)
    const current = cropRef.current
    if (mode === 'move') {
      onCropChange({ ...current, centerX: pointerX, centerY: pointerY })
      return
    }
    const dx = Math.abs(pointerX - current.centerX) * imageWidth
    const dy = Math.abs(pointerY - current.centerY) * imageHeight
    const nextSize = clamp((Math.max(dx, dy) * 2) / minSide, 0.05, 1)
    onCropChange({ ...current, cropSize: nextSize })
  }
  const pointerMode = (event: PointerEvent<HTMLDivElement>): 'move' | 'resize' => {
    const rect = cropSourceRef.current?.getBoundingClientRect()
    if (!rect) return 'move'
    const pointerX = (event.clientX - rect.left) / rect.width
    const pointerY = (event.clientY - rect.top) / rect.height
    const halfW = cropWidthNorm / 2
    const halfH = cropHeightNorm / 2
    const nearX = Math.abs(Math.abs(pointerX - crop.centerX) - halfW) < 0.035
    const nearY = Math.abs(Math.abs(pointerY - crop.centerY) - halfH) < 0.035
    return nearX || nearY ? 'resize' : 'move'
  }
  const moveCenterToPointer = (event: PointerEvent<HTMLDivElement>): void => {
    event.preventDefault()
    const rect = cropSourceRef.current?.getBoundingClientRect()
    if (!rect) return
    onCropChange({
      ...cropRef.current,
      centerX: clamp((event.clientX - rect.left) / rect.width, 0, 1),
      centerY: clamp((event.clientY - rect.top) / rect.height, 0, 1)
    })
  }
  return (
    <Card title={copy.iconCrop}>
      {!image?.previewUrl && <p className="hint">{copy.cropHint}</p>}
      {image?.previewUrl && (
        <div className="iconCropGrid">
          <div
            ref={cropSourceRef}
            className="cropSource"
            style={{ aspectRatio: `${imageWidth} / ${imageHeight}` }}
            onPointerDown={moveCenterToPointer}
            onWheel={(event: WheelEvent<HTMLDivElement>) => {
              event.preventDefault()
              const direction = event.deltaY > 0 ? 1 : -1
              setCropValue({ cropSize: clamp(crop.cropSize + direction * 0.035, 0.05, 1) })
            }}
          >
            <img src={image.previewUrl} alt="crop source" draggable={false} />
            <div
              className="cropBox"
              style={cropBoxStyle}
              onPointerDown={(event) => {
                const mode = pointerMode(event)
                dragRef.current = { mode }
                event.currentTarget.setPointerCapture(event.pointerId)
                applyPointer(event, mode)
              }}
              onPointerMove={(event) => {
                if (!dragRef.current) return
                applyPointer(event, dragRef.current.mode)
              }}
              onPointerUp={(event) => {
                dragRef.current = null
                if (event.currentTarget.hasPointerCapture(event.pointerId)) {
                  event.currentTarget.releasePointerCapture(event.pointerId)
                }
              }}
              onPointerCancel={(event) => {
                dragRef.current = null
                if (event.currentTarget.hasPointerCapture(event.pointerId)) {
                  event.currentTarget.releasePointerCapture(event.pointerId)
                }
              }}
            >
              <span className="cropHandle cropHandleNw" />
              <span className="cropHandle cropHandleNe" />
              <span className="cropHandle cropHandleSw" />
              <span className="cropHandle cropHandleSe" />
            </div>
          </div>
          <button className="iconPreviewLarge" disabled={!icon?.previewUrl} onClick={onIconOpen}>
            {icon?.previewUrl ? <img src={icon.previewUrl} alt="icon preview" style={{ left: 0, top: 0, width: '100%', height: '100%' }} /> : <img src={image.previewUrl} alt="live icon preview" style={cropStyle} />}
          </button>
        </div>
      )}
      <SliderNumber label="centerX" value={crop.centerX} min={0} max={1} step={0.01} onChange={(centerX) => setCropValue({ centerX })} />
      <SliderNumber label="centerY" value={crop.centerY} min={0} max={1} step={0.01} onChange={(centerY) => setCropValue({ centerY })} />
      <SliderNumber label="cropSize" value={crop.cropSize} min={0.05} max={1} step={0.01} onChange={(cropSize) => setCropValue({ cropSize })} />
      <div className="buttonRow">
        <button className="tonalButton" onClick={() => onCropChange({ centerX: 0.5, centerY: 0.35, cropSize: 0.45 })}>{copy.resetCrop}</button>
        <button className="filledButton" disabled={disabled} onClick={onGenerate}>{copy.generateIcon}</button>
      </div>
      <ResourceMeta resource={icon} />
    </Card>
  )
}

function CharacterResultPreviewCard({ image, settings, onChange }: { image?: ResourceInfo; settings: CharacterSettingsEdit; onChange: (value: CharacterSettingsEdit) => void }): ReactElement {
  const copy = useCopy()
  const x = Number(settings.x) || 0
  const y = Number(settings.y) || 0
  const scale = Number(settings.scale) || 0
  const mapped = mapCharacterToResultCanvas(image, x, y, scale)
  const outside = image?.previewUrl && !mapped.intersectsCanvas
  const stageRef = useRef<HTMLDivElement | null>(null)
  const [stageSize, setStageSize] = useState({ width: 0, height: 0 })
  const update = (patch: Partial<CharacterSettingsEdit>): void => onChange({ ...settings, ...patch })
  useEffect(() => {
    const element = stageRef.current
    if (!element) return
    const updateSize = (): void => {
      const rect = element.getBoundingClientRect()
      setStageSize({ width: rect.width, height: rect.height })
    }
    updateSize()
    const observer = new ResizeObserver(updateSize)
    observer.observe(element)
    return () => observer.disconnect()
  }, [])
  const displayScale = stageSize.width > 0 && stageSize.height > 0
    ? Math.min(stageSize.width / androidPortedResultLayout.logicalWidth, stageSize.height / androidPortedResultLayout.logicalHeight)
    : 0
  return (
    <Card title={copy.resultPreview}>
      <div className="resultPreviewStage" ref={stageRef}>
        {resultPreviewBackLayers.map((layer, index) => (
          <ResultPreviewLayerView key={`back-${index}-${layer.kind}`} layer={layer} />
        ))}
        {image?.previewUrl && (
          <img
            className="resultCharacter"
            src={image.previewUrl}
            alt="character"
            draggable={false}
            style={rectCss(mapped.logicalDrawRect)}
          />
        )}
        {resultPreviewFrontLayers.map((layer, index) => (
          <ResultPreviewLayerView key={`front-${index}-${layer.kind}`} layer={layer} />
        ))}
        {image?.previewUrl && (
          <div
            className="resultPivotPoint"
            style={{
              left: `${(mapped.logicalPivot.x / androidPortedResultLayout.logicalWidth) * 100}%`,
              top: `${(mapped.logicalPivot.y / androidPortedResultLayout.logicalHeight) * 100}%`
            }}
          />
        )}
      </div>
      {outside && <p className="warningText">{copy.outsideCharacter}</p>}
      <SliderNumber label="x" value={x} min={-1000} max={1000} step={1} onChange={(value) => update({ x: String(value) })} />
      <SliderNumber label="y" value={y} min={-1000} max={1000} step={1} onChange={(value) => update({ y: String(value) })} />
      <SliderNumber label="scale" value={scale} min={0.1} max={1.8} step={0.01} onChange={(value) => update({ scale: String(value) })} />
      <div className="buttonRow">
        <button className="textButton" onClick={() => update({ x: '300', y: '100', scale: '0.7' })}>{copy.sampleDefault}</button>
        <button className="textButton" onClick={() => update({ x: '300', y: '100', scale: '0.7' })}>{copy.reset}</button>
        <button className="textButton" onClick={() => update({ x: '640', y: '365' })}>{copy.center}</button>
        <button className="textButton" onClick={() => update({ scale: String((androidPortedResultLayout.logicalHeight / CHARACTER_IMAGE_HEIGHT).toFixed(2)) })}>{copy.fitHeight}</button>
        <button className="textButton" onClick={() => update({ scale: image?.width && image.height ? String((androidPortedResultLayout.logicalWidth / (CHARACTER_IMAGE_HEIGHT * (image.width / image.height))).toFixed(2)) : settings.scale })}>{copy.fitWidth}</button>
      </div>
      <details>
        <summary>{copy.previewDebug}</summary>
        <div className="summaryGrid">
          <Info label="Layout profile" value={androidPortedResultLayout.profileName} />
          <Info label="Source size" value={image?.width && image.height ? `${image.width} x ${image.height}` : '-'} />
          <Info label="Logical canvas" value={`${androidPortedResultLayout.logicalWidth} x ${androidPortedResultLayout.logicalHeight}`} />
          <Info label="Draw size" value={`${widthOf(mapped.logicalDrawRect).toFixed(1)} x ${heightOf(mapped.logicalDrawRect).toFixed(1)}`} />
          <Info label="Draw rect" value={`${mapped.logicalDrawRect.left.toFixed(1)}, ${mapped.logicalDrawRect.top.toFixed(1)} - ${mapped.logicalDrawRect.right.toFixed(1)}, ${mapped.logicalDrawRect.bottom.toFixed(1)}`} />
          <Info label="Visible rect" value={`${mapped.logicalVisibleBounds.left.toFixed(1)}, ${mapped.logicalVisibleBounds.top.toFixed(1)} - ${mapped.logicalVisibleBounds.right.toFixed(1)}, ${mapped.logicalVisibleBounds.bottom.toFixed(1)}`} />
          <Info label="Partner pivot" value={`${mapped.logicalPivot.x.toFixed(1)}, ${mapped.logicalPivot.y.toFixed(1)}`} />
          <Info label="displayScale" value={displayScale > 0 ? displayScale.toFixed(4) : '-'} />
          <Info label="Intersects canvas" value={String(mapped.intersectsCanvas)} />
        </div>
      </details>
    </Card>
  )
}

function ResultPreviewLayerView({ layer }: { layer: ResultPreviewLayer }): ReactElement {
  if (layer.kind === 'block') {
    return (
      <div
        className={`resultBlockLayer ${layer.className}`}
        style={{
          ...rectCss(layer.rect),
          borderRadius: `${layer.radius ?? 0}px`
        }}
      />
    )
  }
  return <ResultLayer src={assetForResultLayer(layer.key)} rect={layer.rect} className={layer.className} />
}

function ResultLayer({ src, rect, className }: { src: string; rect: PreviewBounds; className?: string }): ReactElement {
  return (
    <img
      className={`resultAssetLayer ${className ?? ''}`}
      src={src}
      alt=""
      draggable={false}
      style={rectCss(rect)}
    />
  )
}

function assetForResultLayer(key: ResultAssetLayerKey): string {
  switch (key) {
    case 'backgroundArrow':
      return acResultBackgroundArrow
    case 'clearGlow':
      return acResultClearGlow
    case 'jacketBackground':
      return acResultJacketBackground
    case 'scoreFrame':
      return acResultScoreFrame
    case 'judgementTable':
      return acResultJudgementTable
    case 'judgementHighlight':
      return acResultJudgementTableHighlight
    case 'playRetryBackground':
      return acResultPlayRetryBackground
    case 'playRetryFrame':
      return acResultPlayRetryFrame
  }
}

function CharacterValidationPanel({ result }: { result: NonNullable<CharacterConvertResult['validation']> }): ReactElement {
  const copy = useCopy()
  return (
    <div className={`resultPanel ${result.valid ? 'success' : 'error'}`}>
      <Info label="validator" value={result.valid ? 'passed' : 'failed'} />
      <Info label="character entries" value={String(result.characterEntryCount)} />
      <Info label="image exists" value={String(result.imageExists)} />
      <Info label="icon exists" value={String(result.iconExists)} />
      <LogList items={[...(result.errors ?? []), ...(result.warnings ?? [])]} empty={copy.validatorNoErrors} />
    </div>
  )
}

function SliderNumber({ label, value, min, max, step, onChange }: { label: string; value: number; min: number; max: number; step: number; onChange: (value: number) => void }): ReactElement {
  return (
    <label className="sliderNumber">
      <span>{label}</span>
      <input type="range" min={min} max={max} step={step} value={value} onChange={(event) => onChange(Number(event.target.value))} />
      <input type="number" min={min} max={max} step={step} value={value} onChange={(event) => onChange(Number(event.target.value))} />
    </label>
  )
}

function MetadataCard({ metadata, onChange }: { metadata: SingleMetadataEdit; onChange: (value: SingleMetadataEdit) => void }): ReactElement {
  const copy = useCopy()
  const identifier = `${metadata.publisherId || 'etoilebridge'}.${metadata.levelId || 'levelId'}`
  const update = (patch: Partial<SingleMetadataEdit>): void => onChange({ ...metadata, ...patch })
  return (
    <Card title={copy.metadata}>
      <div className="formGrid">
        <TextInput label={copy.publisherId} value={metadata.publisherId} onChange={(value) => update({ publisherId: value })} />
        <TextInput label={copy.levelId} value={metadata.levelId} onChange={(value) => update({ levelId: value })} />
        <Info label={copy.identifierPreview} value={identifier} />
        <TextInput label={copy.title} value={metadata.title} onChange={(value) => update({ title: value })} />
        <TextInput label={copy.artistComposer} value={metadata.artist} onChange={(value) => update({ artist: value })} />
        <TextInput label={copy.bpmBaseLabel} value={metadata.bpmBase} onChange={(value) => update({ bpmBase: value })} />
        <TextInput label={copy.bpmTextLabel} value={metadata.bpmText} onChange={(value) => update({ bpmText: value })} />
      </div>
      <div className="chartEditList">
        {metadata.charts.map((chart, index) => (
          <div className="chartEditor" key={`${chart.ratingClass}-${chart.affPath ?? index}`}>
            <div className="chartEditorTitle">
              <strong>ratingClass {chart.ratingClass}</strong>
              <label className="toggleLine">
                <input type="checkbox" checked={chart.adopted} onChange={(event) => updateChart(metadata, onChange, index, { adopted: event.target.checked })} />
                {copy.adopt}
              </label>
            </div>
            <div className="chartFieldGrid">
              <TextInput label={copy.difficulty} value={chart.difficulty} onChange={(value) => updateChart(metadata, onChange, index, { difficulty: value })} />
              <TextInput label="chartConstant" value={chart.chartConstant} onChange={(value) => updateChart(metadata, onChange, index, { chartConstant: value })} />
              <div className="wideField">
                <TextInput label="charter" value={chart.charter} onChange={(value) => updateChart(metadata, onChange, index, { charter: value })} />
              </div>
              <div className="wideField">
                <TextInput label="illustrator" value={chart.illustrator} onChange={(value) => updateChart(metadata, onChange, index, { illustrator: value })} />
              </div>
              {metadata.showAlias && (
                <div className="wideField">
                  <TextInput label="alias" value={chart.alias} onChange={(value) => updateChart(metadata, onChange, index, { alias: value })} />
                </div>
              )}
            </div>
            <span className="fieldCaption">chartPath</span>
            <PathBlock value={chart.affPath ?? '-'} />
          </div>
        ))}
      </div>
    </Card>
  )
}

function ResourcesCard({
  resources,
  manualResources,
  onChoose,
  onClear,
  onImageOpen
}: {
  resources: Record<ResourceKind, ResourceInfo | undefined>
  manualResources: Partial<Record<ResourceKind, ResourceInfo>>
  onChoose: (kind: ResourceKind) => void
  onClear: (kind: ResourceKind) => void
  onImageOpen: (title: string, resource: ResourceInfo, kind: ResourceKind) => void
}): ReactElement {
  const copy = useCopy()
  const rows: Array<{ kind: ResourceKind; label: string; image?: boolean }> = [
    { kind: 'audioPath', label: copy.audio },
    { kind: 'jacketPath', label: copy.jacket, image: true },
    { kind: 'backgroundPath', label: copy.background, image: true },
    { kind: 'songlistPath', label: 'songlist' },
    { kind: 'packlistPath', label: 'packlist' }
  ]
  return (
    <Card title={copy.resources}>
      <div className="resourceList">
        {rows.map((row) => {
          const resource = resources[row.kind]
          return (
            <div className="resourceCard" key={row.kind}>
              <div className="resourceHeader">
                <div>
                  <strong>{row.label}</strong>
                  <span className={resource?.path ? 'okText' : 'mutedText'}>{resource?.path ? copy.recognized : copy.unrecognized}</span>
                </div>
                <div className="buttonRow right">
                  <button className="textButton" onClick={() => onChoose(row.kind)}>{resource?.path ? copy.replace : copy.manualSelect}</button>
                  {manualResources[row.kind]?.path && <button className="textButton" onClick={() => onClear(row.kind)}>{copy.clear}</button>}
                </div>
              </div>
              {row.image && <ImagePreview title={row.label} resource={resource} onOpen={() => resource?.previewUrl && onImageOpen(row.label, resource, row.kind)} />}
              <ResourceMeta resource={resource} />
            </div>
          )
        })}
      </div>
    </Card>
  )
}

function PackEntriesCard({
  entries,
  scanEntries,
  expandedKeys,
  onExpandedKeysChange,
  onChange,
  onImageOpen
}: {
  entries: PackSettingsEdit['entries']
  scanEntries: PackLevelInfo[]
  expandedKeys: Set<string>
  onExpandedKeysChange: (keys: Set<string>) => void
  onChange: (entries: PackSettingsEdit['entries']) => void
  onImageOpen: (title: string, resource: ResourceInfo) => void
}): ReactElement {
  const copy = useCopy()
  const byKey = new Map(scanEntries.map((entry) => [entry.key, entry]))
  const compactMode = entries.length > 5
  const setAllExpanded = (expanded: boolean): void => {
    onExpandedKeysChange(expanded ? new Set(entries.map((entry) => entry.key)) : new Set())
  }
  const toggleExpanded = (key: string): void => {
    const next = new Set(expandedKeys)
    if (next.has(key)) {
      next.delete(key)
    } else {
      next.add(key)
    }
    onExpandedKeysChange(next)
  }
  return (
    <Card title={copy.levelList}>
      {compactMode && (
        <div className="packListToolbar">
          <span className="hint">{copy.compactLevelListHint}</span>
          <div className="buttonRow right">
            <button className="textButton" onClick={() => setAllExpanded(true)}>{copy.expandAll}</button>
            <button className="textButton" onClick={() => setAllExpanded(false)}>{copy.collapseAll}</button>
          </div>
        </div>
      )}
      <div className="packEntryList">
        {entries.length === 0 && <p className="hint">{copy.scanForLevelList}</p>}
        {entries.map((entry, entryIndex) => {
          const source = byKey.get(entry.key)
          const expanded = !compactMode || expandedKeys.has(entry.key)
          return (
            <div className={`packEntryCard ${expanded ? 'expanded' : 'compact'}`} key={entry.key}>
              <div className="packEntryHeader">
                <ImagePreview
                  title="jacket"
                  resource={source?.jacket}
                  variant="thumb"
                  onOpen={() => source?.jacket?.previewUrl && onImageOpen(entry.title || source?.title || copy.jacket, source.jacket)}
                />
                <div className="packEntryTitle">
                  <strong>{entry.title || source?.title || source?.songId || source?.directory || 'Untitled'}</strong>
                  <p className="hint">{entry.artist || source?.artist || '-'} / {entry.levelId || source?.levelId || source?.songId || '-'} / charts {entry.charts.length}</p>
                  <p className="hint">{source?.identifier ?? `etoilebridge.${entry.levelId || 'level'}`}</p>
                </div>
                <div className="packEntryActions">
                  <label className="toggleLine">
                    <input type="checkbox" checked={entry.enabled} onChange={(event) => updatePackEntry(entries, onChange, entryIndex, { enabled: event.target.checked })} />
                    {copy.packageVerb}
                  </label>
                  {compactMode && (
                    <button className="textButton" onClick={() => toggleExpanded(entry.key)}>
                      {expanded ? copy.collapse : copy.expand}
                    </button>
                  )}
                </div>
              </div>
              {expanded && (
                <div className="packEntryDetails">
                  <div className="formGrid">
                    <TextInput label="title" value={entry.title} onChange={(value) => updatePackEntry(entries, onChange, entryIndex, { title: value })} />
                    <TextInput label="composer" value={entry.artist} onChange={(value) => updatePackEntry(entries, onChange, entryIndex, { artist: value })} />
                    <TextInput label="levelId" value={entry.levelId} onChange={(value) => updatePackEntry(entries, onChange, entryIndex, { levelId: sanitizeUiId(value) })} />
                    <Info label={copy.identifierPreview} value={`etoilebridge.${entry.levelId || source?.levelId || source?.songId || 'level'}`} />
                    <Info label={copy.resource} value={source?.resourceStatus ?? '-'} />
                    <Info label={copy.statusLabel} value={source?.failureReason ?? (source?.canConvert === false ? 'failed' : 'ok')} />
                  </div>
                  {source?.warnings?.length ? <LogList items={source.warnings} empty="" /> : null}
                  <div className="chartEditList">
                    {entry.charts.map((chart, chartIndex) => (
                      <div className="chartEditor" key={`${entry.key}-${chart.ratingClass}-${chartIndex}`}>
                        <div className="chartEditorTitle">
                          <strong>ratingClass {chart.ratingClass}</strong>
                          <label className="toggleLine">
                            <input type="checkbox" checked={chart.enabled} onChange={(event) => updatePackChart(entries, onChange, entryIndex, chartIndex, { enabled: event.target.checked })} />
                            {copy.adopt}
                          </label>
                        </div>
                        <div className="chartFieldGrid">
                          <TextInput label="difficulty" value={chart.difficulty} onChange={(value) => updatePackChart(entries, onChange, entryIndex, chartIndex, { difficulty: value })} />
                          <TextInput label="chartConstant" value={chart.chartConstant} onChange={(value) => updatePackChart(entries, onChange, entryIndex, chartIndex, { chartConstant: value })} />
                          <div className="wideField"><TextInput label="charter" value={chart.charter} onChange={(value) => updatePackChart(entries, onChange, entryIndex, chartIndex, { charter: value })} /></div>
                          <div className="wideField"><TextInput label="illustrator" value={chart.illustrator} onChange={(value) => updatePackChart(entries, onChange, entryIndex, chartIndex, { illustrator: value })} /></div>
                        </div>
                        <span className="fieldCaption">chartPath</span>
                        <PathBlock value={source?.charts?.[chartIndex]?.chartPath ?? '-'} />
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )
        })}
      </div>
    </Card>
  )
}

function AffMappingCard({ metadata, onChange, ignoredCount }: { metadata: SingleMetadataEdit; onChange: (value: SingleMetadataEdit) => void; ignoredCount: number }): ReactElement {
  const copy = useCopy()
  const adopted = metadata.charts.filter((chart) => chart.adopted).length
  return (
    <Card title="AFF Mapping">
      <div className="summaryGrid">
        <Info label={copy.adoptedAff} value={String(adopted)} />
        <Info label={copy.ignoredAff} value={String(Math.max(0, ignoredCount))} />
      </div>
      <div className="affList">
        {metadata.charts.map((chart, index) => (
          <div className="affRow" key={`${chart.ratingClass}-${chart.affPath ?? index}`}>
            <label className="toggleLine">
              <input type="checkbox" checked={chart.adopted} onChange={(event) => updateChart(metadata, onChange, index, { adopted: event.target.checked })} />
              {chart.adopted ? copy.adopt : copy.ignore}
            </label>
            <div className="affMain">
              <strong title={chart.affPath ?? ''}>{chart.affPath ? basename(chart.affPath) : '-'}</strong>
              <span title={chart.affPath ?? ''}>{chart.affPath ?? '-'}</span>
            </div>
            <span>ratingClass {chart.ratingClass}</span>
            <span title={chart.difficulty}>{chart.difficulty || copy.unnamedDifficulty}</span>
          </div>
        ))}
      </div>
    </Card>
  )
}

function AppearanceCard({ value, onChange }: { value: AppearanceEdit; onChange: (value: AppearanceEdit) => void }): ReactElement {
  const copy = useCopy()
  return (
    <Card title={copy.appearance}>
      <ChipGroup title={copy.particle} value={value.particle} options={appearanceOptions.particle} onChange={(particle) => onChange({ ...value, particle })} />
      <ChipGroup title={copy.accent} value={value.accent} options={appearanceOptions.accent} onChange={(accent) => onChange({ ...value, accent })} />
      <ChipGroup title={copy.track} value={value.track} options={appearanceOptions.track} onChange={(track) => onChange({ ...value, track })} />
      <ChipGroup title={copy.singleLine} value={value.singleLine} options={appearanceOptions.singleLine} onChange={(singleLine) => onChange({ ...value, singleLine })} />
    </Card>
  )
}

function PreprocessCard({ value, onChange }: { value: PreprocessEdit; onChange: (value: PreprocessEdit) => void }): ReactElement {
  const copy = useCopy()
  return (
    <Card title={copy.preprocess}>
      <CheckboxLine label={copy.deleteDesignant} checked={value.deleteDesignantLine} onChange={(deleteDesignantLine) => onChange({ ...value, deleteDesignantLine })} />
      <CheckboxLine label={copy.fixZeroArcTap} checked={value.fixZeroDurationArcTap} onChange={(fixZeroDurationArcTap) => onChange({ ...value, fixZeroDurationArcTap })} />
      <CheckboxLine label={copy.fixReversedArc} checked={value.fixReversedArcTime} onChange={(fixReversedArcTime) => onChange({ ...value, fixReversedArcTime })} />
      <CheckboxLine label={copy.expandTiminggroup} checked={value.expandArcResolution} onChange={(expandArcResolution) => onChange({ ...value, expandArcResolution })} />
    </Card>
  )
}

function StatusLogCard({ busy, message, warnings, logs, workspace }: { busy: boolean; message: ActionResult<unknown> | null; warnings: string[]; logs: string[]; workspace?: string }): ReactElement {
  const copy = useCopy()
  return (
    <Card title={copy.statusLog}>
      {busy && <p className="hint">{copy.processing}</p>}
      {message && <ResultPanel result={message} />}
      <Info label="warnings" value={String(warnings.length)} />
      <details>
        <summary>{copy.warningList}</summary>
        <LogList items={warnings} empty={copy.noWarnings} />
      </details>
      <details>
        <summary>{copy.workerLogs}</summary>
        <LogList items={logs} empty={copy.noLogs} />
      </details>
      <details>
        <summary>{copy.workspaceInfo}</summary>
        <PathBlock value={workspace ?? '-'} />
      </details>
    </Card>
  )
}

function PackValidationPanel({ result }: { result: NonNullable<PackConvertResult['validation']> }): ReactElement {
  const copy = useCopy()
  return (
    <div className={`resultPanel ${result.valid ? 'success' : 'error'}`}>
      <Info label="validator" value={result.valid ? 'passed' : 'failed'} />
      <Info label="pack entries" value={String(result.packEntryCount)} />
      <Info label="level entries" value={String(result.levelEntryCount)} />
      <Info label="pack image exists" value={String(result.packImageExists)} />
      <Info label="levelIdentifiers match" value={String(result.levelIdentifiersMatch)} />
      <LogList items={[...(result.errors ?? []), ...(result.warnings ?? [])]} empty={copy.validatorNoErrors} />
    </div>
  )
}

function ResourceMeta({ resource }: { resource?: ResourceInfo }): ReactElement {
  const copy = useCopy()
  return (
    <div className="resourceMeta">
      <Info label={copy.fileName} value={resource?.name ?? '-'} />
      <Info label={copy.source} value={resource?.source ?? '-'} />
      <Info label={copy.fileSize} value={resource?.sizeBytes != null ? formatBytes(resource.sizeBytes) : '-'} />
      {resource?.width && resource.height ? <Info label={copy.imageSize} value={`${resource.width} x ${resource.height}`} /> : null}
      <details>
        <summary>{copy.detailedPath}</summary>
        <PathBlock value={resource?.path ?? '-'} />
      </details>
    </div>
  )
}

function ChipGroup({ title, value, options, onChange }: { title: string; value: string; options: readonly (readonly [string, string])[]; onChange: (value: string) => void }): ReactElement {
  const copy = useCopy()
  return (
    <div className="chipGroup">
      <strong>{title}</strong>
      <div className="chips">
        {options.map(([id, label]) => (
          <button key={id} className={`chip ${value === id ? 'selected' : ''}`} onClick={() => onChange(id)}>
            {copy.locale === 'en' ? label.split(' / ')[0] : label}
          </button>
        ))}
      </div>
    </div>
  )
}

function CheckboxLine({ label, checked, onChange }: { label: string; checked: boolean; onChange: (value: boolean) => void }): ReactElement {
  return (
    <label className="checkLine">
      <input type="checkbox" checked={checked} onChange={(event) => onChange(event.target.checked)} />
      <span>{label}</span>
    </label>
  )
}

function PlaceholderPage({ icon, title }: { icon: ReactElement; title: string }): ReactElement {
  const copy = useCopy()
  return (
    <div className="placeholder">
      <div className="placeholderIcon">{icon}</div>
      <h2>{title}</h2>
      <p>{copy.placeholderState}</p>
    </div>
  )
}

function Card({ title, children, className }: { title: string; children: ReactNode; className?: string }): ReactElement {
  return (
    <section className={`card edgeAwareCard${className ? ` ${className}` : ''}`}>
      <h2>{title}</h2>
      {children}
    </section>
  )
}

function TextInput({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }): ReactElement {
  return (
    <label className="textField">
      <span>{label}</span>
      <input value={value} title={value} onChange={(event: ChangeEvent<HTMLInputElement>) => onChange(event.target.value)} />
    </label>
  )
}

function Info({ label, value, muted = false }: { label: string; value: string; muted?: boolean }): ReactElement {
  return (
    <div className="infoLine">
      <span>{label}</span>
      <strong className={muted ? 'mutedText' : ''} title={value}>
        {value}
      </strong>
    </div>
  )
}

function PathAction({ label, value, onOpen }: { label: string; value?: string; onOpen: () => void }): ReactElement {
  const copy = useCopy()
  return (
    <div className="pathActionBlock">
      <div className="pathActionHeader">
        <span className="fieldCaption">{label}</span>
        <button className="textButton compactButton" disabled={!value} onClick={onOpen}>
          {copy.openLocation}
        </button>
      </div>
      <PathBlock value={value ?? '-'} />
    </div>
  )
}

function ImagePreview({ title, resource, onOpen, variant: forcedVariant }: { title: string; resource?: ResourceInfo; onOpen: () => void; variant?: 'auto' | 'jacket' | 'background' | 'portrait' | 'character' | 'thumb' }): ReactElement {
  const ratio = resource?.width && resource.height ? resource.width / resource.height : 0
  const variant = forcedVariant && forcedVariant !== 'auto' ? forcedVariant : ratio > 1.2 ? 'background' : 'jacket'
  const style = variant === 'character' && resource?.width && resource.height
    ? ({ '--preview-ratio': `${resource.width} / ${resource.height}` } as CSSProperties)
    : undefined
  return (
    <button className={`imagePreview ${variant} ${resource?.previewUrl ? '' : 'empty'}`} style={style} disabled={!resource?.previewUrl} onClick={onOpen}>
      {resource?.previewUrl ? <img src={resource.previewUrl} alt={title} /> : <ImageIcon size={28} />}
      <span>{title}</span>
    </button>
  )
}

function ImageDialog({
  image,
  onClose,
  onChoose
}: {
  image: { title: string; resource: ResourceInfo; kind: ResourceKind }
  onClose: () => void
  onChoose: (kind: ResourceKind) => void
}): ReactElement {
  const copy = useCopy()
  return (
    <ModalPortal
      title={image.title}
      closeLabel={copy.close}
      panelClassName="imageDetailPanel"
      onClose={onClose}
      actions={<button className="tonalButton" onClick={() => onChoose(image.kind)}>{copy.replace}</button>}
    >
      {image.resource.previewUrl && <img className="imageDetailImage" src={image.resource.previewUrl} alt={image.title} />}
      <div className="dialogMeta">
        <Info label={copy.type} value={image.kind === 'jacketPath' ? copy.jacket : copy.background} />
        <ResourceMeta resource={image.resource} />
      </div>
    </ModalPortal>
  )
}

function GenericImageDialog({
  title,
  resource,
  onClose,
  actions
}: {
  title: string
  resource: ResourceInfo
  onClose: () => void
  actions?: ReactNode
}): ReactElement {
  const copy = useCopy()
  return (
    <ModalPortal title={title} closeLabel={copy.close} panelClassName="imageDetailPanel" actions={actions} onClose={onClose}>
      {resource.previewUrl && <img className="imageDetailImage" src={resource.previewUrl} alt={title} />}
      <ResourceMeta resource={resource} />
    </ModalPortal>
  )
}

function ResultPanel({ result }: { result: ActionResult<unknown> }): ReactElement {
  const copy = useCopy()
  return (
    <div className={`resultPanel ${result.ok ? 'success' : 'error'}`}>
      <div className="resultTitle">
        {result.ok ? <CheckCircle2 size={18} /> : <AlertTriangle size={18} />}
        <span>{result.ok ? copy.done : copy.failed}</span>
      </div>
      {result.error && <p>{result.error}</p>}
    </div>
  )
}

function LogList({ items, empty }: { items: string[]; empty: string }): ReactElement {
  if (items.length === 0) return <p className="hint">{empty}</p>
  return (
    <div className="logList">
      {items.map((item, index) => (
        <p key={`${index}-${item}`}>{item}</p>
      ))}
    </div>
  )
}

function PathBlock({ value }: { value: string }): ReactElement {
  return (
    <code className="pathBlock" title={value}>
      {value}
    </code>
  )
}

function updateChart(metadata: SingleMetadataEdit, onChange: (value: SingleMetadataEdit) => void, index: number, patch: Partial<SingleMetadataEdit['charts'][number]>): void {
  onChange({
    ...metadata,
    charts: metadata.charts.map((chart, chartIndex) => (chartIndex === index ? { ...chart, ...patch } : chart))
  })
}

function updatePackEntry(
  entries: PackSettingsEdit['entries'],
  onChange: (entries: PackSettingsEdit['entries']) => void,
  index: number,
  patch: Partial<PackSettingsEdit['entries'][number]>
): void {
  onChange(entries.map((entry, entryIndex) => (entryIndex === index ? { ...entry, ...patch } : entry)))
}

function updatePackChart(
  entries: PackSettingsEdit['entries'],
  onChange: (entries: PackSettingsEdit['entries']) => void,
  entryIndex: number,
  chartIndex: number,
  patch: Partial<PackSettingsEdit['entries'][number]['charts'][number]>
): void {
  onChange(
    entries.map((entry, currentEntryIndex) =>
      currentEntryIndex === entryIndex
        ? {
            ...entry,
            charts: entry.charts.map((chart, currentChartIndex) => (currentChartIndex === chartIndex ? { ...chart, ...patch } : chart))
          }
        : entry
    )
  )
}

function metadataFromScan(scan: SingleScanResult): SingleMetadataEdit {
  const charts = (scan.charts.length > 0 ? scan.charts : scan.affFiles.map((aff) => ({ ratingClass: aff.ratingClass, affPath: aff.path, affName: aff.name } as ChartInfo))).map((chart) => ({
    ratingClass: chart.ratingClass,
    difficulty: chart.difficulty ?? '',
    chartConstant: chart.chartConstant != null ? String(chart.chartConstant) : '',
    charter: chart.charter ?? '',
    illustrator: chart.illustrator ?? '',
    alias: chart.alias ?? '',
    affPath: chart.affPath,
    adopted: true
  }))
  const showAlias = scan.sourceKind === 'arccreate-project' || scan.sourceKind === 'arccreate-arcpkg'
  return {
    publisherId: 'etoilebridge',
    levelId: scan.songId ?? '',
    title: scan.title ?? '',
    artist: scan.artist ?? '',
    bpmText: scan.bpmText ?? '',
    bpmBase: scan.bpmBase != null ? String(scan.bpmBase) : '',
    charts,
    showAlias
  }
}

function emptyMetadata(): SingleMetadataEdit {
  return {
    publisherId: 'etoilebridge',
    levelId: '',
    title: '',
    artist: '',
    bpmText: '',
    bpmBase: '',
    charts: [],
    showAlias: false
  }
}

function defaultAppearance(): AppearanceEdit {
  return { particle: 'INHERIT', accent: 'INHERIT', track: 'INHERIT', singleLine: 'NONE' }
}

function defaultPreprocess(): PreprocessEdit {
  return {
    deleteDesignantLine: true,
    fixZeroDurationArcTap: true,
    fixReversedArcTime: true,
    expandArcResolution: true
  }
}

function emptyPackSettings(): PackSettingsEdit {
  return {
    publisherId: 'etoilebridge',
    outputFileName: 'etoilebridge.pack.arcpkg',
    packName: '',
    packId: 'pack',
    packIdentifier: 'etoilebridge.pack.pack',
    packDirectory: 'pack',
    entries: [],
    appearance: defaultAppearance(),
    preprocess: defaultPreprocess()
  }
}

function emptyCharacterSettings(): CharacterSettingsEdit {
  return {
    publisherId: 'etoilebridge',
    characterId: 'character',
    directory: 'character',
    outputFileName: 'etoilebridge.character.arcpkg',
    defaultName: '',
    zhCnName: '',
    x: '300',
    y: '100',
    scale: '0.7'
  }
}

function characterSettingsFromScan(scan: CharacterScanResult): CharacterSettingsEdit {
  return {
    publisherId: scan.publisherId || 'etoilebridge',
    characterId: scan.characterId || 'character',
    directory: scan.directory || scan.characterId || 'character',
    outputFileName: scan.outputFileName || `${scan.publisherId || 'etoilebridge'}.${scan.characterId || 'character'}.arcpkg`,
    defaultName: scan.defaultName || scan.characterId || 'Character',
    zhCnName: scan.zhCnName ?? '',
    imagePath: scan.image?.path,
    iconPath: scan.icon?.path,
    imageFileName: scan.imagePath || scan.image?.name,
    iconFileName: scan.iconPath || scan.icon?.name || `${scan.characterId || 'character'}_icon.png`,
    x: String(scan.x ?? 300),
    y: String(scan.y ?? 100),
    scale: String(scan.scale ?? 0.7)
  }
}

function packSettingsFromScan(scan: PackScanResult): PackSettingsEdit {
  const packId = sanitizeUiId(scan.packId || scan.packName || 'pack') || 'pack'
  const publisherId = scan.publisherId || 'etoilebridge'
  const packIdentifier = scan.packIdentifier || buildPackIdentifier(publisherId, packId)
  return {
    ...emptyPackSettings(),
    publisherId,
    outputFileName: `${packIdentifier.replace(/\.pack$/i, '')}.arcpkg`,
    packName: scan.packName || packId,
    packId,
    packIdentifier,
    packDirectory: scan.packDirectory || packId,
    packImagePath: scan.packImage?.path,
    entries: scan.entries.map(packEntryEditFromScan)
  }
}

function defaultPackExpandedKeys(entries: PackSettingsEdit['entries']): Set<string> {
  return entries.length <= 5 ? new Set(entries.map((entry) => entry.key)) : new Set()
}

function packEntryEditFromScan(entry: PackLevelInfo): PackSettingsEdit['entries'][number] {
  const levelId = sanitizeUiId(entry.levelId || entry.songId || entry.directory || entry.identifier || 'level') || 'level'
  return {
    key: entry.key,
    enabled: entry.enabled !== false && entry.canConvert !== false,
    title: entry.title ?? '',
    artist: entry.artist ?? '',
    levelId,
    charts: entry.charts.map(packChartEditFromScan)
  }
}

function packChartEditFromScan(chart: PackChartInfo): PackSettingsEdit['entries'][number]['charts'][number] {
  return {
    ratingClass: chart.ratingClass,
    enabled: chart.enabled !== false && chart.canConvert !== false,
    difficulty: chart.difficulty ?? '',
    chartConstant: chart.chartConstant != null ? String(chart.chartConstant) : '',
    charter: chart.charter ?? '',
    illustrator: chart.illustrator ?? ''
  }
}

function computeStatus(scan: SingleScanResult | null, ready: boolean, message: ActionResult<unknown> | null, copy: UiCopy): { label: string; description: string; level: 'neutral' | 'good' | 'bad' } {
  if (message?.ok === false) return { label: copy.resultFailed, description: message.error ?? copy.seeLogs, level: 'bad' }
  if (message?.ok && message.data && 'outputPath' in (message.data as Record<string, unknown>)) return { label: copy.resultSaved, description: copy.statusSavedDescription, level: 'good' }
  if (!scan) return { label: copy.statusNoInput, description: copy.statusNoInputDescription, level: 'neutral' }
  if (!ready) return { label: copy.statusNeedMetadata, description: copy.statusNeedMetadataDescription, level: 'bad' }
  return { label: copy.statusReady, description: copy.statusReadyDescription, level: 'good' }
}

function firstPackSourcePath(scan: PackScanResult | null): string | undefined {
  const source = scan?.sourcePath || scan?.basePackPath
  return source?.split(';')[0]
}

function sanitizeUiId(value: string): string {
  return value.replace(/[^\w.-]+/g, '_').replace(/^[_\-.]+|[_\-.]+$/g, '')
}

function buildPackIdentifier(publisherId: string, packId: string): string {
  return `${publisherId || 'etoilebridge'}.${packId || 'pack'}.pack`
}

function basename(filePath: string): string {
  return filePath.split(/[\\/]/).pop() ?? filePath
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  const kb = bytes / 1024
  if (kb < 1024) return `${kb.toFixed(1)} KB`
  const mb = kb / 1024
  if (mb < 1024) return `${mb.toFixed(1)} MB`
  return `${(mb / 1024).toFixed(2)} GB`
}
