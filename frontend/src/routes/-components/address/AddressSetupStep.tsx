import { useState, useEffect, useCallback, useRef, type ReactNode } from 'react'
import { ChevronLeft, Crosshair, Search, X, MapPin } from 'lucide-react'

import {
  searchAddressOrPlaces,
  type AddressSearchResult,
} from '@/lib/kakaoMap'
import { KakaoMapPicker, type SelectedLocation } from './KakaoMapPicker'

/** 검색 결과 텍스트에서 검색어와 일치하는 부분(대소문자 무관, 전체 등장)을 하이라이트한다. */
function highlightMatch(text: string, query: string) {
  const trimmed = query.trim()
  if (!trimmed) return text

  const lowerText = text.toLowerCase()
  const lowerQuery = trimmed.toLowerCase()

  const parts: ReactNode[] = []
  let lastIndex = 0
  let index = lowerText.indexOf(lowerQuery)
  if (index === -1) return text

  while (index !== -1) {
    if (index > lastIndex) parts.push(text.slice(lastIndex, index))
    parts.push(
      <mark key={index} className="bg-transparent font-bold text-primary">
        {text.slice(index, index + trimmed.length)}
      </mark>,
    )
    lastIndex = index + trimmed.length
    index = lowerText.indexOf(lowerQuery, lastIndex)
  }
  if (lastIndex < text.length) parts.push(text.slice(lastIndex))

  return parts
}

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
  const [showMapPicker, setShowMapPicker] = useState(false)
  const [mapInitialCoords, setMapInitialCoords] = useState<{ latitude: number; longitude: number } | null>(null)
  const [mapInitialAddress, setMapInitialAddress] = useState<string | undefined>(undefined)

  const [searchResults, setSearchResults] = useState<AddressSearchResult[]>([])
  const [isSearching, setIsSearching] = useState(false)

  const debounceTimerRef = useRef<number | null>(null)
  const searchInputRef = useRef<HTMLInputElement>(null)

  // 지도 화면도 URL이 안 바뀌어 브라우저 히스토리에 항목이 안 쌓인다. 그대로 두면 지도 화면에서
  // 뒤로가기를 눌렀을 때 검색 화면이 아니라 이 컴포넌트를 띄운 화면(회원가입 등)으로 튕긴다.
  // pushState로 항목을 하나 쌓고, popstate에서 history.state를 보고 지도/검색 중 어느 쪽을
  // 보여줄지 판단한다 — 이 컴포넌트를 연 부모도 자기 몫의 항목을 쌓아두므로, 무조건 닫는 대신
  // "이 항목에 지도 마커가 있는가"만 확인해야 부모 단계까지 같이 닫히는 걸 막을 수 있다.
  const openMapPicker = () => {
    window.history.pushState({ addressMapPicker: true }, '')
    setShowMapPicker(true)
  }

  useEffect(() => {
    const handlePopState = (event: PopStateEvent) => {
      setShowMapPicker(event.state?.addressMapPicker === true)
    }
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  // Geolocation 요청. 브라우저/OS 네이티브 권한 팝업이 여기서 뜬다 — 앱 자체 확인 모달은
  // 두지 않는다(중복 팝업 방지).
  const requestLocation = useCallback((onGranted?: (coords: { latitude: number; longitude: number }) => void) => {
    if (typeof navigator !== 'undefined' && navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          const coords = {
            latitude: pos.coords.latitude,
            longitude: pos.coords.longitude,
          }
          setUserCoords(coords)
          setHasLocationPermission(true)
          localStorage.setItem('delipot_loc_pref', 'allowed')
          onGranted?.(coords)
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

  // 이전에 허용한 적이 있으면(로컬스토리지 기록) 조용히 위치를 다시 가져온다.
  // 브라우저가 이미 이 출처에 권한을 기억하고 있어 네이티브 팝업도 다시 뜨지 않는다.
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
    searchInputRef.current?.blur()
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
      openMapPicker()
    } else {
      requestLocation((coords) => {
        setMapInitialCoords(coords)
        openMapPicker()
      })
    }
  }

  // 검색 결과 항목 선택 시
  const handleSelectSearchResult = (item: AddressSearchResult) => {
    setMapInitialCoords({ latitude: item.latitude, longitude: item.longitude })
    setMapInitialAddress(item.roadAddress || item.jibunAddress)
    openMapPicker()
  }

  if (showMapPicker) {
    return (
      <KakaoMapPicker
        initialCoords={mapInitialCoords}
        initialAddress={mapInitialAddress}
        onBack={() => window.history.back()}
        onConfirm={onComplete}
        isSubmitting={isSubmitting}
        confirmLabel={confirmLabel}
        submittingLabel={submittingLabel}
      />
    )
  }

  return (
    // 앱 전체가 393px 모바일 셸이다. 부모 화면이 감싸주길 기대하지 않고 스스로 폭을 잡는다 —
    // 홈·팟 만들기는 이 화면을 자기 <main> 셸 바깥에서 전체화면으로 띄우기 때문이다.
    <div className="bg-bg mx-auto flex min-h-dvh max-w-[393px] flex-col shadow-xl">
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

      {/* 검색바 (State I, J, K) */}
      <div className="px-6 pt-6 pb-4">
        <form onSubmit={handleSearchSubmit} className="relative flex items-center">
          <input
            ref={searchInputRef}
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
      </div>

      <div className="bg-surface h-2" />

      <div className="flex flex-1 flex-col px-6 pt-4 pb-6">
        {/* 현재 위치로 주소 찾기 버튼 (State J) */}
        {!submittedQuery && !query && (
          <button
            type="button"
            onClick={handleOpenCurrentLocationMap}
            className="mb-6 flex h-13 w-full items-center justify-center gap-2 rounded-xl bg-bg text-sm font-semibold text-fg transition-colors"
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
              <span className="text-primary font-bold">&lsquo;{submittedQuery}&rsquo;</span>에 대한 검색 결과입니다.
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
                          {highlightMatch(item.placeName, query)}
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
                        {highlightMatch(item.roadAddress, query)}
                      </span>
                      {item.jibunAddress && item.jibunAddress !== item.roadAddress && (
                        <span className="text-muted-fg/70 text-[11px]">
                          [지번] {highlightMatch(item.jibunAddress, query)}
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
    </div>
  )
}
