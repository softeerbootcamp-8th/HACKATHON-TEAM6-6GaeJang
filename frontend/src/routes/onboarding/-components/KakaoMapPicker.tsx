import { useEffect, useRef, useState, useCallback } from 'react'
import { ChevronLeft, Crosshair, MapPin } from 'lucide-react'

import { Button } from '@/components/ui/button'
import {
  coordToAddress,
  loadKakaoMapSdk,
  type KakaoMapInstance,
  type KakaoCustomOverlay,
} from '@/lib/kakaoMap'

export interface SelectedLocation {
  roadAddress: string
  jibunAddress: string
  address: string
  latitude: number
  longitude: number
}

type KakaoMapPickerProps = {
  initialCoords?: { latitude: number; longitude: number } | null
  initialAddress?: string
  onBack: () => void
  onConfirm: (location: SelectedLocation) => void
  isSubmitting?: boolean
}

const DEFAULT_CENTER = {
  latitude: 37.5138,
  longitude: 127.0295,
}

export function KakaoMapPicker({
  initialCoords,
  initialAddress,
  onBack,
  onConfirm,
  isSubmitting,
}: KakaoMapPickerProps) {
  const mapContainerRef = useRef<HTMLDivElement | null>(null)
  const mapInstanceRef = useRef<KakaoMapInstance | null>(null)
  const userMarkerRef = useRef<KakaoCustomOverlay | null>(null)

  const [currentCenter, setCurrentCenter] = useState<{ latitude: number; longitude: number }>(
    initialCoords || DEFAULT_CENTER,
  )

  const [roadAddress, setRoadAddress] = useState(
    initialAddress || '서울특별시 강남구 학동로 171 (논현동)',
  )
  const [jibunAddress, setJibunAddress] = useState(
    '[지번] 서울특별시 강남구 논현동 58-3 삼익악기 빌딩',
  )
  const [isLoadingAddress, setIsLoadingAddress] = useState(false)

  // 좌표로 주소 갱신
  const updateAddressFromCoords = useCallback(async (lat: number, lng: number) => {
    setIsLoadingAddress(true)
    try {
      const result = await coordToAddress(lat, lng)
      setRoadAddress(result.roadAddress)
      setJibunAddress(`[지번] ${result.jibunAddress}`)
    } catch {
      // fallback
    } finally {
      setIsLoadingAddress(false)
    }
  }, [])

  // 유저 현재 위치 조회
  const fetchCurrentLocation = useCallback(() => {
    if (typeof navigator !== 'undefined' && navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          const lat = pos.coords.latitude
          const lng = pos.coords.longitude
          const newLoc = { latitude: lat, longitude: lng }

          if (mapInstanceRef.current && window.kakao?.maps) {
            const moveLatLon = new window.kakao.maps.LatLng(lat, lng)
            mapInstanceRef.current.setCenter(moveLatLon)
            setCurrentCenter(newLoc)
            void updateAddressFromCoords(lat, lng)

            // 유저 파란색 점 위치 업데이트
            if (userMarkerRef.current) {
              userMarkerRef.current.setPosition(moveLatLon)
            }
          }
        },
        () => {
          console.warn('Geolocation access failed or denied')
        },
        { enableHighAccuracy: true, timeout: 5000 },
      )
    }
  }, [updateAddressFromCoords])

  // 카카오맵 초기화
  useEffect(() => {
    let isCancelled = false

    async function initMap() {
      const isLoaded = await loadKakaoMapSdk()
      if (isCancelled || !mapContainerRef.current) return

      const initialLat = initialCoords?.latitude ?? DEFAULT_CENTER.latitude
      const initialLng = initialCoords?.longitude ?? DEFAULT_CENTER.longitude

      if (isLoaded && window.kakao?.maps) {
        const container = mapContainerRef.current
        const centerLatLng = new window.kakao.maps.LatLng(initialLat, initialLng)
        const options = {
          center: centerLatLng,
          level: 3, // 300m 반경이 화면에 잘 보이도록 3레벨 설정
        }

        const map = new window.kakao.maps.Map(container, options)
        mapInstanceRef.current = map

        // 유저 위치 파란 점 (Blue Dot) 커스텀 오버레이
        const blueDotContent = document.createElement('div')
        blueDotContent.className = 'relative flex items-center justify-center'
        blueDotContent.innerHTML = `
          <span class="absolute size-6 rounded-full bg-blue-500/30 animate-ping"></span>
          <span class="relative size-3.5 rounded-full border-2 border-white bg-blue-500 shadow-md"></span>
        `

        const userOverlay = new window.kakao.maps.CustomOverlay({
          position: centerLatLng,
          content: blueDotContent,
          map: map,
        })
        userMarkerRef.current = userOverlay

        // 지도 이동 / 드래그 완료 이벤트
        window.kakao.maps.event.addListener(map, 'idle', () => {
          const center = map.getCenter()
          const lat = center.getLat()
          const lng = center.getLng()
          setCurrentCenter({ latitude: lat, longitude: lng })
          void updateAddressFromCoords(lat, lng)
        })

        // 초기 주소 로드
        void updateAddressFromCoords(initialLat, initialLng)
      } else {
        // Mock 모드 fallback
        void updateAddressFromCoords(initialLat, initialLng)
      }
    }

    void initMap()

    return () => {
      isCancelled = true
    }
  }, [initialCoords, updateAddressFromCoords])

  const handleConfirm = () => {
    onConfirm({
      roadAddress,
      jibunAddress,
      address: roadAddress || jibunAddress,
      latitude: currentCenter.latitude,
      longitude: currentCenter.longitude,
    })
  }

  return (
    <div className="relative flex h-dvh w-full max-w-md flex-col bg-bg">
      {/* 헤더 */}
      <header className="relative z-20 flex h-14 items-center justify-between border-b border-border bg-bg/95 px-4 backdrop-blur-xs">
        <button
          type="button"
          onClick={onBack}
          aria-label="뒤로가기"
          className="hover:bg-muted -ml-2 flex size-10 items-center justify-center rounded-full transition-colors"
        >
          <ChevronLeft className="size-6 text-fg" />
        </button>
        <h2 className="text-base font-bold text-fg">지도에서 주소 찾기</h2>
        <div className="size-8" />
      </header>

      {/* 지도 컨테이너 */}
      <div className="relative flex-1 bg-muted">
        <div ref={mapContainerRef} className="h-full w-full" />

        {/* 화면 정중앙 고정 주황색 핀 (State N) */}
        <div className="pointer-events-none absolute top-1/2 left-1/2 z-10 -translate-x-1/2 -translate-y-full drop-shadow-lg">
          <div className="flex flex-col items-center">
            <div className="bg-primary flex size-9 items-center justify-center rounded-full shadow-md">
              <MapPin className="size-5 fill-white text-white" />
            </div>
            <div className="bg-primary -mt-1 size-2 rotate-45" />
          </div>
        </div>

        {/* 내 위치 (GPS) 이동 버튼 (State O) */}
        <button
          type="button"
          onClick={fetchCurrentLocation}
          aria-label="현재 위치로 이동"
          className="border-border bg-bg text-fg hover:bg-muted absolute right-4 bottom-4 z-10 flex size-11 items-center justify-center rounded-full border shadow-md transition-colors"
        >
          <Crosshair className="text-primary size-5" />
        </button>
      </div>

      {/* 하단 주소 정보 카드 (State P, Q) */}
      <div className="z-20 border-t border-border bg-bg px-6 pt-5 pb-8 shadow-lg">
        <div className="mb-5 flex flex-col gap-1.5">
          <span className="text-muted-fg text-xs font-semibold">
            {isLoadingAddress ? '주소 확인 중…' : '지도를 움직여 위치를 설정하세요'}
          </span>
          <h3 className="text-base font-bold text-fg leading-tight">
            {roadAddress}
          </h3>
          <p className="text-muted-fg text-xs leading-tight">
            {jibunAddress}
          </p>
        </div>

        <Button
          type="button"
          onClick={handleConfirm}
          disabled={isSubmitting}
          className="h-13 w-full rounded-xl text-base font-semibold"
        >
          {isSubmitting ? '가입 처리 중…' : '다음'}
        </Button>
      </div>
    </div>
  )
}
