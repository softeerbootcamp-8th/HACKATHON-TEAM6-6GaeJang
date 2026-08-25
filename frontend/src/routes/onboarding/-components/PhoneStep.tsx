import { Input } from '@/components/ui/input'

import { StepLayout } from './StepLayout'

type PhoneStepProps = {
  value: string
  onChange: (value: string) => void
  onNext: () => void
}

/** 휴대폰 번호: 숫자만, 최대 11자리. 표준 휴대폰이라 11자리를 채워야 다음으로 넘어간다. */
export function PhoneStep({ value, onChange, onNext }: PhoneStepProps) {
  const valid = /^\d{11}$/.test(value)

  return (
    <StepLayout
      title="휴대폰 번호를 입력해주세요"
      description="본인 확인을 위해 인증번호를 보내드려요."
      onNext={onNext}
      nextDisabled={!valid}
    >
      <Input
        type="tel"
        inputMode="numeric"
        autoFocus
        placeholder="01012345678"
        value={value}
        // 숫자만 남기고 11자리로 자른다.
        onChange={(e) => onChange(e.target.value.replace(/\D/g, '').slice(0, 11))}
        aria-label="휴대폰 번호"
      />
    </StepLayout>
  )
}
