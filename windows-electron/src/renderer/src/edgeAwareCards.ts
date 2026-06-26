import { useLayoutEffect } from 'react'
import type { RefObject } from 'react'

type EdgeAwarePage = 'single' | 'pack' | 'character'

type CardMetrics = {
  card: HTMLElement
  cropTop: number
  visibleHeight: number
  radius: number
}

const BASE_RADIUS_FALLBACK = 24

function resetCard(card: HTMLElement): void {
  card.style.setProperty('--edge-cut-top', '0px')
  card.style.setProperty('--edge-visible-height', '9999px')
  card.style.removeProperty('--edge-radius')
}

function readMetrics(host: HTMLElement): CardMetrics[] {
  const viewportTop = host.getBoundingClientRect().top
  const activePane = host.querySelector<HTMLElement>('.pagePane.active')
  if (!activePane) return []

  const cards = Array.from(activePane.querySelectorAll<HTMLElement>('.edgeAwareCard'))
  return cards.map((card) => {
    const rect = card.getBoundingClientRect()
    const height = Math.max(0, rect.height)
    const cropTop = Math.min(Math.max(viewportTop - rect.top, 0), height)
    const visibleHeight = Math.max(0, height - cropTop)
    const baseRadius = Number.parseFloat(window.getComputedStyle(card).getPropertyValue('--card-radius')) || BASE_RADIUS_FALLBACK
    return {
      card,
      cropTop,
      visibleHeight,
      radius: Math.min(baseRadius, visibleHeight / 2)
    }
  })
}

function writeMetrics(metrics: CardMetrics[]): void {
  for (const item of metrics) {
    item.card.style.setProperty('--edge-cut-top', `${item.cropTop.toFixed(2)}px`)
    item.card.style.setProperty('--edge-visible-height', `${item.visibleHeight.toFixed(2)}px`)
    item.card.style.setProperty('--edge-radius', `${item.radius.toFixed(2)}px`)
  }
}

export function useEdgeAwareCards(page: EdgeAwarePage, hostRef: RefObject<HTMLElement | null>): void {
  useLayoutEffect(() => {
    const host = hostRef.current
    if (!host) return

    let frame = 0
    let disposed = false
    const observedCards = new Set<HTMLElement>()

    const resetInactiveCards = (): void => {
      host
        .querySelectorAll<HTMLElement>('.pagePane:not(.active) .edgeAwareCard')
        .forEach(resetCard)
    }

    const updateNow = (): void => {
      if (disposed) return
      frame = 0
      resetInactiveCards()
      const metrics = readMetrics(host)
      writeMetrics(metrics)
    }

    const schedule = (): void => {
      if (frame !== 0 || disposed) return
      frame = window.requestAnimationFrame(updateNow)
    }

    const cardResizeObserver = new ResizeObserver(schedule)
    const hostResizeObserver = new ResizeObserver(schedule)
    hostResizeObserver.observe(host)

    const observeActiveCards = (): void => {
      const activePane = host.querySelector<HTMLElement>('.pagePane.active')
      const cards = activePane
        ? Array.from(activePane.querySelectorAll<HTMLElement>('.edgeAwareCard'))
        : []
      for (const card of cards) {
        if (observedCards.has(card)) continue
        observedCards.add(card)
        cardResizeObserver.observe(card)
      }
    }

    const mutationObserver = new MutationObserver(() => {
      observeActiveCards()
      schedule()
    })
    mutationObserver.observe(host, {
      attributes: true,
      attributeFilter: ['class', 'open'],
      childList: true,
      subtree: true
    })

    const onResourceLoaded = (event: Event): void => {
      if (event.target instanceof HTMLImageElement) schedule()
    }

    host.addEventListener('scroll', schedule, { passive: true })
    host.addEventListener('load', onResourceLoaded, true)
    host.addEventListener('error', onResourceLoaded, true)
    window.addEventListener('resize', schedule)

    observeActiveCards()
    updateNow()
    schedule()

    if (document.fonts) {
      document.fonts.ready.then(schedule).catch(() => undefined)
    }

    return () => {
      disposed = true
      if (frame !== 0) window.cancelAnimationFrame(frame)
      host.removeEventListener('scroll', schedule)
      host.removeEventListener('load', onResourceLoaded, true)
      host.removeEventListener('error', onResourceLoaded, true)
      window.removeEventListener('resize', schedule)
      cardResizeObserver.disconnect()
      hostResizeObserver.disconnect()
      mutationObserver.disconnect()
      host.querySelectorAll<HTMLElement>('.edgeAwareCard').forEach(resetCard)
      observedCards.clear()
    }
  }, [page, hostRef])
}
