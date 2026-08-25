import { useEffect, useState } from 'react'

import { useCheckNickname } from '@/api/generated/member/member'

/** 닉네임: 한/영/숫자 2~10자. 백엔드 @Pattern 과 동일하게 맞춘다. */
export const NICKNAME_PATTERN = /^[가-힣a-zA-Z0-9]{2,10}$/

function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(id)
  }, [value, delayMs])
  return debounced
}

export type NicknameStatus = 'empty' | 'invalid' | 'checking' | 'available' | 'taken'

/**
 * 타이핑마다 서버에 중복확인을 던지되, 디바운스로 호출을 줄인다.
 * 형식이 맞고(디바운스가 안정된) 값에 대해서만 useCheckNickname(query)을 활성화한다.
 */
export function useNicknameAvailability(nickname: string) {
  const formatValid = NICKNAME_PATTERN.test(nickname)
  const debounced = useDebouncedValue(nickname, 300)
  // 형식이 맞고, 디바운스가 현재 입력을 따라잡았을 때만 서버에 묻는다.
  const settled = debounced === nickname
  const enabled = formatValid && settled

  const query = useCheckNickname(
    { nickname: debounced },
    { query: { enabled, staleTime: 10_000 } },
  )

  const available = enabled ? query.data?.data?.available : undefined

  const status: NicknameStatus = !nickname
    ? 'empty'
    : !formatValid
      ? 'invalid'
      : !settled || query.isFetching
        ? 'checking'
        : available
          ? 'available'
          : 'taken'

  return { status, isAvailable: status === 'available' }
}
