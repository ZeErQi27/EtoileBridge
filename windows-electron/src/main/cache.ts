import { app } from 'electron'
import fs from 'node:fs/promises'
import path from 'node:path'
import crypto from 'node:crypto'

export type CleanupResult = {
  deleted: number
  failed: string[]
}

export class ElectronCacheManager {
  readonly cacheRoot: string
  private activeSessions = new Set<string>()
  lastCleanup = '尚未清理'

  constructor() {
    this.cacheRoot = path.join(app.getPath('appData'), 'EtoileBridgeElectron', 'cache')
  }

  async ensureRoot(): Promise<void> {
    await fs.mkdir(this.cacheRoot, { recursive: true })
  }

  async createSession(): Promise<string> {
    await this.ensureRoot()
    const session = path.join(this.cacheRoot, `session-${Date.now()}-${crypto.randomUUID()}`)
    await fs.mkdir(session, { recursive: true })
    this.activeSessions.add(session)
    return session
  }

  async cleanupSession(session: string): Promise<CleanupResult> {
    const safe = this.safeSessionPath(session)
    if (!safe) return { deleted: 0, failed: [session] }
    try {
      await fs.rm(safe, { recursive: true, force: true })
      this.activeSessions.delete(safe)
      this.lastCleanup = `已清理 1 个 session`
      return { deleted: 1, failed: [] }
    } catch {
      return { deleted: 0, failed: [safe] }
    }
  }

  async cleanupActiveSessions(): Promise<CleanupResult> {
    let deleted = 0
    const failed: string[] = []
    for (const session of Array.from(this.activeSessions)) {
      const result = await this.cleanupSession(session)
      deleted += result.deleted
      failed.push(...result.failed)
    }
    this.lastCleanup = failed.length === 0 ? `关闭清理完成：${deleted} 个 session` : `关闭清理部分失败：${failed.length}`
    return { deleted, failed }
  }

  async cleanupStale(maxAgeHours = 24): Promise<CleanupResult> {
    await this.ensureRoot()
    const now = Date.now()
    const maxAgeMs = maxAgeHours * 60 * 60 * 1000
    let deleted = 0
    const failed: string[] = []
    const entries = await fs.readdir(this.cacheRoot, { withFileTypes: true }).catch(() => [])
    for (const entry of entries) {
      if (!entry.isDirectory() || !entry.name.startsWith('session-')) continue
      const full = path.join(this.cacheRoot, entry.name)
      const stat = await fs.stat(full).catch(() => null)
      if (!stat || now - stat.mtimeMs < maxAgeMs) continue
      const result = await this.cleanupSession(full)
      deleted += result.deleted
      failed.push(...result.failed)
    }
    this.lastCleanup = failed.length === 0 ? `启动清理完成：${deleted} 个旧 session` : `启动清理部分失败：${failed.length}`
    return { deleted, failed }
  }

  async cleanupInactiveSessions(maxAgeHours = 0): Promise<CleanupResult> {
    await this.ensureRoot()
    const now = Date.now()
    const maxAgeMs = maxAgeHours * 60 * 60 * 1000
    let deleted = 0
    const failed: string[] = []
    const entries = await fs.readdir(this.cacheRoot, { withFileTypes: true }).catch(() => [])
    for (const entry of entries) {
      if (!entry.isDirectory() || !entry.name.startsWith('session-')) continue
      const full = path.join(this.cacheRoot, entry.name)
      const safe = this.safeSessionPath(full)
      if (!safe || this.activeSessions.has(safe)) continue
      const stat = await fs.stat(safe).catch(() => null)
      if (!stat || now - stat.mtimeMs < maxAgeMs) continue
      try {
        await fs.rm(safe, { recursive: true, force: true })
        deleted += 1
      } catch {
        failed.push(safe)
      }
    }
    this.lastCleanup = failed.length === 0 ? `清理完成：${deleted} 个非活动 session` : `清理部分失败：${failed.length}`
    return { deleted, failed }
  }

  async sizeBytes(): Promise<number> {
    await this.ensureRoot()
    return dirSize(this.cacheRoot)
  }

  private safeSessionPath(candidate: string): string | null {
    const normalized = path.resolve(candidate)
    const root = path.resolve(this.cacheRoot)
    if (!normalized.startsWith(root + path.sep)) return null
    if (!path.basename(normalized).startsWith('session-')) return null
    return normalized
  }
}

async function dirSize(dir: string): Promise<number> {
  let total = 0
  const entries = await fs.readdir(dir, { withFileTypes: true }).catch(() => [])
  for (const entry of entries) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) total += await dirSize(full)
    else if (entry.isFile()) total += (await fs.stat(full)).size
  }
  return total
}
