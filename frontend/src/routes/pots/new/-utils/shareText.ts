/**
 * 배달앱 공유 텍스트에서 링크와 가게명을 뽑는다.
 *
 * 배민·요기요 앱의 공유 버튼은 링크만 복사하지 않고 문구를 함께 붙여준다.
 *
 *   '호백반점 본점' 어때요? 배달의민족 앱에서 확인해보세요.
 *   https://s.baemin.com/0d000f0kYdAUl
 *
 *   '60계치킨-광주용봉점' 요기요 앱에서 확인해보세요.
 *   https://ws.yogiyo.co.kr/g27z59vb
 *
 * 이 문구가 두 가지 일을 해준다.
 *
 * 1. 그대로 붙여넣으면 `<input type="url">` 검증에 걸려 제출이 막힌다 — 링크만 잘라내야 한다.
 * 2. 인용부호 안에 가게명이 들어 있다 — 네트워크 요청 없이 가게명을 얻을 수 있다.
 *
 * 특히 배민은 서버에서 가게명을 가져올 방법이 아예 없어(응답 어디에도 문자열이 없다)
 * 이 경로가 유일한 자동 기입 수단이다. 쿠팡이츠는 반대로 순수 링크만 공유되므로
 * 여기서 가게명이 나오지 않고, 서버 추출(`POST /api/pots/store-name`)이 담당한다.
 */

/** 링크만 뽑는다. 공백·인용부호·꺾쇠는 URL에 올 수 없으니 경계로 쓴다. */
const URL_PATTERN = /https?:\/\/[^\s<>"'`]+/i

/**
 * URL 끝에 딸려온 문장부호. "확인해보세요 (https://...)" 처럼 감싸인 경우 닫는 괄호가 붙는다.
 * 실제 링크의 마지막 글자로는 거의 쓰이지 않는 문자만 골라 제거한다.
 */
const TRAILING_PUNCTUATION = /[.,;:!?)\]}>」』」"'`·]+$/

/**
 * 인용부호로 감싼 가게명. 앱·OS마다 곧은 인용부호(')와 굽은 인용부호(''), 큰따옴표,
 * 한국어 낫표(「」『』)가 섞여 나오므로 전부 받는다. 한 종류만 두면 조용히 실패한다.
 */
const QUOTED_NAME_PATTERN =
  /['"‘’“”]([^'"‘’“”\n]{1,100})['"‘’“”]|[「『]([^」』\n]{1,100})[」』]/

/** DB 컬럼 길이(`pots.store_name`)와 같다. */
const MAX_STORE_NAME_LENGTH = 100

export type ParsedShareText = {
  /** 잘라낸 순수 링크. */
  storeUrl: string
  /** 공유 문구에서 얻은 가게명. 없으면 null — 서버 추출 또는 손입력으로 넘긴다. */
  storeName: string | null
}

/**
 * @returns 붙여넣은 텍스트에 링크가 없으면 null. 이 경우 호출부는 붙여넣기를 가로채지 않고
 *   브라우저 기본 동작에 맡긴다 — 사용자가 무엇을 붙여넣었는지 화면에서 볼 수 있어야 한다.
 */
export function parseShareText(text: string): ParsedShareText | null {
  const urlMatch = URL_PATTERN.exec(text)
  if (!urlMatch) return null

  const storeUrl = urlMatch[0].replace(TRAILING_PUNCTUATION, '')

  // 링크를 먼저 빼고 남은 문구에서만 가게명을 찾는다. 링크 안의 따옴표 인코딩을
  // 가게명으로 오인하지 않게 하는 것이 목적이다.
  const prose = text.replace(urlMatch[0], ' ')

  return { storeUrl, storeName: extractQuotedName(prose) }
}

function extractQuotedName(prose: string): string | null {
  const match = QUOTED_NAME_PATTERN.exec(prose)
  if (!match) return null

  const name = (match[1] ?? match[2] ?? '').trim()
  if (!name || name.length > MAX_STORE_NAME_LENGTH) return null
  // 링크 조각이나 문장이 잡힌 경우를 막는다.
  if (name.includes('http')) return null

  return name
}
