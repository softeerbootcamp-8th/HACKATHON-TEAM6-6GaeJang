import { useCallback, useEffect, useRef, useState } from 'react'

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
  const expandedHeight = isRefreshing ? 44 : Math.min(56, pullDistance * 0.65)
  const arcLength = 18 + progress * 58
  const transitionDuration = expandedHeight === 0 ? 240 : isRefreshing ? 180 : 50

  return (
    <div
      className="pointer-events-none overflow-hidden transition-[height] ease-out"
      style={{ height: expandedHeight, transitionDuration: `${transitionDuration}ms` }}
    >
      <div className="flex h-full items-center justify-center">
        <svg
          viewBox="0 0 24 24"
          aria-hidden="true"
          className={`size-6 ${isRefreshing ? 'animate-[spin_0.7s_linear_infinite]' : ''}`}
          style={
            isRefreshing
              ? undefined
              : { opacity: progress, transform: `rotate(${progress * 220 - 90}deg)` }
          }
        >
          <circle
            cx="12"
            cy="12"
            r="9"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.5"
            className="text-primary/15"
          />
          <circle
            cx="12"
            cy="12"
            r="9"
            pathLength="100"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.5"
            strokeLinecap="round"
            strokeDasharray={`${arcLength} 100`}
            className="text-primary"
          />
        </svg>
      </div>
      <span className="sr-only" aria-live="polite">
        {isRefreshing ? '새로고침 중' : ''}
      </span>
    </div>
  )
}
