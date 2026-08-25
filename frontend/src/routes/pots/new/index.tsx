import { useState, type FormEvent, type ReactNode } from 'react'
import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { ChevronDown, MapPin, Minus, Plus, X } from 'lucide-react'

import { getGetPotsQueryKey, useCreatePot } from '@/api/generated/pot/pot'
import { formatAccountNumber } from '@/lib/accountNumberFormatter'
import { requireAuth } from '@/lib/authGuard'
import type { PotCreateRequest } from '@/api/generated/model'

import { AddressSetupStep } from '../../-components/address/AddressSetupStep'
import type { SelectedLocation } from '../../-components/address/KakaoMapPicker'
import { DeadlineSheet } from './-components/DeadlineSheet'
import { useStoreNameAutofill } from './-hooks/useStoreNameAutofill'

/** 서버가 요청을 받을 때도 선택한 최소 마감시간이 줄지 않도록 전송 지연만큼 여유를 둔다. */
const DEADLINE_REQUEST_BUFFER_MS = 15_000

const countNonWhitespaceCharacters = (value: string) =>
  Array.from(value.replaceAll(/\s/gu, '')).length

export const Route = createFileRoute('/pots/new/')({
  beforeLoad: ({ context }) => requireAuth(context.queryClient),
  component: NewPotPage,
})

/** 만날 장소는 지도에서 고른 좌표까지 한 덩어리로 움직이므로 폼 상태에서 분리한다. */
type FormState = Omit<
  PotCreateRequest,
  | 'latitude'
  | 'longitude'
  | 'deadline'
  | 'meetingPlace'
  | 'meetingRoadAddress'
  | 'meetingJibunAddress'
  | 'minOrderAmount'
>

const emptyForm: FormState = {
  title: '',
  storeName: '',
  storeUrl: '',
  capacity: 2,
  description: '',
  bankName: '',
  accountNumber: '',
  accountHolder: '',
}

function NewPotPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [form, setForm] = useState(emptyForm)
  const [meetingLocation, setMeetingLocation] = useState<SelectedLocation | null>(null)
  const [locationPickerOpen, setLocationPickerOpen] = useState(false)
  const [hours, setHours] = useState(0)
  const [minutes, setMinutes] = useState(30)
  const [deadlineLabel, setDeadlineLabel] = useState('')
  const [sheetOpen, setSheetOpen] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')

  const createPot = useCreatePot({
    mutation: {
      onSuccess: (response) => {
        void queryClient.invalidateQueries({ queryKey: getGetPotsQueryKey() })
        const potId = response.data?.potId
        navigate({ to: '/', search: potId ? { openPotId: potId } : {} })
      },
      onError: (error) => setErrorMessage(error.message),
    },
  })

  const setField = <K extends keyof FormState>(key: K, value: FormState[K]) => {
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
    createPot.mutate({
      data: {
        ...form,
        meetingPlace: meetingLocation.address,
        meetingRoadAddress: meetingLocation.roadAddress,
        meetingJibunAddress: meetingLocation.jibunAddress,
        latitude: meetingLocation.latitude,
        longitude: meetingLocation.longitude,
        // 최소 주문 금액은 생성 화면에서 받지 않는다. 기존 API/DB 계약이 요구하는 값은
        // "제한 없음"을 뜻하는 0으로 고정해 상세 조회와 기존 데이터 호환성을 유지한다.
        minOrderAmount: 0,
        deadline,
      },
    })
  }

  if (locationPickerOpen) {
    return (
      <AddressSetupStep
        onBack={() => setLocationPickerOpen(false)}
        onComplete={(location) => {
          setMeetingLocation(location)
          setLocationPickerOpen(false)
        }}
        confirmLabel="이 위치로 설정"
      />
    )
  }

  return (
    <main
      aria-label="새 배달팟 만들기"
      className="bg-bg mx-auto h-dvh max-w-[393px] overflow-hidden shadow-xl"
    >
      <div className="sheet-slide-up relative flex h-full flex-col">
        <header className="bg-bg relative z-20 flex shrink-0 items-center justify-center px-5 pt-[max(28px,env(safe-area-inset-top))] pb-4">
          <h1 className="text-lg font-bold">새 배달팟 만들기</h1>
          <button
            type="button"
            onClick={() => navigate({ to: '/' })}
            aria-label="닫기"
            className="absolute right-4 bottom-1 flex size-11 items-center justify-center rounded-full"
          >
            <X className="size-6" />
          </button>
        </header>

        <form
          id="new-pot-form"
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
            hint="배달앱 공유 버튼으로 복사한 문구를 그대로 붙여넣어도 링크만 들어가요"
          >
            <input
              required
              type="url"
              maxLength={500}
              value={form.storeUrl}
              onChange={(e) => setField('storeUrl', e.target.value)}
              onPaste={storeName.handlePaste}
              onBlur={(e) => storeName.handleBlur(e.target.value)}
              placeholder="배달 앱의 가게 링크를 복사해서 붙여넣어 주세요"
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
          <FormField label="만날 장소" hint="지도에서 배달을 받아 나눌 지점을 찍어주세요">
            <button
              type="button"
              onClick={() => setLocationPickerOpen(true)}
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
            <div className="bg-surface flex h-14 items-center justify-between rounded-xl px-3">
              <CounterButton
                label="인원 줄이기"
                disabled={form.capacity <= 2}
                onClick={() => setField('capacity', form.capacity - 1)}
              >
                <Minus className="size-5" />
              </CounterButton>
              <span className="font-bold">{form.capacity}명</span>
              <CounterButton
                label="인원 늘리기"
                disabled={form.capacity >= 4}
                onClick={() => setField('capacity', form.capacity + 1)}
              >
                <Plus className="size-5" />
              </CounterButton>
            </div>
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
          {errorMessage && (
            <p role="alert" className="text-down mt-4 text-sm">
              {errorMessage}
            </p>
          )}
        </form>

        <div className="bg-bg absolute inset-x-0 bottom-0 z-20 px-5 pt-3 pb-[max(24px,env(safe-area-inset-bottom))]">
          <button
            form="new-pot-form"
            type="submit"
            disabled={createPot.isPending}
            className="bg-primary text-primary-fg h-13 w-full rounded-xl text-sm font-bold disabled:opacity-50"
          >
            {createPot.isPending ? '만드는 중…' : '팟 만들기'}
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
              setDeadlineLabel(`${hours}시간${minutes ? ` ${minutes}분` : ''} 후`)
              setSheetOpen(false)
            }}
          />
        )}
      </div>
    </main>
  )
}

function FormField({
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
