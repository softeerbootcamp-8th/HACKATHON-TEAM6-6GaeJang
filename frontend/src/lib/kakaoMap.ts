// Kakao Maps SDK & Geocoding / Place Search Helper

declare global {
  interface Window {
    kakao?: {
      maps: {
        load: (callback: () => void) => void
        Map: new (container: HTMLElement, options: KakaoMapOptions) => KakaoMapInstance
        LatLng: new (lat: number, lng: number) => KakaoLatLng
        CustomOverlay: new (options: KakaoCustomOverlayOptions) => KakaoCustomOverlay
        Circle: new (options: KakaoCircleOptions) => KakaoCircle
        event: {
          addListener: (target: unknown, type: string, handler: (...args: unknown[]) => void) => void
          removeListener: (target: unknown, type: string, handler: (...args: unknown[]) => void) => void
        }
        services: {
          Status: {
            OK: string
            ZERO_RESULT: string
            ERROR: string
          }
          Geocoder: new () => KakaoGeocoder
          Places: new () => KakaoPlaces
        }
      }
    }
  }
}

export interface KakaoLatLng {
  getLat: () => number
  getLng: () => number
}

export interface KakaoMapOptions {
  center: KakaoLatLng
  level: number
}

export interface KakaoMapInstance {
  setCenter: (latlng: KakaoLatLng) => void
  getCenter: () => KakaoLatLng
  setLevel: (level: number) => void
  getLevel: () => number
  relayout: () => void
}

export interface KakaoCustomOverlayOptions {
  position: KakaoLatLng
  content: HTMLElement | string
  yAnchor?: number
  xAnchor?: number
  map?: KakaoMapInstance
}

export interface KakaoCustomOverlay {
  setMap: (map: KakaoMapInstance | null) => void
  setPosition: (position: KakaoLatLng) => void
}

export interface KakaoCircleOptions {
  center: KakaoLatLng
  radius: number
  strokeWeight?: number
  strokeColor?: string
  strokeOpacity?: number
  strokeStyle?: string
  fillColor?: string
  fillOpacity?: number
  map?: KakaoMapInstance
}

export interface KakaoCircle {
  setMap: (map: KakaoMapInstance | null) => void
}

export interface KakaoGeocoder {
  coord2Address: (
    lng: number,
    lat: number,
    callback: (result: KakaoCoord2AddressResult[], status: string) => void,
  ) => void
  addressSearch: (
    address: string,
    callback: (result: KakaoAddressSearchResult[], status: string) => void,
  ) => void
}

export interface KakaoPlaces {
  keywordSearch: (
    keyword: string,
    callback: (result: KakaoPlaceItem[], status: string, pagination?: unknown) => void,
    options?: { x?: number; y?: number; radius?: number; location?: KakaoLatLng },
  ) => void
}

export interface KakaoCoord2AddressResult {
  road_address?: {
    address_name: string
    region_1depth_name: string
    region_2depth_name: string
    region_3depth_name: string
    road_name: string
    building_name: string
    zone_no: string
  }
  address?: {
    address_name: string
    region_1depth_name: string
    region_2depth_name: string
    region_3depth_name: string
    mountain_yn: string
    main_address_no: string
    sub_address_no: string
    zip_code: string
  }
}

export interface KakaoAddressSearchResult {
  address_name: string
  road_address?: {
    address_name: string
    building_name: string
  }
  address?: {
    address_name: string
  }
  x: string // lng
  y: string // lat
}

export interface KakaoPlaceItem {
  id: string
  place_name: string
  road_address_name: string
  address_name: string
  x: string // lng
  y: string // lat
  distance?: string
}

export interface AddressSearchResult {
  id: string
  placeName: string
  roadAddress: string
  jibunAddress: string
  fullAddress: string
  latitude: number
  longitude: number
  distanceMeters?: number
}

