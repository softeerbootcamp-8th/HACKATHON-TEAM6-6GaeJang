import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { ChevronDown, MapPin, X } from 'lucide-react'

import { formatAccountNumber } from '@/lib/accountNumberFormatter'
import type { PotCreateRequest } from '@/api/generated/model'

import { AddressSetupStep } from '../../-components/address/AddressSetupStep'
import type { SelectedLocation } from '../../-components/address/KakaoMapPicker'
import { CapacityCounter } from './CapacityCounter'
import { DeadlineSheet } from '../new/-components/DeadlineSheet'
import { useStoreNameAutofill } from '../new/-hooks/useStoreNameAutofill'

/** 서버가 요청을 받을 때도 선택한 최소 마감시간이 줄지 않도록 전송 지연만큼 여유를 둔다. */
const DEADLINE_REQUEST_BUFFER_MS = 15_000

const countNonWhitespaceCharacters = (value: string) =>
  Array.from(value.replaceAll(/\s/gu, '')).length

/** 만날 장소는 지도에서 고른 좌표까지 한 덩어리로 움직이므로 폼 상태에서 분리한다. */
type TextFields = Omit<
  PotCreateRequest,
  | 'latitude'
  | 'longitude'
  | 'deadline'
  | 'meetingPlace'
  | 'meetingRoadAddress'
  | 'meetingJibunAddress'
  | 'minOrderAmount'
>

/**
 * 생성/수정 화면이 함께 쓰는 초기값. 수정은 기존 팟에서 채우고, 생성은 빈 값으로 시작한다.
 * 마감시간은 절대 시각이 아니라 "지금부터 N시간 M분"이라 남은 시간으로 넣는다.
 */
export type PotFormInitialValues = {
  fields: TextFields
  meetingLocation: SelectedLocation | null
  /** 마감까지 남은 시간. null이면 사용자가 아직 고르지 않은 상태로 둔다. */
  remaining: { hours: number; minutes: number } | null
}

export const emptyPotFormValues: PotFormInitialValues = {
  fields: {
    title: '',
    storeName: '',
    storeUrl: '',
    capacity: 2,
    description: '',
    bankName: '',
    accountNumber: '',
    accountHolder: '',
  },
  meetingLocation: null,
  remaining: null,
}

type PotFormProps = {
  /** 헤더 제목. 생성은 "새 배달팟 만들기", 수정은 "배달팟 정보 수정". */
  heading: string
  /** 제출 버튼 문구와 진행 중 문구. */
  submitLabel: string
  submittingLabel: string
  initialValues: PotFormInitialValues
  isSubmitting: boolean
  /** 서버 에러 등 폼 밖에서 생긴 메시지. 폼 자체 검증 메시지와 같은 자리에 뜬다. */
  externalError?: string
  onSubmit: (request: PotCreateRequest) => void
  onClose: () => void
}

/**
 * 배달팟 생성/수정 공용 폼.
 *
 * <p>두 화면이 필드 구성·검증·마감시간 선택 방식까지 같아서 한 컴포넌트로 둔다. 복제하면
 * 필드가 하나 늘 때마다 두 곳을 고쳐야 하고, 한쪽을 빼먹으면 수정 화면이 값을 지워 버린다
 * (전체 값을 다시 보내는 PUT이라 빠진 필드는 곧 삭제다).
 *
 * <p>마감시간을 절대 시각이 아니라 "N시간 후" 상대값으로 다루는 것도 두 화면이 같다. 수정
 * 화면은 저장된 절대 시각에서 남은 시간을 역산해 initialValues로 받는다.
 */
