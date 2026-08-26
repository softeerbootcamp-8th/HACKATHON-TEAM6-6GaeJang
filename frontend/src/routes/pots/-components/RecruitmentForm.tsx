import { useState, type FormEvent, type ReactNode } from 'react'
import { ChevronDown, CircleAlert, Link2, X } from 'lucide-react'

import type { PotDetailResponse } from '@/api/generated/model'

import { DeadlineSheet } from '../new/-components/DeadlineSheet'
import { CapacityCounter } from './CapacityCounter'

/** 서버가 요청을 받을 때도 선택한 최소 마감시간이 줄지 않도록 전송 지연만큼 여유를 둔다. */
const DEADLINE_REQUEST_BUFFER_MS = 15_000

/** 마감 시간 선택 휠이 30분 단위라, 남은 시간을 역산할 때도 같은 단위로 맞춘다. */
const MINUTE_STEP = 30

export type RecruitmentValues = { capacity: number; deadline: string }

type RecruitmentFormProps = {
  pot: PotDetailResponse
  isSubmitting: boolean
  externalError?: string
  onSubmit: (values: RecruitmentValues) => void
  onClose: () => void
}

/**
 * 참여자가 이미 있는 팟의 수정 화면. 배달팟 인원과 마감 시간만 손댈 수 있다.
 *
 * <p>{@code PotForm}에 잠금 모드를 넣는 대신 별도 컴포넌트로 둔 이유는, 잠긴 화면에서 입력창·
 * 지도 선택·가게명 자동완성·계좌 포맷팅이 전부 빠져 공유할 코드가 거의 남지 않아서다.
 * 실제로 겹치는 건 마감 시트와 인원 카운터뿐이고 그 둘은 각각 컴포넌트로 공유한다.
 *
 * <p>줄이는 방향은 화면에서 미리 막는다 — 인원은 현재 정원이 바닥이고, 마감은 남은 시간보다
 * 짧게 고르면 저장 시 걸러낸다. 서버도 같은 규칙(POT_RECRUITMENT_CANNOT_SHRINK)으로 막지만,
 * 저장을 눌러야 알게 되는 것보다 카운터가 안 눌리는 편이 낫다.
 */