// Fallback mock data when Kakao API key is missing or offline
const MOCK_PLACES: AddressSearchResult[] = [
  {
    id: 'mock-1',
    placeName: '삼익악기 빌딩',
    roadAddress: '서울특별시 강남구 학동로 171',
    jibunAddress: '서울특별시 강남구 논현동 58-3',
    fullAddress: '서울특별시 강남구 학동로 171 (논현동, 삼익악기 빌딩)',
    latitude: 37.5138,
    longitude: 127.0295,
  },
  {
    id: 'mock-2',
    placeName: '논현 푸르지오',
    roadAddress: '서울특별시 강남구 학동로 171',
    jibunAddress: '서울특별시 강남구 논현동 58-3',
    fullAddress: '서울특별시 강남구 학동로 171 (논현동, 논현 푸르지오)',
    latitude: 37.5142,
    longitude: 127.0301,
  },
  {
    id: 'mock-3',
    placeName: '논현동 우체국',
    roadAddress: '서울특별시 강남구 학동로 150',
    jibunAddress: '서울특별시 강남구 논현동 45-1',
    fullAddress: '서울특별시 강남구 학동로 150 (논현동)',
    latitude: 37.5129,
    longitude: 127.0284,
  },
  {
    id: 'mock-4',
    placeName: '학동역 7호선',
    roadAddress: '서울특별시 강남구 학동로 180',
    jibunAddress: '서울특별시 강남구 논현동 89-1',
    fullAddress: '서울특별시 강남구 학동로 180 (논현동, 학동역)',
    latitude: 37.5143,
    longitude: 127.0317,
  },
  {
    id: 'mock-5',
    placeName: '논현 아크로힐스',
    roadAddress: '서울특별시 강남구 봉은사로 331',
    jibunAddress: '서울특별시 강남구 논현동 278',
    fullAddress: '서울특별시 강남구 봉은사로 331 (논현동, 아크로힐스논현)',
    latitude: 37.5105,
    longitude: 127.0392,
  },
]

let sdkLoadPromise: Promise<boolean> | null = null

export function getKakaoApiKey(): string {
  return (
    import.meta.env.VITE_KAKAO_MAP_API_KEY ||
    import.meta.env.VITE_KAKAO_JAVASCRIPT_KEY ||
    ''
  )
}

/** 카카오맵 SDK 동적 로드 */
export function loadKakaoMapSdk(): Promise<boolean> {
  if (typeof window === 'undefined') return Promise.resolve(false)

  if (window.kakao?.maps?.services) {
    return Promise.resolve(true)
  }

  if (sdkLoadPromise) return sdkLoadPromise

  const appKey = getKakaoApiKey()
  if (!appKey) {
    // API 키가 없으면 목업 모드로 작동
    return Promise.resolve(false)
  }

  sdkLoadPromise = new Promise((resolve) => {
    const existing = document.getElementById('kakao-map-sdk')
    if (existing) {
      if (window.kakao?.maps) {
        window.kakao.maps.load(() => resolve(true))
      } else {
        existing.addEventListener('load', () => {
          window.kakao?.maps.load(() => resolve(true))
        })
      }
      return
    }

    const script = document.createElement('script')
    script.id = 'kakao-map-sdk'
    script.src = `//dapi.kakao.com/v2/maps/sdk.js?appkey=${appKey}&libraries=services,clusterer&autoload=false`
    script.async = true

    script.onload = () => {
      if (window.kakao?.maps) {
        window.kakao.maps.load(() => resolve(true))
      } else {
        resolve(false)
      }
    }

    script.onerror = () => {
      console.warn('Failed to load Kakao Maps SDK, falling back to mock mode')
      resolve(false)
    }

    document.head.appendChild(script)
  })

  return sdkLoadPromise
}

/** 두 위경도 좌표 사이의 거리(미터) - 하버사인 공식 */
export function calculateDistanceMeters(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number,
): number {
  const R = 6371008.8 // 지구 반지름(m)
  const dLat = ((lat2 - lat1) * Math.PI) / 180
  const dLon = ((lon2 - lon1) * Math.PI) / 180
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2)
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
  return Math.round(R * c)
}

