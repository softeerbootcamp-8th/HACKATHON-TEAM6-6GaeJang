import { useState, type ReactNode } from 'react'
import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { ChevronDown, Plus, Search } from 'lucide-react'

import { getMeQueryKey, useMe, useUpdateProfile } from '@/api/generated/auth/auth'
import { getGetPotsQueryKey, useCompletePot, useGetPots } from '@/api/generated/pot/pot'
import type { PotSummaryResponse } from '@/api/generated/model'
import { formatLocalAddress } from '@/lib/addressFormatter'
import { requireAuth } from '@/lib/authGuard'

import { AddressSetupStep } from './-components/address/AddressSetupStep'
import type { SelectedLocation } from './-components/address/KakaoMapPicker'
import { MobileBottomNav } from './-components/MobileBottomNav'
import { PotCard } from './-components/PotCard'
import { PotDetailSheet } from './-components/PotDetailSheet'

export const Route = createFileRoute('/')({
  beforeLoad: ({ context }) => requireAuth(context.queryClient),
  validateSearch: (search: Record<string, unknown>): { openPotId?: number } => {
    const openPotId = Number(search.openPotId)
    return Number.isSafeInteger(openPotId) && openPotId > 0 ? { openPotId } : {}
  },
  component: HomePage,
})

function HomePage() {
  const navigate = useNavigate()
  const { openPotId } = Route.useSearch()
  const [keyword, setKeyword] = useState('')
  const [actionError, setActionError] = useState('')
  const [addressPickerOpen, setAddressPickerOpen] = useState(false)
  const queryClient = useQueryClient()
  const me = useMe({ query: { retry: false } })
  const member = me.isError ? undefined : me.data?.data
  const pots = useGetPots({ request: {} }, { query: { enabled: !!member } })
  const complete = useCompletePot({
    mutation: {
      onSuccess: () => queryClient.invalidateQueries({ queryKey: getGetPotsQueryKey() }),
      onError: (error) => setActionError(error.message),
    },
  })

  // 주소를 바꾸면 회원 주소 자체가 바뀐다. 목록의 300m 반경 중심을 서버가 회원 좌표로 잡기
  // 때문에(PotService.findPots), 헤더에서 주소를 고치는 것이 곧 조회 위치를 옮기는 것이다.
  const saveAddress = useUpdateProfile({
    mutation: {
      onSuccess: async () => {
        await Promise.all([
          queryClient.invalidateQueries({ queryKey: getMeQueryKey() }),
          queryClient.invalidateQueries({ queryKey: getGetPotsQueryKey() }),
        ])
        setAddressPickerOpen(false)
      },
      onError: (error) => {
        setActionError(error.message)
        setAddressPickerOpen(false)
      },
    },
  })

  const handleAddressComplete = (location: SelectedLocation) => {
    saveAddress.mutate({
      data: {
        address: location.address,
        roadAddress: location.roadAddress,
        jibunAddress: location.jibunAddress,
        latitude: location.latitude,
        longitude: location.longitude,
      },
    })
  }

  if (addressPickerOpen) {
    return (
      <AddressSetupStep
        onBack={() => setAddressPickerOpen(false)}
        onComplete={handleAddressComplete}
        isSubmitting={saveAddress.isPending}
        confirmLabel="이 위치로 설정"
        submittingLabel="저장 중…"
      />
    )
  }

  const data = pots.data?.data
  const normalizedKeyword = keyword.trim().toLocaleLowerCase('ko-KR')
  const filterPots = (items?: PotSummaryResponse[]) =>
    (items ?? []).filter((pot) =>
      [pot.storeName, pot.title, pot.description].some((value) =>
        value?.toLocaleLowerCase('ko-KR').includes(normalizedKeyword),
      ),
    )
  const hosted = filterPots(data?.hosted)
  const joined = filterPots(data?.joined)
  const all = filterPots(data?.all)

  return (
    <main aria-label="배달팟 홈" className="app-shell">
      <div className="relative flex h-full flex-col">
        <header className="bg-bg z-20 shrink-0 px-5 pt-[max(28px,env(safe-area-inset-top))] pb-2">
          <button
            type="button"
            onClick={() => setAddressPickerOpen(true)}
            aria-label="내 위치 변경"
            className="flex items-center gap-2 py-2 text-[15px] font-bold"
          >
            {formatLocalAddress(
              member?.roadAddress || member?.address || member?.jibunAddress,
            ) || '주소를 설정해주세요'}
            <ChevronDown className="fill-fg size-4" />
          </button>
          <label className="bg-surface text-muted-fg mt-2 flex h-12 items-center gap-3 rounded-xl px-3">
            <Search className="size-5" />
            <input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="지금 먹고 싶은 음식이 있나요?"
              aria-label="가게 검색"
              maxLength={100}
              className="text-fg placeholder:text-muted-fg w-full bg-transparent text-base outline-none"
            />
          </label>
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto overscroll-y-contain px-5 pb-32">
          <p className="text-muted-fg pt-3 text-center text-xs">내 주변 300m 내의 배달팟이에요</p>

          {!member && !me.isPending ? (
            <StateMessage>
              로그인이 필요해요.
              <Link to="/login" className="text-primary ml-1 underline">
                로그인하기
              </Link>
            </StateMessage>
          ) : me.isPending || pots.isPending ? (
            <StateMessage>배달팟을 불러오는 중이에요…</StateMessage>
          ) : pots.isError ? (
            <p role="alert" className="text-down mt-24 text-center text-sm">
              {pots.error.message}
            </p>
          ) : (
            <div className="mt-4 space-y-7">
              {actionError && (
                <p role="alert" className="text-down text-sm">
                  {actionError}
                </p>
              )}
              <PotSection
                title="내가 연 배달팟"
                items={hosted}
                onOpen={(potId) => navigate({ to: '/', search: { openPotId: potId } })}
                onComplete={(potId) => complete.mutate({ potId })}
                completingId={complete.variables?.potId}
              />
              <PotSection
                title="참여중인 배달팟"
                items={joined}
                onOpen={(potId) => navigate({ to: '/', search: { openPotId: potId } })}
              />
              <PotSection
                title="전체 배달팟"
                items={all}
                onOpen={(potId) => navigate({ to: '/', search: { openPotId: potId } })}
              />
              {hosted.length + joined.length + all.length === 0 && (
                <StateMessage>
                  현재 진행중인 배달팟이 없어요
                  <br />
                  새로운 배달팟을 열어보세요!
                </StateMessage>
              )}
            </div>
          )}
        </div>

        {member && (
          <Link
            to="/pots/new"
            aria-label="새 배달팟 만들기"
            className="bg-primary text-primary-fg absolute right-5 bottom-[104px] z-30 flex size-14 items-center justify-center rounded-full shadow-lg"
          >
            <Plus className="size-8" />
          </Link>
        )}
        <MobileBottomNav active="home" />

        {openPotId != null && (
          <PotDetailSheet
            potId={openPotId}
            onClose={() => navigate({ to: '/', search: {}, replace: true })}
          />
        )}
      </div>
    </main>
  )
}

type PotSectionProps = {
  title: string
  items: PotSummaryResponse[]
  onOpen: (potId: number) => void
  onComplete?: (potId: number) => void
  completingId?: number
}

function PotSection({ title, items, onOpen, onComplete, completingId }: PotSectionProps) {
  if (items.length === 0) return null
  const headingId = `${title}-heading`
  return (
    <section aria-labelledby={headingId}>
      <h2 id={headingId} className="mb-3 text-sm font-bold">
        {title}
      </h2>
      <div className="space-y-3">
        {items.map((pot) => (
          <PotCard
            key={pot.potId}
            pot={pot}
            onOpen={onOpen}
            onComplete={onComplete}
            isCompleting={completingId === pot.potId}
          />
        ))}
      </div>
    </section>
  )
}

function StateMessage({ children }: { children: ReactNode }) {
  return <p className="text-muted-fg mt-28 text-center text-sm leading-6">{children}</p>
}
