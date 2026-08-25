import { useState } from 'react'

import { Input } from '@/components/ui/input'

import { StepLayout } from './StepLayout'

type VerifyStepProps = {
  phoneNumber: string
  onNext: () => void
  onBack: () => void
}

/**
 * 전화번호 인증 — 데모라 실제 SMS 는 보내지 않는다.
 * 아무 6자리나 입력하면 통과한다(백엔드 검증 없음, 프론트 게이트일 뿐).
 */
export function VerifyStep({ phoneNumber, onNext, onBack }: VerifyStepProps) {
  const [code, setCode] = useState('')
  const valid = /^\d{6}$/.test(code)

  return (
    <StepLayout
      title="인증번호를 입력해주세요"
      description={`${phoneNumber} 로 전송했어요. (데모: 아무 6자리나 입력하면 통과)`}
      onBack={onBack}
      onNext={onNext}
      nextLabel="인증 확인"
      nextDisabled={!valid}
    >
      <Input
        inputMode="numeric"
        autoFocus
        placeholder="000000"
        value={code}
        onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
        aria-label="인증번호"
      />
    </StepLayout>
  )
}
