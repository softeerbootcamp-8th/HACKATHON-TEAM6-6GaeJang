import { useState } from 'react'

import { Input } from '@/components/ui/input'

import { StepLayout } from './StepLayout'

type PasswordStepProps = {
  value: string
  onChange: (value: string) => void
  onNext: () => void
  onBack: () => void
}

/** 비밀번호: 8~64자 + 확인 입력 일치. 재방문 로그인·재인증에 쓰인다. */
export function PasswordStep({ value, onChange, onNext, onBack }: PasswordStepProps) {
  const [confirm, setConfirm] = useState('')
  const lengthOk = value.length >= 8 && value.length <= 64
  const matched = value === confirm
  const valid = lengthOk && matched

  // 확인란에 뭔가 입력했을 때만 불일치 에러를 보여준다(첫 입력부터 빨갛지 않게).
  const showMismatch = confirm.length > 0 && !matched

  return (
    <StepLayout
      title="비밀번호를 설정해주세요"
      description="다음 로그인부터 사용해요. 8자 이상."
      onBack={onBack}
      onNext={onNext}
      nextDisabled={!valid}
    >
      <div className="flex flex-col gap-3">
        <div className="flex flex-col gap-1.5">
          <Input
            type="password"
            autoFocus
            placeholder="비밀번호 (8자 이상)"
            value={value}
            maxLength={64}
            onChange={(e) => onChange(e.target.value)}
            aria-label="비밀번호"
            aria-invalid={value.length > 0 && !lengthOk}
          />
          {value.length > 0 && !lengthOk && (
            <p role="status" className="text-down text-sm">
              8자 이상 64자 이하로 입력해주세요.
            </p>
          )}
        </div>

        <div className="flex flex-col gap-1.5">
          <Input
            type="password"
            placeholder="비밀번호 확인"
            value={confirm}
            maxLength={64}
            onChange={(e) => setConfirm(e.target.value)}
            aria-label="비밀번호 확인"
            aria-invalid={showMismatch}
          />
          {showMismatch && (
            <p role="status" className="text-down text-sm">
              비밀번호가 일치하지 않아요.
            </p>
          )}
        </div>
      </div>
    </StepLayout>
  )
}
