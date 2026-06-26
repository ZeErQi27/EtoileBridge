import type { EtoileBridgeApi } from '../../preload'

declare global {
  interface Window {
    etoileBridge: EtoileBridgeApi
  }
}

export {}
