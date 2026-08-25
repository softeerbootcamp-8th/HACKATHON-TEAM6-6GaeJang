import { useState, type FormEvent, type ReactNode } from 'react'
import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { ChevronDown, Minus, Plus, X } from 'lucide-react'

import { useMe } from '@/api/generated/auth/auth'
import { useCreatePot } from '@/api/generated/pot/pot'
import { formatAccountNumber } from '@/lib/accountNumberFormatter'
import { requireAuth } from '@/lib/authGuard'
import type { PotCreateRequest } from '@/api/generated/model'

import { DeadlineSheet } from './-components/DeadlineSheet'

export const Route = createFileRoute('/pots/new/')({
  beforeLoad: ({ context }) => requireAuth(context.queryClient),
  component: NewPotPage,
})

type FormState = Omit<PotCreateRequest, 'latitude' | 'longitude' | 'deadline'>

const emptyForm: FormState = {
  title: '',
  storeName: '',
  storeUrl: '',
  meetingPlace: '',
  capacity: 2,
  minOrderAmount: 0,
  description: '',
  bankName: '',
  accountNumber: '',
  accountHolder: '',
}

function NewPotPage() {
  const navigate = useNavigate()
  const me = useMe({ query: { retry: false } })
  const member = me.data?.data
  const [form, setForm] = useState(emptyForm)
  const [hours, setHours] = useState(8)
  const [minutes, setMinutes] = useState(0)
  const [deadlineLabel, setDeadlineLabel] = useState('')
  const [sheetOpen, setSheetOpen] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')

  const createPot = useCreatePot({
    mutation: {
      onSuccess: (response) =>
        response.data?.potId
          ? navigate({ to: '/pots/$potId', params: { potId: String(response.data.potId) } })
          : navigate({ to: '/' }),
      onError: (error) => setErrorMessage(error.message),
    },
  })

  const setField = <K extends keyof FormState>(key: K, value: FormState[K]) => {
    setForm((current) => ({ ...current, [key]: value }))
  }

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    setErrorMessage('')
    if (member?.latitude == null || member.longitude == null) {
      setErrorMessage('팟을 만들기 전에 내 주소를 설정해주세요.')
      return
    }
    if (!deadlineLabel) {
      setErrorMessage('마감 시간을 선택해주세요.')
      return
    }
    const deadline = new Date(Date.now() + (hours * 60 + minutes) * 60_000).toISOString()
    createPot.mutate({
      data: {
        ...form,
        meetingPlace: form.meetingPlace || member.address || member.roadAddress || '',
        latitude: member.latitude,
        longitude: member.longitude,
        deadline,
      },
    })
  }

  return (
    <main
      aria-label="새 배달팟 만들기"
      className="bg-bg mx-auto h-dvh max-w-[393px] overflow-hidden shadow-xl"
    >
      <div className="sheet-slide-up relative flex h-full flex-col">
        <header className="bg-bg z-20 flex shrink-0 items-center justify-center px-5 pt-[max(28px,env(safe-area-inset-top))] pb-4">
          <h1 className="text-lg font-bold">새 배달팟 만들기</h1>
          <button
            type="button"
            onClick={() => navigate({ to: '/' })}
            aria-label="닫기"
            className="absolute right-4 bottom-2 flex size-11 items-center justify-center rounded-full"
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
              onChange={(e) => setField('title', e.target.value)}
              placeholder="제목을 입력해주세요"
              className="form-control"
            />
          </FormField>
          <FormField label="가게" hint="배달앱 가게 페이지 링크를 복사해서 붙여넣어 주세요">
            <input
              required
              type="url"
              maxLength={500}
              value={form.storeUrl}
              onChange={(e) => setField('storeUrl', e.target.value)}
              placeholder="링크를 복사해서 붙여넣어 주세요"
              className="form-control"
            />
          </FormField>
          <FormField label="가게명">
            <input
              required
              maxLength={100}
              value={form.storeName}
              onChange={(e) => setField('storeName', e.target.value)}
              placeholder="가게명을 입력해주세요"
              className="form-control"
            />
          </FormField>
          <FormField label="만날 장소">
            <input
              required
              maxLength={200}
              value={form.meetingPlace || member?.address || member?.roadAddress || ''}
              onChange={(e) => setField('meetingPlace', e.target.value)}
              className="form-control"
            />
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
                disabled={form.capacity >= 20}
                onClick={() => setField('capacity', form.capacity + 1)}
              >
                <Plus className="size-5" />
              </CounterButton>
            </div>
          </FormField>
          <FormField label="가게 최소 주문 금액">
            <div className="relative">
              <input
                required
                min={0}
                type="number"
                value={form.minOrderAmount || ''}
                onChange={(e) => setField('minOrderAmount', Number(e.target.value))}
                placeholder="최소 주문 금액을 입력해주세요"
                className="form-control pr-10"
              />
              <span className="text-muted-fg absolute top-1/2 right-4 -translate-y-1/2 text-sm">
                원
              </span>
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
              onChange={(e) => setField('description', e.target.value)}
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
              onChange={(e) => setField('accountNumber', formatAccountNumber(form.bankName, e.target.value))}
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
