import { useEffect, useState, type ReactNode } from 'react'
import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { ChevronDown, Plus, Search, X } from 'lucide-react'

import type { PotSummaryResponse } from '@/api/generated/model'
import { getMeQueryKey, useMe, useUpdateProfile } from '@/api/generated/auth/auth'
import { getGetPotsQueryKey, useCompletePot, useGetPots } from '@/api/generated/pot/pot'
import { formatLocalAddress } from '@/lib/addressFormatter'
import { requireAuth } from '@/lib/authGuard'

import { useDebouncedValue } from './-hooks/useDebouncedValue'
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
  const [isComposingKeyword, setIsComposingKeyword] = useState(false)
  const [actionError, setActionError] = useState('')
  const [addressPickerOpen, setAddressPickerOpen] = useState(false)

  // 주소 선택 화면은 URL이 안 바뀌어 브라우저 히스토리에 항목이 안 쌓인다. 그대로 두면
  // 뒤로가기를 눌렀을 때 이 화면이 아니라 홈 이전 페이지로 튕긴다. pushState로 항목을
  // 하나 쌓고 popstate에서 다시 닫아 맞춘다.
  const openAddressPicker = () => {
    window.history.pushState({ addressPickerOpen: true }, '')
    setAddressPickerOpen(true)
  }

  useEffect(() => {
    // AddressSetupStep이 지도 화면용으로 항목을 하나 더 쌓을 수 있으니, 무조건 닫지 않고
    // popstate가 남긴 state를 보고 판단한다 — 지도→검색처럼 이 단계 안쪽으로 돌아온 경우까지
    // 홈으로 튕겨버리면 안 된다.
    const handlePopState = (event: PopStateEvent) => {
      setAddressPickerOpen(event.state?.addressPickerOpen === true)
    }
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  const queryClient = useQueryClient()
  const me = useMe({ query: { retry: false } })
  const member = me.isError ? undefined : me.data?.data
  const debouncedKeyword = useDebouncedValue(keyword.trim(), 300, !isComposingKeyword)
  const effectiveKeyword = keyword.trim() ? debouncedKeyword : ''
  const pots = useGetPots(
    { keyword: effectiveKeyword || undefined },
    {
      query: {
        enabled: !!member,
        placeholderData: (previousData) => previousData,
      },
    },
  )
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
        // onComplete(handleAddressComplete)는 항상 AddressSetupStep의 지도 화면에서만 불린다 —
        // 그 화면에 오려면 주소 선택 단계(1) + 지도 화면(1), 총 두 단계를 눌러 왔으므로
        // 두 칸을 한 번에 되돌린다.
        window.history.go(-2)
      },
      onError: (error) => {
        setActionError(error.message)
        window.history.go(-2)
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
        onBack={() => window.history.back()}
        onComplete={handleAddressComplete}
        isSubmitting={saveAddress.isPending}
        confirmLabel="이 위치로 설정"
        submittingLabel="저장 중…"
      />
    )
  }

  const data = pots.data?.data
  const hosted = data?.hosted ?? []
  const joined = data?.joined ?? []
  const all = data?.all ?? []

  return (
    <main aria-label="배달팟 홈" className="app-shell">
      <div className="relative flex h-full flex-col">
        <header className="bg-bg z-20 shrink-0 px-5 pb-2">
          <button
            type="button"
            onClick={openAddressPicker}
            aria-label="내 위치 변경"
            className="flex items-center gap-2 py-2 text-[15px] font-bold"
          >
            {formatLocalAddress(
              member?.roadAddress || member?.address || member?.jibunAddress,
            ) || '주소를 설정해주세요'}
            <ChevronDown className="fill-fg size-4" />
          </button>
          <div className="bg-surface text-muted-fg mt-2 flex h-12 items-center gap-3 rounded-xl px-3">
            <Search className="size-5" />
            <input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              onCompositionStart={() => setIsComposingKeyword(true)}
              onCompositionEnd={(event) => {
                setKeyword(event.currentTarget.value)
                setIsComposingKeyword(false)
              }}
              placeholder="지금 먹고 싶은 음식이 있나요?"
              aria-label="가게명 검색"
              maxLength={100}
              className="text-fg placeholder:text-muted-fg w-full bg-transparent text-base outline-none"
            />
            {keyword && (
              <button
                type="button"
                onClick={() => {
                  setKeyword('')
                  setIsComposingKeyword(false)
                }}
                aria-label="검색어 지우기"
                className="hover:bg-muted hover:text-fg flex size-8 shrink-0 items-center justify-center rounded-full transition-colors"
              >
                <X className="size-4" />
              </button>
            )}
          </div>
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
                  {effectiveKeyword ? (
                    '검색 결과가 없어요'
                  ) : (
                    <>
                      현재 진행중인 배달팟이 없어요
                      <br />
                      새로운 배달팟을 열어보세요!
                    </>
                  )}
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