export function PotForm({
  heading,
  submitLabel,
  submittingLabel,
  initialValues,
  isSubmitting,
  externalError,
  onSubmit,
  onClose,
}: PotFormProps) {
  const [form, setForm] = useState(initialValues.fields)
  const [meetingLocation, setMeetingLocation] = useState<SelectedLocation | null>(
    initialValues.meetingLocation,
  )
  const [locationPickerOpen, setLocationPickerOpen] = useState(false)
  const [hours, setHours] = useState(initialValues.remaining?.hours ?? 0)
  const [minutes, setMinutes] = useState(initialValues.remaining?.minutes ?? 30)
  const [deadlineLabel, setDeadlineLabel] = useState(
    initialValues.remaining ? formatRemaining(initialValues.remaining) : '',
  )
  const [sheetOpen, setSheetOpen] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')

  // 주소 선택 화면은 URL이 안 바뀌어 브라우저 히스토리에 항목이 안 쌓인다. 그대로 두면
  // 뒤로가기를 눌렀을 때 이 화면이 아니라 이 폼을 띄운 페이지로 튕긴다. pushState로
  // 항목을 하나 쌓고 popstate에서 다시 닫아 맞춘다.
  const openLocationPicker = () => {
    window.history.pushState({ locationPickerOpen: true }, '')
    setLocationPickerOpen(true)
  }

  useEffect(() => {
    // AddressSetupStep이 지도 화면용으로 항목을 하나 더 쌓을 수 있으니, 무조건 닫지 않고
    // popstate가 남긴 state를 보고 판단한다 — 지도→검색처럼 이 단계 안쪽으로 돌아온 경우까지
    // 폼으로 튕겨버리면 안 된다.
    const handlePopState = (event: PopStateEvent) => {
      setLocationPickerOpen(event.state?.locationPickerOpen === true)
    }
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  const setField = <K extends keyof TextFields>(key: K, value: TextFields[K]) => {
    setForm((current) => ({ ...current, [key]: value }))
  }

  // 링크를 붙여넣으면 링크만 잘라 넣고 가게명을 자동으로 채운다. 실패하면 손입력 상태로 남는다.
  const storeName = useStoreNameAutofill({
    onStoreUrl: (storeUrl) => setField('storeUrl', storeUrl),
    onStoreName: (value) => setField('storeName', value),
  })

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    setErrorMessage('')
    if (!meetingLocation) {
      setErrorMessage('만날 장소를 지도에서 선택해주세요.')
      return
    }
    if (!deadlineLabel) {
      setErrorMessage('마감 시간을 선택해주세요.')
      return
    }
    const deadline = new Date(
      Date.now() + (hours * 60 + minutes) * 60_000 + DEADLINE_REQUEST_BUFFER_MS,
    ).toISOString()
    // 좌표는 반드시 만날 장소의 것이어야 한다 — 회원 집 좌표를 쓰면 홈의 300m 반경 판정이
    // 실제 수령 장소와 어긋나, 근처 사람에게 안 보이거나 먼 사람에게 보인다.
    onSubmit({
      ...form,
      meetingPlace: meetingLocation.address,
      meetingRoadAddress: meetingLocation.roadAddress,
      meetingJibunAddress: meetingLocation.jibunAddress,
      latitude: meetingLocation.latitude,
      longitude: meetingLocation.longitude,
      // 최소 주문 금액은 이 폼에서 받지 않는다. 기존 API/DB 계약이 요구하는 값은
      // "제한 없음"을 뜻하는 0으로 고정해 상세 조회와 기존 데이터 호환성을 유지한다.
      minOrderAmount: 0,
      deadline,
    })
  }

  if (locationPickerOpen) {
    return (
      <AddressSetupStep
        onBack={() => window.history.back()}
        onComplete={(location) => {
          setMeetingLocation(location)
          // onComplete는 항상 AddressSetupStep의 지도 화면에서만 불린다 — 그 화면에 오려면
          // 이 주소 선택 단계(1) + 지도 화면(1), 총 두 단계를 눌러 왔으므로 두 칸을 한 번에 되돌린다.
          window.history.go(-2)
        }}
        confirmLabel="이 위치로 설정"
      />
    )
  }

  const visibleError = errorMessage || externalError

  return (
    <div className="sheet-slide-up relative flex h-full flex-col">
      <header className="bg-bg relative z-20 flex shrink-0 items-center justify-center px-5 pt-[max(28px,env(safe-area-inset-top))] pb-4">
        <h1 className="text-lg font-bold">{heading}</h1>
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
        id="pot-form"
        onSubmit={handleSubmit}
        className="min-h-0 flex-1 overflow-y-auto px-5 pt-3 pb-32"
      >
        <FormField label="글 제목">
          <input
            required
            maxLength={100}
            value={form.title}
            onChange={(e) => {
              if (countNonWhitespaceCharacters(e.target.value) <= 30) {
                setField('title', e.target.value)
              }
            }}
            placeholder="제목을 입력해주세요"
            className="form-control"
          />
        </FormField>
        <FormField
          label="가게 링크"
          hint="배달앱 가게 페이지에서 공유 링크를 복사해서 붙여넣어 주세요"
        >
          <input
            required
            type="url"
            maxLength={500}
            value={form.storeUrl}
            onChange={(e) => setField('storeUrl', e.target.value)}
            onPaste={storeName.handlePaste}
            onBlur={(e) => storeName.handleBlur(e.target.value)}
            placeholder="주문할 가게의 링크를 입력해주세요"
            className="form-control"
          />
        </FormField>
        <FormField label="가게명">
          <input
            required
            maxLength={100}
            value={form.storeName}
            onChange={(e) => {
              storeName.handleManualEdit()
              setField('storeName', e.target.value)
            }}
            placeholder="가게명을 입력해주세요"
            className="form-control"
          />
          {/* 붙여넣기 뒤 비동기로 바뀌는 안내다. 화면을 보지 않는 사용자에게도 읽히게 알린다. */}
          <span aria-live="polite" className="text-muted-fg/70 mt-2 block text-xs">
            {storeName.hint}
          </span>
        </FormField>
        <FormField label="만날 장소" hint="정확한 위치는 멤버들과 채팅방에서 논의할 수 있어요">
          <button
            type="button"
            onClick={openLocationPicker}
            className={`form-control flex items-center gap-2 text-left ${meetingLocation ? 'text-fg' : 'text-muted-fg'}`}
          >
            <MapPin className="text-primary size-5 shrink-0" />
            <span className="truncate">
              {meetingLocation?.address || '지도에서 만날 장소를 선택해주세요'}
            </span>
          </button>
          {meetingLocation?.jibunAddress && (
            <span className="text-muted-fg/70 mt-2 block text-xs">
              {meetingLocation.jibunAddress}
            </span>
          )}
        </FormField>
        <FormField label="배달팟 인원">
          <CapacityCounter value={form.capacity} onChange={(next) => setField('capacity', next)} />
        </FormField>
        <FormField label="마감 시간">
          <button
            type="button"
            onClick={() => setSheetOpen(true)}
            className={`form-control flex items-center justify-between text-left ${deadlineLabel ? 'text-fg' : 'text-muted-fg'}`}
          >
            {deadlineLabel || '마감 시간을 선택해주세요'}
            <ChevronDown className="fill-muted-fg size-5" />
          </button>
        </FormField>
        <FormField label="상세 설명 (선택)">
          <textarea
            maxLength={2000}
            value={form.description}
            onChange={(e) => {
              if (countNonWhitespaceCharacters(e.target.value) <= 200) {
                setField('description', e.target.value)
              }
            }}
            placeholder="상세 설명을 입력해주세요"
            className="form-control h-32 resize-none py-4"
          />
        </FormField>
        <FormField label="정산받을 계좌" hint="참여자들이 이 계좌로 정산 금액을 보내요">
          <div className="grid grid-cols-2 gap-2">
            <input
              required
              maxLength={30}
              value={form.bankName}
              onChange={(e) => {
                const bankName = e.target.value
                setForm((current) => ({
                  ...current,
                  bankName,
                  accountNumber: formatAccountNumber(bankName, current.accountNumber),
                }))
              }}
              placeholder="은행명"
              className="form-control"
            />
            <input
              required
              maxLength={30}
              value={form.accountHolder}
              onChange={(e) => setField('accountHolder', e.target.value)}
              placeholder="예금주"
              className="form-control"
            />
          </div>
          <input
            required
            pattern="[0-9-]{8,30}"
            value={form.accountNumber}
            onChange={(e) =>
              setField('accountNumber', formatAccountNumber(form.bankName, e.target.value))
            }
            placeholder="계좌 번호"
            inputMode="numeric"
            className="form-control mt-2"
          />
        </FormField>
        {visibleError && (
          <p role="alert" className="text-down mt-4 text-sm">
            {visibleError}
          </p>
        )}
      </form>

      <div className="bg-bg absolute inset-x-0 bottom-0 z-20 px-5 pt-3 pb-[max(24px,env(safe-area-inset-bottom))]">
        <button
          form="pot-form"
          type="submit"
          disabled={isSubmitting}
          className="bg-primary text-primary-fg h-13 w-full rounded-xl text-sm font-bold disabled:opacity-50"
        >
          {isSubmitting ? submittingLabel : submitLabel}
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
            setSheetOpen(false)
          }}
        />
      )}
    </div>
  )
}

function formatRemaining({ hours, minutes }: { hours: number; minutes: number }) {
  return `${hours}시간${minutes ? ` ${minutes}분` : ''} 후`
}

export function FormField({
  label,
  hint,
  children,
}: {
  label: string
  hint?: string
  children: ReactNode
}) {
  return (
    <label className="mb-6 block">
      <span className="text-muted-fg mb-2 block text-sm font-semibold">{label}</span>
      {children}
      {hint && <span className="text-muted-fg/70 mt-2 block text-xs">{hint}</span>}
    </label>
  )
}
