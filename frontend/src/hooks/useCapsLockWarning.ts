import { useState } from 'react'

/**
 * 비밀번호 등 민감한 입력 필드에 캡스락 켜짐 여부를 표시하기 위한 훅.
 * 반환된 핸들러를 input에 그대로 스프레드해서 쓴다.
 */
export function useCapsLockWarning() {
  const [capsLockOn, setCapsLockOn] = useState(false)

  const onKeyUp = (e: React.KeyboardEvent<HTMLInputElement>) => {
    setCapsLockOn(e.getModifierState('CapsLock'))
  }

  const onBlur = () => {
    setCapsLockOn(false)
  }

  return { capsLockOn, onKeyUp, onBlur }
}
