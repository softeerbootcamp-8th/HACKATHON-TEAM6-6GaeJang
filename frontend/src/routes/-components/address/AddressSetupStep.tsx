import { useState, useEffect, useCallback, useRef } from 'react'
import { ChevronLeft, Crosshair, Search, X, MapPin } from 'lucide-react'

import {
  searchAddressOrPlaces,
  type AddressSearchResult,
} from '@/lib/kakaoMap'
import { LocationPermissionDialog } from './LocationPermissionDialog'
import { KakaoMapPicker, type SelectedLocation } from './KakaoMapPicker'

type AddressSetupStepProps = {
  onBack: () => void
  onComplete: (location: SelectedLocation) => void
  isSubmitting?: boolean
  /** 지도 화면 확정 버튼 문구. 온보딩(가입)과 마이페이지(주소 변경)가 다른 문구를 쓴다. */
  confirmLabel?: string
  submittingLabel?: string
}

export function AddressSetupStep({
  onBack,
  onComplete,
  isSubmitting,
  confirmLabel,
  submittingLabel,
}: AddressSetupStepProps) {
  const [query, setQuery] = useState('')
  const [submittedQuery, setSubmittedQuery] = useState<string | null>(null)
  const [userCoords, setUserCoords] = useState<{ latitude: number; longitude: number } | null>(null)
  const [hasLocationPermission, setHasLocationPermission] = useState<boolean | null>(() => {
    if (typeof window !== 'undefined') {
      return localStorage.getItem('delipot_loc_pref') === 'allowed' ? true : null
    }
    return null
  })
  const [showPermissionDialog, setShowPermissionDialog] = useState(() => {
    if (typeof window !== 'undefined') {
      return localStorage.getItem('delipot_loc_pref') !== 'allowed'
    }
    return false
  })
  const [showMapPicker, setShowMapPicker] = useState(false)
  const [mapInitialCoords, setMapInitialCoords] = useState<{ latitude: number; longitude: number } | null>(null)
  const [mapInitialAddress, setMapInitialAddress] = useState<string | undefined>(undefined)

  const [searchResults, setSearchResults] = useState<AddressSearchResult[]>([])
  const [isSearching, setIsSearching] = useState(false)

  const debounceTimerRef = useRef<number | null>(null)

  // Geolocation 요청
  const requestLocation = useCallback((persist: boolean) => {
    if (typeof navigator !== 'undefined' && navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          const coords = {
            latitude: pos.coords.latitude,
            longitude: pos.coords.longitude,
          }
          setUserCoords(coords)
          setHasLocationPermission(true)
          if (persist) {
            localStorage.setItem('delipot_loc_pref', 'allowed')
          }
        },
        () => {
          setHasLocationPermission(false)
        },
        { enableHighAccuracy: true, timeout: 5000 },
      )
    } else {
      setHasLocationPermission(false)
    }
  }, [])

  // 초기 허용 상태인 경우 비동기 위치 로드
  useEffect(() => {
    if (hasLocationPermission === true && !userCoords && typeof navigator !== 'undefined' && navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          setUserCoords({
            latitude: pos.coords.latitude,
            longitude: pos.coords.longitude,
          })
        },
        () => {
          setHasLocationPermission(false)
        },
        { enableHighAccuracy: true, timeout: 5000 },
      )
    }
  }, [hasLocationPermission, userCoords])

  // 실시간 자동완성 검색 (타이핑 시)
  useEffect(() => {
    if (debounceTimerRef.current) clearTimeout(debounceTimerRef.current)

    const trimmed = query.trim()
    if (!trimmed) {
      return
    }

    debounceTimerRef.current = window.setTimeout(async () => {
      setIsSearching(true)
      try {
        const results = await searchAddressOrPlaces(trimmed, userCoords)
        // 실시간 입력 중에는 최대 10개 (State I 2)
        setSearchResults(submittedQuery ? results : results.slice(0, 10))
      } finally {
        setIsSearching(false)
      }
    }, 200)

    return () => {
      if (debounceTimerRef.current) clearTimeout(debounceTimerRef.current)
    }
  }, [query, userCoords, submittedQuery])

  // 엔터 또는 검색 버튼 클릭 시 (State I 3, I 4)
  const handleSearchSubmit = async (e?: React.FormEvent) => {
    if (e) e.preventDefault()
    const trimmed = query.trim()
    if (!trimmed) return

    setSubmittedQuery(trimmed)
    setIsSearching(true)
    try {
      const results = await searchAddressOrPlaces(trimmed, userCoords)
      setSearchResults(results) // 전체 결과 무제한 (State I 4)
    } finally {
      setIsSearching(false)
    }
  }

  // "현재 위치로 주소 찾기" 클릭 시 (State J)
  const handleOpenCurrentLocationMap = () => {
    if (hasLocationPermission && userCoords) {
      setMapInitialCoords(userCoords)
      setShowMapPicker(true)
    } else {
      // 권한이 없으면 권한 팝업 다시 띄움
      setShowPermissionDialog(true)
    }
  }

  // 검색 결과 항목 선택 시
  const handleSelectSearchResult = (item: AddressSearchResult) => {
    setMapInitialCoords({ latitude: item.latitude, longitude: item.longitude })
    setMapInitialAddress(item.roadAddress || item.jibunAddress)
    setShowMapPicker(true)
  }

  if (showMapPicker) {
    return (
      <KakaoMapPicker
        initialCoords={mapInitialCoords}
        initialAddress={mapInitialAddress}
        onBack={() => setShowMapPicker(false)}
        onConfirm={onComplete}
        isSubmitting={isSubmitting}
        confirmLabel={confirmLabel}
        submittingLabel={submittingLabel}
      />
    )
  }

  return (
    <div className="flex min-h-dvh flex-col bg-bg">
      {/* 헤더 */}
      <header className="flex h-14 items-center justify-between border-b border-border px-4">
        <button
          type="button"
          onClick={onBack}
          aria-label="뒤로가기"
          className="hover:bg-muted -ml-2 flex size-10 items-center justify-center rounded-full transition-colors"
        >
          <ChevronLeft className="size-6 text-fg" />
        </button>
        <h2 className="text-base font-bold text-fg">주소 설정</h2>
        <div className="size-8" />
      </header>

      <div className="flex flex-1 flex-col px-6 py-6">
        {/* 검색바 (State I, J, K) */}
        <form onSubmit={handleSearchSubmit} className="relative mb-4 flex items-center">
          <input
            type="text"
            placeholder="아파트명, 도로명 또는 지번"
            value={query}
            maxLength={100}
            autoFocus
            onChange={(e) => setQuery(e.target.value)}
            className="h-13 w-full rounded-xl border border-border bg-bg pr-20 pl-4 text-base text-fg outline-none transition-colors placeholder:text-muted-fg focus:border-primary"
          />
          <div className="absolute right-3 flex items-center gap-1.5">
            {query && (
              <button
                type="button"
                onClick={() => {
                  setQuery('')
                  setSubmittedQuery(null)
                  setSearchResults([])
                }}
                className="text-muted-fg hover:text-fg flex size-7 items-center justify-center rounded-full"
              >
                <X className="size-4" />
              </button>
            )}
            <button
              type="submit"
              aria-label="검색"
              className="text-muted-fg hover:text-primary flex size-8 items-center justify-center rounded-lg"
            >
              <Search className="size-5" />
            </button>
          </div>
        </form>

        {/* 현재 위치로 주소 찾기 버튼 (State J) */}
        {!submittedQuery && !query && (
          <button
            type="button"
            onClick={handleOpenCurrentLocationMap}
            className="hover:border-primary/50 mb-6 flex h-13 w-full items-center justify-center gap-2 rounded-xl border border-border bg-bg text-sm font-semibold text-fg transition-colors"
          >
            <Crosshair className="text-primary size-4" />
            현재 위치로 주소 찾기
          </button>
        )}

        {/* 1. 검색어가 없을 때: 이렇게 검색해보세요 가이드 (State K) */}
        {!query && !submittedQuery && (
          <div className="flex flex-col gap-4">
            <h3 className="text-muted-fg text-xs font-semibold">이렇게 검색해보세요</h3>
            <div className="flex flex-col gap-3 rounded-xl border border-border/60 bg-muted/20 p-4 text-xs leading-relaxed">
              <div className="flex gap-3">
                <span className="text-muted-fg font-medium shrink-0">도로명</span>
                <button
                  type="button"
                  onClick={() => setQuery('강남구 학동로 171')}
                  className="hover:text-primary text-left text-fg underline-offset-2 hover:underline"
                >
                  예) 강남구 학동로 171
                </button>
              </div>
              <div className="flex gap-3">
                <span className="text-muted-fg font-medium shrink-0">동주소</span>
                <button
                  type="button"
                  onClick={() => setQuery('논현동 58-3')}
                  className="hover:text-primary text-left text-fg underline-offset-2 hover:underline"
                >
                  예) 논현동 58-3
                </button>
              </div>
              <div className="flex gap-3">
                <span className="text-muted-fg font-medium shrink-0">아파트명</span>
                <button
                  type="button"
                  onClick={() => setQuery('논현 푸르지오')}
                  className="hover:text-primary text-left text-fg underline-offset-2 hover:underline"
                >
                  예) 논현 푸르지오
                </button>
              </div>
            </div>
          </div>
        )}

        {/* 2. 검색 결과 헤딩 (State I 3) */}
        {submittedQuery && (
          <div className="mb-4">
            <h3 className="text-sm font-semibold text-fg">
              &lsquo;{submittedQuery}&rsquo;에 대한 검색 결과입니다.
            </h3>
          </div>
        )}

        {/* 3. 검색 결과 목록 (State I 2, I 4) */}
        {query && (
          <div className="flex flex-1 flex-col overflow-y-auto">
            {searchResults.length > 0 ? (
              <ul className="divide-y divide-border/60">
                {searchResults.map((item) => (
                  <li key={item.id}>
                    <button
                      type="button"
                      onClick={() => handleSelectSearchResult(item)}
                      className="hover:bg-muted/40 flex w-full flex-col gap-1 py-3.5 text-left transition-colors"
                    >
                      <div className="flex items-center justify-between">
                        <span className="text-sm font-bold text-fg">
                          {item.placeName}
                        </span>
                        {item.distanceMeters !== undefined && (
                          <span className="text-muted-fg text-xs">
                            {item.distanceMeters >= 1000
                              ? `${(item.distanceMeters / 1000).toFixed(1)}km`
                              : `${item.distanceMeters}m`}
                          </span>
                        )}
                      </div>
                      <span className="text-muted-fg text-xs">
                        {item.roadAddress}
                      </span>
                      {item.jibunAddress && item.jibunAddress !== item.roadAddress && (
                        <span className="text-muted-fg/70 text-[11px]">
                          [지번] {item.jibunAddress}
                        </span>
                      )}
                    </button>
                  </li>
                ))}
              </ul>
            ) : !isSearching ? (
              <div className="flex flex-1 flex-col items-center justify-center py-16 text-center">
                <MapPin className="text-muted-fg/40 mb-3 size-10" />
                <p className="text-muted-fg text-sm">
                  검색 결과가 없습니다.
                  <br />
                  검색어를 다시 확인해주세요.
                </p>
              </div>
            ) : (
              <div className="flex flex-1 items-center justify-center py-10">
                <span className="text-muted-fg text-xs">주소를 찾는 중…</span>
              </div>
            )}
          </div>
        )}
      </div>

      {/* 위치 권한 요청 모달 */}
      <LocationPermissionDialog
        isOpen={showPermissionDialog}
        onAllow={(persist) => {
          setShowPermissionDialog(false)
          requestLocation(persist)
        }}
        onDeny={() => {
          setShowPermissionDialog(false)
          setHasLocationPermission(false)
        }}
      />
    </div>
  )
}
