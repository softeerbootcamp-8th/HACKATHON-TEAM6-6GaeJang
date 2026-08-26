const ROAD_ADDRESS = /\S+(?:대로|로|길)(?:\s+\d+(?:-\d+)?)?/u
const JIBUN_ADDRESS = /\S+(?:동|가|읍|면|리)(?:\s+(?:산\s*)?\d+(?:-\d+)?)?/u
const DISTRICT = /(?:구|군|시)$/u

function parseAddress(value?: string) {
  if (!value) return null

  const address = value.replace(/^\[지번\]\s*/u, '').trim()

  if (!address) return null

  const match = address.match(ROAD_ADDRESS) ?? address.match(JIBUN_ADDRESS)
  if (match?.index != null) return address.slice(match.index).trim()

  const tokens = address.split(/\s+/u).filter(Boolean)
  const districtIndex = tokens.findLastIndex((token) => DISTRICT.test(token))
  if (districtIndex < 0) return address

  const afterDistrict = tokens.slice(districtIndex + 1).join(' ')
  return afterDistrict || tokens[districtIndex]
}

/** 도로명(없으면 지번)부터 상세주소까지 표시하고, 뒤 주소가 없을 때만 구·군·시를 표시한다. */
export function formatLocalAddress(value?: string) {
  return parseAddress(value) ?? ''
}
