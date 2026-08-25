import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'

import { StepLayout } from './StepLayout'

type AddressStepProps = {
  value: string
  onChange: (value: string) => void
  rememberMe: boolean
  onRememberMeChange: (value: boolean) => void
  onSubmit: () => void
  onBack: () => void
  isSubmitting: boolean
  error?: string
}

/** 주소: 제약 없음. 마지막 단계라 다음 버튼이 가입 제출을 트리거한다. */
export function AddressStep({
  value,
  onChange,
  rememberMe,
  onRememberMeChange,
  onSubmit,
  onBack,
  isSubmitting,
  error,
}: AddressStepProps) {
  const valid = value.trim().length > 0

  return (
    <StepLayout
      title="주소를 입력해주세요"
      description="배달받을 주소예요."
      onBack={onBack}
      onNext={onSubmit}
      nextLabel="가입 완료"
      nextDisabled={!valid}
      nextLoading={isSubmitting}
    >
      <div className="flex flex-col gap-1.5">
        <Input
          autoFocus
          placeholder="서울시 강남구 …"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          aria-label="주소"
        />
        {error && (
          <p role="alert" className="text-down text-sm">
            {error}
          </p>
        )}
      </div>

      <label className="flex cursor-pointer items-center gap-2 text-sm">
        <Checkbox
          checked={rememberMe}
          onChange={(e) => onRememberMeChange(e.target.checked)}
        />
        자동 로그인 유지
      </label>
    </StepLayout>
  )
}
