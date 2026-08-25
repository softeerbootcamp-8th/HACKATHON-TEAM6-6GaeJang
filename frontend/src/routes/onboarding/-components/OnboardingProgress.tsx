import { cn } from '@/lib/utils'

const STEP_LABELS = ['휴대폰', '인증', '비밀번호', '닉네임', '주소']

/** 현재 단계(0-based)를 점/막대로 표시. */
export function OnboardingProgress({ current }: { current: number }) {
  return (
    <ol className="flex items-center gap-2" aria-label={`온보딩 ${current + 1}/${STEP_LABELS.length}단계`}>
      {STEP_LABELS.map((label, i) => (
        <li key={label} className="flex flex-1 flex-col gap-1.5">
          <span
            className={cn(
              'h-1.5 rounded-full transition-colors',
              i <= current ? 'bg-primary' : 'bg-muted',
            )}
          />
          <span className={cn('text-xs', i === current ? 'text-fg font-medium' : 'text-muted-fg')}>
            {label}
          </span>
        </li>
      ))}
    </ol>
  )
}
