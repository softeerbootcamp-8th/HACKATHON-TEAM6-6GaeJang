import { useCallback, useEffect, useRef, useState } from 'react'
import { RefreshCw } from 'lucide-react'

const PULL_THRESHOLD = 72
const MAX_PULL_DISTANCE = 96
const REFRESH_HOLD_DISTANCE = 52
const MIN_REFRESH_DURATION_MS = 500

type PullToRefreshOptions = {
  onRefresh: () => Promise<unknown> | unknown
}

export function usePullToRefresh({ onRefresh }: PullToRefreshOptions) {
  const [scrollElement, setScrollElement] = useState<HTMLDivElement | null>(null)
  const onRefreshRef = useRef(onRefresh)
  const startYRef = useRef<number | null>(null)
  const pullDistanceRef = useRef(0)
  const refreshingRef = useRef(false)
  const [pullDistance, setPullDistance] = useState(0)
  const [isRefreshing, setIsRefreshing] = useState(false)
  const scrollRef = useCallback((node: HTMLDivElement | null) => setScrollElement(node), [])

  useEffect(() => {
    onRefreshRef.current = onRefresh
  }, [onRefresh])

  const updatePullDistance = useCallback((distance: number) => {
    pullDistanceRef.current = distance
    setPullDistance(distance)
  }, [])

  const refresh = useCallback(async () => {
    if (refreshingRef.current) return

    refreshingRef.current = true
    setIsRefreshing(true)
    updatePullDistance(REFRESH_HOLD_DISTANCE)

    try {
      const refreshPromise = Promise.resolve().then(() => onRefreshRef.current())
      await Promise.allSettled([
        refreshPromise,
        new Promise((resolve) => window.setTimeout(resolve, MIN_REFRESH_DURATION_MS)),
      ])
    } finally {
      refreshingRef.current = false
      setIsRefreshing(false)
      updatePullDistance(0)
    }
  }, [updatePullDistance])

  useEffect(() => {
    if (!scrollElement) return

    const handleTouchStart = (event: TouchEvent) => {
      if (refreshingRef.current || event.touches.length !== 1 || scrollElement.scrollTop > 0) {
        startYRef.current = null
        return
      }

      startYRef.current = event.touches[0].clientY
    }

    const handleTouchMove = (event: TouchEvent) => {
      if (startYRef.current == null || event.touches.length !== 1) return

      if (scrollElement.scrollTop > 0) {
        startYRef.current = null
        updatePullDistance(0)
        return
      }

      const delta = event.touches[0].clientY - startYRef.current
      if (delta <= 0) {
        updatePullDistance(0)
        return
      }

      event.preventDefault()
      updatePullDistance(Math.min(MAX_PULL_DISTANCE, delta * 0.5))
    }

    const handleTouchEnd = () => {
      if (startYRef.current == null) return

      startYRef.current = null
      if (pullDistanceRef.current >= PULL_THRESHOLD) void refresh()
      else updatePullDistance(0)
    }

    const handleTouchCancel = () => {
      startYRef.current = null
      if (!refreshingRef.current) updatePullDistance(0)
    }

    scrollElement.addEventListener('touchstart', handleTouchStart, { passive: true })
    scrollElement.addEventListener('touchmove', handleTouchMove, { passive: false })
    scrollElement.addEventListener('touchend', handleTouchEnd)
    scrollElement.addEventListener('touchcancel', handleTouchCancel)

    return () => {
      scrollElement.removeEventListener('touchstart', handleTouchStart)
      scrollElement.removeEventListener('touchmove', handleTouchMove)
      scrollElement.removeEventListener('touchend', handleTouchEnd)
      scrollElement.removeEventListener('touchcancel', handleTouchCancel)
    }
  }, [refresh, scrollElement, updatePullDistance])

  return { scrollRef, pullDistance, isRefreshing }
}

type PullToRefreshIndicatorProps = {
  pullDistance: number
  isRefreshing: boolean
}

export function PullToRefreshIndicator({
  pullDistance,
  isRefreshing,
}: PullToRefreshIndicatorProps) {
  const progress = Math.min(1, pullDistance / PULL_THRESHOLD)

  return (
    <div className="pointer-events-none absolute inset-x-0 top-0 z-40 flex justify-center">
      <div
        className="bg-bg flex size-9 items-center justify-center rounded-full border shadow-sm transition-[transform,opacity] duration-150"
        style={{
          opacity: progress,
          transform: `translateY(${pullDistance - 40}px)`,
        }}
      >
        <RefreshCw
          className={`text-primary size-4 ${isRefreshing ? 'animate-spin' : ''}`}
          style={isRefreshing ? undefined : { transform: `rotate(${progress * 240}deg)` }}
        />
      </div>
      <span className="sr-only" aria-live="polite">
        {isRefreshing ? '새로고침 중' : ''}
      </span>
    </div>
  )
}
