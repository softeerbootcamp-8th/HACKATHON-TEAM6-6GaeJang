/**
 * 금액 입력 자동 콤마 포맷팅.
 *
 * `type="number"` 를 쓰지 않는 이유: 브라우저 네이티브 스피너(업다운 버튼)와 휠 스크롤로
 * 값이 바뀌고, `1e5` 같은 입력이 허용되며(그 상태의 value 는 빈 문자열이라 조용히 0 이 된다),
 * 무엇보다 숫자가 아닌 콤마를 value 에 담을 수 없다. 그래서 text + inputMode="numeric" 으로
 * 받고 포맷은 여기서 처리한다.
 */

/** 최대 자릿수. 배달 메뉴 금액이라 7자리(999만 원)면 충분하다. */
const MAX_DIGITS = 7

/** 입력값에서 숫자만 추출해 천 단위 콤마를 붙인다. 빈 입력은 빈 문자열로 둔다. */
export function formatPrice(value: string): string {
  const digits = unformatPrice(value)
  if (!digits) return ''
  return Number(digits).toLocaleString('ko-KR')
}

/** 콤마가 포함된 문자열에서 순수 숫자만 추출 */
export function unformatPrice(value: string): string {
  return value.replace(/\D/g, '').slice(0, MAX_DIGITS)
}