export function RecruitmentForm({
  pot,
  isSubmitting,
  externalError,
  onSubmit,
  onClose,
}: RecruitmentFormProps) {
  const currentCapacity = pot.capacity ?? 2
  const currentRemaining = remainingUntil(pot.deadline)

  const [capacity, setCapacity] = useState(currentCapacity)
  const [hours, setHours] = useState(currentRemaining?.hours ?? 0)
  const [minutes, setMinutes] = useState(currentRemaining?.minutes ?? MINUTE_STEP)
  const [deadlineLabel, setDeadlineLabel] = useState(
    currentRemaining ? formatRemaining(currentRemaining) : '',
  )
  const [sheetOpen, setSheetOpen] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')

  /**
   * 마감 시트에서 실제로 저장을 눌렀는지. 안 건드렸으면 기존 마감을 그대로 다시 보낸다.
   *
   * <p>이 플래그가 없으면 마감을 손대지 않아도 저장할 때마다 "지금 + 남은 시간"으로 값이 다시
   * 계산된다. 남은 시간은 30분 단위로 내림해 보여주므로 2시간 5분 남은 팟은 "2시간 후"가 되고,
   * 저장하면 마감이 5분 당겨져 서버에 거부당한다. 통과하더라도 총대가 건드리지도 않은
   * 마감 변경 공지가 채팅방에 나간다.
   */
  const [deadlineEdited, setDeadlineEdited] = useState(false)

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    setErrorMessage('')
    if (!deadlineLabel) {
      setErrorMessage('마감 시간을 선택해주세요.')
      return
    }

    if (!deadlineEdited) {
      onSubmit({ capacity, deadline: pot.deadline ?? '' })
      return
    }

    const deadlineMs = Date.now() + (hours * 60 + minutes) * 60_000 + DEADLINE_REQUEST_BUFFER_MS
    // 이미 지난 마감을 늘리는 경우가 있어 현재 마감과 직접 비교한다. 남은 시간끼리 비교하면
    // 지난 마감의 남은 시간이 음수라 무엇을 골라도 통과해 버린다.
    if (pot.deadline && deadlineMs < new Date(pot.deadline).getTime()) {
      setErrorMessage('마감 시간은 앞당길 수 없어요. 지금보다 뒤로 선택해주세요.')
      return
    }

    onSubmit({ capacity, deadline: new Date(deadlineMs).toISOString() })
  }

  const visibleError = errorMessage || externalError

  return (
    <div className="sheet-slide-up relative flex h-full flex-col">
      <header className="bg-bg relative z-20 flex shrink-0 items-center justify-center px-5 pt-[max(28px,env(safe-area-inset-top))] pb-4">
        <h1 className="text-lg font-bold">배달팟 정보 수정</h1>
        <button
          type="button"
          onClick={onClose}
          aria-label="닫기"
          className="absolute right-4 bottom-1 flex size-11 items-center justify-center rounded-full"
        >
          <X className="size-6" />
        </button>
      </header>

      <form
        id="recruitment-form"
        onSubmit={handleSubmit}
        className="min-h-0 flex-1 overflow-y-auto px-5 pt-3 pb-32"
      >
        <p
          role="status"
          className="bg-primary-soft text-primary flex items-start gap-2 rounded-xl px-4 py-3 text-sm font-semibold"
        >
          <CircleAlert className="mt-0.5 size-4 shrink-0" />
          <span>
            배달팟에 참여자가 있을 때는
            <br />
            배달팟 인원, 마감 시간만 수정할 수 있어요
          </span>
        </p>

        <div className="mt-6">
          <LockedField label="글 제목">{pot.title}</LockedField>
          <LockedField label="가게 링크">
            <span className="flex items-center gap-2">
              <Link2 className="text-primary size-4 shrink-0" />
              <span className="truncate">{stripScheme(pot.storeUrl)}</span>
            </span>
          </LockedField>
          <LockedField label="가게명">{pot.storeName}</LockedField>
          <LockedField label="만날 장소">{pot.meetingPlace}</LockedField>
        </div>

        <label className="mb-6 block">
          <span className="text-muted-fg mb-2 block text-sm font-semibold">배달팟 인원</span>
          {/* 현재 정원이 바닥이다. 줄이면 이미 들어온 참여자가 정원 밖으로 밀린다. */}
          <CapacityCounter value={capacity} onChange={setCapacity} min={currentCapacity} />
        </label>

        <label className="mb-6 block">
          <span className="text-muted-fg mb-2 block text-sm font-semibold">마감 시간</span>
          <button
            type="button"
            onClick={() => setSheetOpen(true)}
            className={`form-control flex items-center justify-between text-left ${deadlineLabel ? 'text-fg' : 'text-muted-fg'}`}
          >
            {deadlineLabel || '마감 시간을 선택해주세요'}
            <ChevronDown className="fill-muted-fg size-5" />
          </button>
        </label>

        <LockedField label="상세 설명 (선택)">{pot.description}</LockedField>
        <LockedField label="정산받을 계좌">
          {pot.account &&
            `${pot.account.bankName} ${pot.account.accountNumber} ${pot.account.accountHolder}`}
        </LockedField>

        {visibleError && (
          <p role="alert" className="text-down mt-4 text-sm">
            {visibleError}
          </p>
        )}
      </form>

      <div className="bg-bg absolute inset-x-0 bottom-0 z-20 px-5 pt-3 pb-[max(24px,env(safe-area-inset-bottom))]">
        <button
          form="recruitment-form"
          type="submit"
          disabled={isSubmitting}
          className="bg-primary text-primary-fg h-13 w-full rounded-xl text-sm font-bold disabled:opacity-50"
        >
          {isSubmitting ? '저장 중…' : '저장'}
        </button>
      </div>

      {sheetOpen && (
        <DeadlineSheet
          hours={hours}
          minutes={minutes}
          onChange={(nextHours, nextMinutes) => {
            setHours(nextHours)
            setMinutes(nextMinutes)
          }}
          onClose={() => setSheetOpen(false)}
          onSave={() => {
            setDeadlineLabel(formatRemaining({ hours, minutes }))
            setDeadlineEdited(true)
            setSheetOpen(false)
          }}
        />
      )}
    </div>
  )
}

/**
 * 잠긴 필드. 입력창 대신 값만 보여준다.
 *
 * <p>비활성 `<input>`으로 만들지 않는 이유는 폼 컨트롤처럼 보이면 눌러 보게 되기 때문이다.
 * 값이 없으면 줄 자체를 그리지 않는다 — 빈 상자만 남으면 뭘 못 고치는 건지 알 수 없다.
 */
function LockedField({ label, children }: { label: string; children: ReactNode }) {
  if (!children) return null

  return (
    <div className="mb-6">
      <span className="text-muted-fg mb-1 block text-sm font-semibold">{label}</span>
      <p className="text-sm leading-6 break-words">{children}</p>
    </div>
  )
}

/** 링크는 좁은 화면에서 잘리므로 스킴을 떼고 보여준다. 값 자체는 건드리지 않는다. */
function stripScheme(storeUrl: string | undefined) {
  return storeUrl?.replace(/^https?:\/\//u, '') ?? ''
}

function formatRemaining({ hours, minutes }: { hours: number; minutes: number }) {
  return `${hours}시간${minutes ? ` ${minutes}분` : ''} 후`
}

/**
 * 저장된 절대 마감시각을 "지금부터 N시간 M분"으로 되돌린다.
 * 이미 지난 마감은 null — 0시간 0분을 채워 두면 총대가 마감 칸을 건드리지 않고 저장해
 * 서버의 "최소 30분" 규칙에 걸린다.
 */
function remainingUntil(deadline: string | undefined) {
  if (!deadline) return null

  const remainingMinutes = Math.round((new Date(deadline).getTime() - Date.now()) / 60_000)
  if (remainingMinutes < MINUTE_STEP) return null

  const stepped = Math.floor(remainingMinutes / MINUTE_STEP) * MINUTE_STEP
  return { hours: Math.floor(stepped / 60), minutes: stepped % 60 }
}
