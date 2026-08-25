/**
 * 전화번호 자동 포맷팅 유틸리티.
 * 숫자만 추출하여 11자리 이하를 3-4-4 (또는 3-3-4) 하이픈 포맷으로 변환한다.
 * 사용자가 키보드로 하이픈을 직접 입력/수정할 필요 없이 숫자 입력에 따라 자동으로 적용된다.
 */
export function formatPhoneNumber(value: string): string {
  const digits = value.replace(/\D/g, '').slice(0, 11)
  if (digits.length <= 3) {
    return digits
  }
  if (digits.length <= 7) {
    return `${digits.slice(0, 3)}-${digits.slice(3)}`
  }
  return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`
}

/** 하이픈이 포함된 문자열에서 순수 숫자 11자리만 추출 */
export function unformatPhoneNumber(value: string): string {
  return value.replace(/\D/g, '').slice(0, 11)
}
