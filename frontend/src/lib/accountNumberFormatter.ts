/**
 * 계좌번호 자동 하이픈 포맷팅.
 *
 * 은행별로 자릿수 구성이 달라 전 은행을 정확히 커버할 수 없다. 확인된 은행만 표로 두고,
 * 모르는 은행(표에 없거나 은행명을 아직 안 입력)이면 숫자만 추출해 그대로 둔다 — 잘못된
 * 위치에 하이픈을 넣는 것보다 안 넣는 게 안전하다. 사용자가 원하면 직접 하이픈을 입력할 수 있다.
 */
const BANK_DIGIT_GROUPS: Record<string, number[]> = {
  카카오뱅크: [4, 2, 7],
  케이뱅크: [3, 3, 6],
  토스뱅크: [3, 4, 6],
  국민은행: [6, 2, 6],
  KB국민은행: [6, 2, 6],
  신한은행: [3, 3, 6],
  우리은행: [4, 3, 6],
  하나은행: [3, 6, 5],
  KEB하나은행: [3, 6, 5],
  농협은행: [3, 4, 4, 2],
  NH농협은행: [3, 4, 4, 2],
  기업은행: [3, 6, 2, 3],
  IBK기업은행: [3, 6, 2, 3],
}

function findDigitGroups(bankName: string): number[] | undefined {
  const normalized = bankName.replace(/\s/g, '')
  if (!normalized) return undefined
  const key = Object.keys(BANK_DIGIT_GROUPS).find(
    (name) => normalized.includes(name) || name.includes(normalized),
  )
  return key ? BANK_DIGIT_GROUPS[key] : undefined
}

/** 표에 있는 은행이면 자릿수에 맞춰 하이픈을 넣고, 없으면 숫자만 추출해 돌려준다. */
export function formatAccountNumber(bankName: string, value: string): string {
  const digits = value.replace(/\D/g, '').slice(0, 20)
  const groups = findDigitGroups(bankName)
  if (!groups) return digits

  let result = ''
  let cursor = 0
  for (const groupLength of groups) {
    if (cursor >= digits.length) break
    result += (result ? '-' : '') + digits.slice(cursor, cursor + groupLength)
    cursor += groupLength
  }
  return result
}
