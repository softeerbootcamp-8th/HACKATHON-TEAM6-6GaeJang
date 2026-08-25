import { X } from 'lucide-react'

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
        className="deadline-sheet bg-bg w-full rounded-t-[32px] px-5 pt-3 pb-[max(50px,env(safe-area-inset-bottom))]"
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
        <div className="mx-auto mt-12 flex max-w-[310px] items-center justify-center gap-3 text-center">
          <label className="flex items-center gap-2">
            <span className="sr-only">몇 시간 후</span>
            <select
              value={hours}
              onChange={(event) => onChange(Number(event.target.value), minutes)}
              className="bg-bg h-14 w-18 border-y text-center text-xl font-bold outline-none"
            >
              {Array.from({ length: 8 }, (_, index) => index + 1).map((value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ))}
            </select>
            <span className="text-xl font-bold whitespace-nowrap">시간</span>
          </label>
          <label className="flex items-center gap-2">
            <span className="sr-only">몇 분 후</span>
            <select
              value={minutes}
              onChange={(event) => onChange(hours, Number(event.target.value))}
              className="bg-bg h-14 w-18 border-y text-center text-xl font-bold outline-none"
            >
              <option value={0}>00</option>
              <option value={30}>30</option>
            </select>
            <span className="text-xl font-bold whitespace-nowrap">분 후</span>
          </label>
        </div>
        <button
          type="button"
          onClick={onSave}
          className="bg-primary text-primary-fg mt-24 h-13 w-full rounded-xl text-sm font-bold"
        >
          저장
        </button>
      </section>
    </div>
  )
}
