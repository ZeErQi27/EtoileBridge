import { contextBridge, ipcRenderer } from 'electron'
import type {
  ActionResult,
  AboutInfo,
  AppSettingsInfo,
  CacheInfo,
  CharacterConvertResult,
  CharacterIconResult,
  CharacterScanResult,
  CharacterCropEdit,
  ConvertResult,
  PackConvertResult,
  PackScanResult,
  SavePackRequest,
  SaveCharacterRequest,
  SaveSingleRequest,
  SelectedResource,
  SingleScanResult,
  UiLanguage,
  UpdateInfo
} from '../main/types'

const api = {
  cacheInfo: (): Promise<CacheInfo> => ipcRenderer.invoke('cache:info'),
  cleanupCache: (): Promise<CacheInfo> => ipcRenderer.invoke('cache:cleanup'),
  getSettings: (): Promise<AppSettingsInfo> => ipcRenderer.invoke('settings:get'),
  setLanguage: (language: UiLanguage): Promise<AppSettingsInfo> => ipcRenderer.invoke('settings:setLanguage', language),
  getSettingsCacheInfo: (): Promise<CacheInfo> => ipcRenderer.invoke('settings:getCacheInfo'),
  clearCacheSafe: (): Promise<CacheInfo> => ipcRenderer.invoke('settings:clearCache'),
  getAboutInfo: (): Promise<AboutInfo> => ipcRenderer.invoke('settings:getAboutInfo'),
  checkUpdates: (): Promise<UpdateInfo> => ipcRenderer.invoke('settings:checkUpdates'),
  chooseZipAndScan: (): Promise<ActionResult<SingleScanResult>> => ipcRenderer.invoke('single:chooseZipAndScan'),
  chooseFolderAndScan: (): Promise<ActionResult<SingleScanResult>> => ipcRenderer.invoke('single:chooseFolderAndScan'),
  rescanSingle: (sourcePath: string): Promise<ActionResult<SingleScanResult>> => ipcRenderer.invoke('single:rescan', sourcePath),
  openInputLocation: (sourcePath: string): Promise<ActionResult<boolean>> => ipcRenderer.invoke('path:openInputLocation', sourcePath),
  chooseResource: (kind: keyof SaveSingleRequest['resources']): Promise<ActionResult<SelectedResource>> => ipcRenderer.invoke('resource:choose', kind),
  saveSingle: (request: SaveSingleRequest): Promise<ActionResult<ConvertResult>> => ipcRenderer.invoke('single:save', request),
  chooseOfficialPackZipAndScan: (): Promise<ActionResult<PackScanResult>> => ipcRenderer.invoke('pack:chooseOfficialZipAndScan'),
  chooseOfficialPackFolderAndScan: (): Promise<ActionResult<PackScanResult>> => ipcRenderer.invoke('pack:chooseOfficialFolderAndScan'),
  chooseArcpkgFilesAndScan: (): Promise<ActionResult<PackScanResult>> => ipcRenderer.invoke('pack:chooseArcpkgFilesAndScan'),
  chooseArcpkgFolderAndScan: (): Promise<ActionResult<PackScanResult>> => ipcRenderer.invoke('pack:chooseArcpkgFolderAndScan'),
  chooseExistingPackAndScan: (): Promise<ActionResult<PackScanResult>> => ipcRenderer.invoke('pack:chooseExistingBaseAndScan'),
  chooseExistingPackAddFilesAndScan: (basePackPath: string): Promise<ActionResult<PackScanResult>> => ipcRenderer.invoke('pack:chooseExistingAddFilesAndScan', basePackPath),
  chooseExistingPackAddFolderAndScan: (basePackPath: string): Promise<ActionResult<PackScanResult>> => ipcRenderer.invoke('pack:chooseExistingAddFolderAndScan', basePackPath),
  choosePackCover: (): Promise<ActionResult<SelectedResource>> => ipcRenderer.invoke('pack:chooseCover'),
  savePack: (request: SavePackRequest): Promise<ActionResult<PackConvertResult>> => ipcRenderer.invoke('pack:save', request),
  chooseCharacterImageAndScan: (): Promise<ActionResult<CharacterScanResult>> => ipcRenderer.invoke('character:chooseImageAndScan'),
  chooseCharacterArcpkgAndScan: (): Promise<ActionResult<CharacterScanResult>> => ipcRenderer.invoke('character:chooseArcpkgAndScan'),
  chooseCharacterImage: (): Promise<ActionResult<SelectedResource>> => ipcRenderer.invoke('character:chooseImage'),
  generateCharacterIcon: (scan: CharacterScanResult, crop: CharacterCropEdit): Promise<ActionResult<CharacterIconResult>> => ipcRenderer.invoke('character:generateIcon', scan, crop),
  saveCharacter: (request: SaveCharacterRequest): Promise<ActionResult<CharacterConvertResult>> => ipcRenderer.invoke('character:save', request)
}

contextBridge.exposeInMainWorld('etoileBridge', api)

export type EtoileBridgeApi = typeof api
