import { useEffect, useRef, type UIEvent } from 'react'
import { X } from 'lucide-react'

const ITEM_HEIGHT = 56
const HOURS = Array.from({ length: 25 }, (_, index) => index)
const MINUTES = [0, 30]

type DeadlineSheetProps = {
  hours: number
  minutes: number
  onChange: (hours: number, minutes: number) => void
  onClose: () => void
  onSave: () => void
}

export function DeadlineSheet({ hours, minutes, onChange, onClose, onSave }: DeadlineSheetProps) {
  return (
    <div
      className="bg-scrim absolute inset-0 z-50 flex items-end"
      role="presentation"
      onMouseDown={onClose}
    >
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="deadline-title"
        className="sheet-slide-up bg-bg w-full rounded-t-[32px] px-5 pt-3 pb-[max(50px,env(safe-area-inset-bottom))]"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="bg-border mx-auto h-1 w-32 rounded-full" />
        <div className="mt-4 flex items-center justify-between">
          <h2 id="deadline-title" className="text-lg font-bold">
            마감 시간 설정
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="마감 시간 닫기"
            className="flex size-10 items-center justify-center rounded-full"
          >
            <X className="size-6" />
          </button>
        </div>
        <p className="text-muted-fg mt-1 text-xs">
          이 시간까지 정원이 다 모이지 않으면 자동으로 취소돼요
        </p>
        <div className="relative mx-auto mt-7 flex h-[280px] max-w-[310px] items-center justify-center gap-3 text-center">
          <div
            aria-hidden="true"
            className="border-border pointer-events-none absolute inset-x-5 top-1/2 z-10 h-14 -translate-y-1/2 border-y"
          />
          <div
            aria-hidden="true"
            className="from-bg pointer-events-none absolute inset-x-0 top-0 z-20 h-24 bg-linear-to-b to-transparent"
          />
          <div
            aria-hidden="true"
            className="from-bg pointer-events-none absolute inset-x-0 bottom-0 z-20 h-24 bg-linear-to-t to-transparent"
          />

          <WheelPicker
            label="몇 시간 후"
            values={HOURS}
            value={hours}
            formatValue={(value) => String(value)}
            onChange={(value) => onChange(value, value === 0 && minutes === 0 ? 30 : minutes)}
          />
          <span className="z-10 text-xl font-bold whitespace-nowrap">시간</span>
          <WheelPicker
            key={hours === 0 ? 'minutes-after-zero-hours' : 'minutes'}
            label="몇 분 후"
            values={hours === 0 ? [30] : MINUTES}
            value={minutes}
            formatValue={(value) => String(value).padStart(2, '0')}
            onChange={(value) => onChange(hours, value)}
          />
          <span className="z-10 text-xl font-bold whitespace-nowrap">분 후</span>
        </div>
        <button
          type="button"
          onClick={onSave}
          className="bg-primary text-primary-fg mt-6 h-13 w-full rounded-xl text-sm font-bold"
        >
          저장
        </button>
      </section>
    </div>
  )
}

type WheelPickerProps = {
  label: string
  values: readonly number[]
  value: number
  formatValue: (value: number) => string
  onChange: (value: number) => void
}

function WheelPicker({ label, values, value, formatValue, onChange }: WheelPickerProps) {
  const wheelRef = useRef<HTMLDivElement>(null)
  const selectedIndex = Math.max(0, values.indexOf(value))
  const optionIdPrefix = label.replaceAll(' ', '-')

  useEffect(() => {
    wheelRef.current?.scrollTo({ top: selectedIndex * ITEM_HEIGHT })
    // 선택창이 열릴 때 저장돼 있던 위치로 한 번만 맞춘다. 스크롤 중 재실행하면 휠이 튄다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleScroll = (event: UIEvent<HTMLDivElement>) => {
    const nextIndex = Math.min(
      values.length - 1,
      Math.max(0, Math.round(event.currentTarget.scrollTop / ITEM_HEIGHT)),
    )
    const nextValue = values[nextIndex]
    if (nextValue !== value) onChange(nextValue)
  }

  const selectValue = (index: number) => {
    wheelRef.current?.scrollTo({ top: index * ITEM_HEIGHT, behavior: 'smooth' })
    onChange(values[index])
  }

  return (
    <div
      ref={wheelRef}
      role="listbox"
      aria-label={label}
      aria-activedescendant={`${optionIdPrefix}-${value}`}
      tabIndex={0}
      onScroll={handleScroll}
      className="deadline-wheel z-10 h-[280px] w-20 snap-y snap-mandatory overflow-y-auto py-28"
    >
      {values.map((option, index) => (
        <button
          id={`${optionIdPrefix}-${option}`}
          key={option}
          type="button"
          role="option"
          aria-selected={option === value}
          onClick={() => selectValue(index)}
          className={`flex h-14 w-full snap-center items-center justify-center text-xl transition-all ${
            option === value ? 'text-fg font-bold' : 'text-muted-fg/35'
          }`}
        >
          {formatValue(option)}
        </button>
      ))}
    </div>
  )
}