/** 키워드 또는 주소 검색 (거리순 또는 가나다순 정렬) */
export async function searchAddressOrPlaces(
  query: string,
  userCoords: { latitude: number; longitude: number } | null,
): Promise<AddressSearchResult[]> {
  const trimmed = query.trim()
  if (!trimmed) return []

  const isLoaded = await loadKakaoMapSdk()

  if (!isLoaded || !window.kakao?.maps?.services) {
    // Mock 모드 검색
    const lower = trimmed.toLowerCase()
    let filtered = MOCK_PLACES.filter(
      (p) =>
        p.placeName.toLowerCase().includes(lower) ||
        p.roadAddress.toLowerCase().includes(lower) ||
        p.jibunAddress.toLowerCase().includes(lower),
    )

    if (filtered.length === 0) {
      filtered = [
        {
          id: 'custom-query',
          placeName: trimmed,
          roadAddress: `서울특별시 강남구 ${trimmed} 171`,
          jibunAddress: `서울특별시 강남구 논현동 58-3`,
          fullAddress: `서울특별시 강남구 ${trimmed} 171 (논현동)`,
          latitude: userCoords?.latitude ?? 37.5138,
          longitude: userCoords?.longitude ?? 127.0295,
        },
      ]
    }

    const withDist = filtered.map((p) => {
      const dist = userCoords
        ? calculateDistanceMeters(
            userCoords.latitude,
            userCoords.longitude,
            p.latitude,
            p.longitude,
          )
        : undefined
      return { ...p, distanceMeters: dist }
    })

    if (userCoords) {
      withDist.sort((a, b) => (a.distanceMeters ?? 0) - (b.distanceMeters ?? 0))
    } else {
      withDist.sort((a, b) => a.placeName.localeCompare(b.placeName, 'ko'))
    }

    return withDist
  }

  // Kakao Places & Geocoder 검색
  return new Promise((resolve) => {
    const places = new window.kakao!.maps.services.Places()
    const options = userCoords
      ? {
          x: userCoords.longitude,
          y: userCoords.latitude,
        }
      : undefined

    places.keywordSearch(
      trimmed,
      (result, status) => {
        if (status === window.kakao!.maps.services.Status.OK && result.length > 0) {
          const items: AddressSearchResult[] = result.map((item) => {
            const lat = parseFloat(item.y)
            const lng = parseFloat(item.x)
            const dist = userCoords
              ? calculateDistanceMeters(
                  userCoords.latitude,
                  userCoords.longitude,
                  lat,
                  lng,
                )
              : undefined

            return {
              id: item.id,
              placeName: item.place_name,
              roadAddress: item.road_address_name || item.address_name,
              jibunAddress: item.address_name,
              fullAddress: `${item.road_address_name || item.address_name} (${item.place_name})`,
              latitude: lat,
              longitude: lng,
              distanceMeters: dist,
            }
          })

          if (userCoords) {
            items.sort((a, b) => (a.distanceMeters ?? 0) - (b.distanceMeters ?? 0))
          } else {
            items.sort((a, b) => a.placeName.localeCompare(b.placeName, 'ko'))
          }

          resolve(items)
        } else {
          // AddressSearch 시도
          const geocoder = new window.kakao!.maps.services.Geocoder()
          geocoder.addressSearch(trimmed, (addrResult, addrStatus) => {
            if (
              addrStatus === window.kakao!.maps.services.Status.OK &&
              addrResult.length > 0
            ) {
              const items: AddressSearchResult[] = addrResult.map((item, idx) => {
                const lat = parseFloat(item.y)
                const lng = parseFloat(item.x)
                const road = item.road_address?.address_name || item.address_name
                const jibun = item.address?.address_name || item.address_name
                const place = item.road_address?.building_name || trimmed
                const dist = userCoords
                  ? calculateDistanceMeters(
                      userCoords.latitude,
                      userCoords.longitude,
                      lat,
                      lng,
                    )
                  : undefined

                return {
                  id: `addr-${idx}`,
                  placeName: place || road,
                  roadAddress: road,
                  jibunAddress: jibun,
                  fullAddress: `${road} (${jibun})`,
                  latitude: lat,
                  longitude: lng,
                  distanceMeters: dist,
                }
              })

              if (userCoords) {
                items.sort((a, b) => (a.distanceMeters ?? 0) - (b.distanceMeters ?? 0))
              } else {
                items.sort((a, b) => a.placeName.localeCompare(b.placeName, 'ko'))
              }

              resolve(items)
            } else {
              resolve([])
            }
          })
        }
      },
      options,
    )
  })
}

/** 좌표 -> 주소 변환 (역지오코딩) */
export async function coordToAddress(
  lat: number,
  lng: number,
): Promise<{
  roadAddress: string
  jibunAddress: string
  regionName: string
  fullAddress: string
}> {
  const isLoaded = await loadKakaoMapSdk()

  if (!isLoaded || !window.kakao?.maps?.services) {
    return {
      roadAddress: '서울특별시 강남구 학동로 171 (논현동)',
      jibunAddress: '서울특별시 강남구 논현동 58-3 삼익악기 빌딩',
      regionName: '논현동',
      fullAddress: '서울특별시 강남구 학동로 171 (논현동)',
    }
  }

  return new Promise((resolve) => {
    const geocoder = new window.kakao!.maps.services.Geocoder()
    geocoder.coord2Address(lng, lat, (result, status) => {
      if (status === window.kakao!.maps.services.Status.OK && result.length > 0) {
        const item = result[0]
        const road = item.road_address
          ? `${item.road_address.address_name}${
              item.road_address.building_name ? ` (${item.road_address.building_name})` : ''
            }`
          : item.address?.address_name || ''
        const jibun = item.address?.address_name || ''
        const region =
          item.road_address?.region_3depth_name ||
          item.address?.region_3depth_name ||
          ''

        resolve({
          roadAddress: road || jibun,
          jibunAddress: jibun || road,
          regionName: region,
          fullAddress: road || jibun,
        })
      } else {
        resolve({
          roadAddress: `위도 ${lat.toFixed(5)}, 경도 ${lng.toFixed(5)}`,
          jibunAddress: `위도 ${lat.toFixed(5)}, 경도 ${lng.toFixed(5)}`,
          regionName: '',
          fullAddress: `위도 ${lat.toFixed(5)}, 경도 ${lng.toFixed(5)}`,
        })
      }
    })
  })
}
