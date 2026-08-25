type AddressParts = {
  localAddress: string
  district?: string
}

const ROAD_ADDRESS = /(\S+(?:대로|로|길)\s+\d+(?:-\d+)?)/u
const JIBUN_ADDRESS = /(\S+(?:동|가|읍|면|리)\s+(?:산\s*)?\d+(?:-\d+)?)/u

function parseAddress(value?: string): AddressParts | null {
  if (!value) return null

  const address = value
    .replace(/^\[지번\]\s*/u, '')
    .replace(/\s*\([^)]*\)\s*$/u, '')
    .trim()

  if (!address) return null

  const match = address.match(ROAD_ADDRESS) ?? address.match(JIBUN_ADDRESS)
  if (!match) return { localAddress: address }

  const prefix = address.slice(0, match.index).trim()
  const district = prefix.split(/\s+/u).filter(Boolean).at(-1)

  return { localAddress: match[1], district }
}

/** 홈 상단처럼 좁은 영역에 도로명 또는 동·번지만 표시한다. */
export function formatLocalAddress(value?: string) {
  return parseAddress(value)?.localAddress ?? ''
}

/** 팟 카드와 상세처럼 장소 구분이 필요한 영역에는 구·군·시부터 표시한다. */
export function formatDistrictAddress(value?: string) {
  const parts = parseAddress(value)
  if (!parts) return ''

  return parts.district ? `${parts.district} ${parts.localAddress}` : parts.localAddress
}
