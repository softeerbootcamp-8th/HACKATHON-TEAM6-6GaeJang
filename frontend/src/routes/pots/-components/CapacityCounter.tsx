import type { ReactNode } from 'react'
import { Minus, Plus } from 'lucide-react'

/** 모집 정원의 도메인 상한/하한. 서버 검증(@Min 2 / @Max 4)과 같은 값이다. */
const MIN_CAPACITY = 2
const MAX_CAPACITY = 4

type CapacityCounterProps = {
  value: number
  onChange: (next: number) => void
  /**
   * 이 값 아래로는 못 내린다. 참여자가 있는 팟에서 이미 들어온 인원만큼을 바닥으로 잠글 때 쓴다.
   * 생략하면 도메인 최소값(2명)까지 내려간다.
   */
  min?: number
}

/** 생성·수정·모집조건 변경 화면이 함께 쓰는 인원 카운터. */
export function CapacityCounter({ value, onChange, min = MIN_CAPACITY }: CapacityCounterProps) {
  const lowerBound = Math.max(min, MIN_CAPACITY)

  return (
    <div className="bg-surface flex h-14 items-center justify-between rounded-xl px-3">
      <CounterButton
        label="인원 줄이기"
        disabled={value <= lowerBound}
        onClick={() => onChange(value - 1)}
      >
        <Minus className="size-5" />
      </CounterButton>
      <span className="font-bold">{value}명</span>
      <CounterButton
        label="인원 늘리기"
        disabled={value >= MAX_CAPACITY}
        onClick={() => onChange(value + 1)}
      >
        <Plus className="size-5" />
      </CounterButton>
    </div>
  )
}

function CounterButton({
  label,
  disabled,
  onClick,
  children,
}: {
  label: string
  disabled: boolean
  onClick: () => void
  children: ReactNode
}) {
  return (
    <button
      type="button"
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
      className="bg-bg disabled:text-muted-fg/40 flex size-10 items-center justify-center rounded-full"
    >
      {children}
    </button>
  )
}
