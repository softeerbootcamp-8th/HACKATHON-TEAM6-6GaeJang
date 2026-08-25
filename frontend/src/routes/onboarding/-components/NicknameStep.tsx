import { Loader2 } from 'lucide-react'

import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

import { useNicknameAvailability, type NicknameStatus } from '../-hooks/useNicknameAvailability'
import { StepLayout } from './StepLayout'

type NicknameStepProps = {
  value: string
  onChange: (value: string) => void
  onNext: () => void
  onBack: () => void
}

const MESSAGE: Record<NicknameStatus, { text: string; tone: 'muted' | 'up' | 'down' } | null> = {
  empty: null,
  invalid: { text: '한글/영문 1~10자로 입력해주세요.', tone: 'down' },
  checking: { text: '확인 중…', tone: 'muted' },
  available: { text: '사용할 수 있는 닉네임이에요.', tone: 'up' },
  taken: { text: '이미 사용 중인 닉네임이에요.', tone: 'down' },
}

/** 닉네임: 한/영 10자, 한 글자마다 서버에 중복확인(디바운스). available 일 때만 다음. */
export function NicknameStep({ value, onChange, onNext, onBack }: NicknameStepProps) {
  const { status, isAvailable } = useNicknameAvailability(value)
  const message = MESSAGE[status]

  return (
    <StepLayout
      title="닉네임을 정해주세요"
      description="다른 사용자에게 보여지는 이름이에요."
      onBack={onBack}
      onNext={onNext}
      nextDisabled={!isAvailable}
    >
      <div className="flex flex-col gap-1.5">
        <div className="relative">
          <Input
            autoFocus
            placeholder="철수"
            value={value}
            maxLength={10}
            onChange={(e) => onChange(e.target.value.slice(0, 10))}
            aria-label="닉네임"
            aria-invalid={status === 'invalid' || status === 'taken'}
          />
          {status === 'checking' && (
            <Loader2 className="text-muted-fg absolute top-1/2 right-3 size-4 -translate-y-1/2 animate-spin" />
          )}
        </div>
        {message && (
          <p
            role="status"
            aria-live="polite"
            className={cn(
              'text-sm',
              message.tone === 'up' && 'text-up',
              message.tone === 'down' && 'text-down',
              message.tone === 'muted' && 'text-muted-fg',
            )}
          >
            {message.text}
          </p>
        )}
      </div>
    </StepLayout>
  )
}
