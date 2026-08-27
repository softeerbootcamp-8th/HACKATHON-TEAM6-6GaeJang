import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { createFileRoute, Link, redirect, useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { ChevronDown, Plus, Search, X } from 'lucide-react'

import type { PotSummaryResponse } from '@/api/generated/model'
import { getMeQueryKey, useMe, useUpdateProfile } from '@/api/generated/auth/auth'
import {
  getGetPotQueryKey,
  getGetPotsQueryKey,
  useCompletePot,
  useGetPots,
} from '@/api/generated/pot/pot'
import { formatLocalAddress } from '@/lib/addressFormatter'
import { requireAuth } from '@/lib/authGuard'

import { useDebouncedValue } from './-hooks/useDebouncedValue'
import { AddressSetupStep } from './-components/address/AddressSetupStep'
import type { SelectedLocation } from './-components/address/KakaoMapPicker'
import { AppLogoHeader } from './-components/AppLogoHeader'
import { MobileBottomNav } from './-components/MobileBottomNav'
import { PotCard } from './-components/PotCard'
import { PotDetailSheet } from './-components/PotDetailSheet'
import { PullToRefreshIndicator, usePullToRefresh } from './-components/PullToRefresh'

export const Route = createFileRoute('/')({
  beforeLoad: async ({ context, search }) => {
    await requireAuth(context.queryClient)
    if (search.openPotID && !search.openPotId) {
      throw redirect({
        to: '/',
        search: { openPotId: search.openPotID },
        replace: true,
      })
    }
  },
  validateSearch: (
    search: Record<string, unknown>,
  ): { openPotId?: number; openPotID?: number; revealPotId?: number } => {
    const openPotId = Number(search.openPotId)
    const legacyOpenPotId = Number(search.openPotID)
    const revealPotId = Number(search.revealPotId)

    if (Number.isSafeInteger(openPotId) && openPotId > 0) return { openPotId }
    if (Number.isSafeInteger(legacyOpenPotId) && legacyOpenPotId > 0)
      return { openPotID: legacyOpenPotId }
    if (Number.isSafeInteger(revealPotId) && revealPotId > 0) return { revealPotId }
    return {}
  },
  component: HomePage,
})

function HomePage() {
  const navigate = useNavigate()
  const { openPotId: canonicalOpenPotId, openPotID, revealPotId } = Route.useSearch()
  const openPotId = canonicalOpenPotId ?? openPotID
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
  const refreshPage = useCallback(
    () => queryClient.refetchQueries({ type: 'active' }),
    [queryClient],
  )
  const {
    scrollRef: pullToRefreshRef,
    pullDistance,
    isRefreshing,
  } = usePullToRefresh({ onRefresh: refreshPage })
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
        // 남이 연 팟·참여 현황은 소켓 신호가 없어서 화면에 머무는 동안 갱신될 길이 없다.
        // 탭이 백그라운드면 타이머가 자동으로 멈추므로(refetchIntervalInBackground 기본 false)
        // 실제 요청은 사용자가 홈을 보고 있는 동안에만 나간다.
        refetchInterval: 15_000,
      },
    },
  )
  const complete = useCompletePot({
    mutation: {
      onSuccess: async (_, { potId }) => {
        await Promise.all([
          // 완료 팟 제거와 서버가 확정한 총대 횟수를 같은 성공 흐름에서 다시 받는다.
          queryClient.invalidateQueries({ queryKey: getGetPotsQueryKey() }),
          queryClient.invalidateQueries({ queryKey: getMeQueryKey() }),
          queryClient.invalidateQueries({ queryKey: getGetPotQueryKey(potId) }),
          // 채팅방 역조회·메시지는 roomId를 여기서 모르므로 관련 경로를 모두 stale 처리한다.
          // 현재 화면에 있으면 즉시 refetch되고, 캐시에만 있으면 다음 진입 때 새로 조회된다.
          queryClient.invalidateQueries({
            predicate: ({ queryKey }) => {
              const path = queryKey[0]
              return (
                typeof path === 'string' &&
                (path.startsWith('/api/pots/by-chat-room/') ||
                  path.startsWith('/api/chat/rooms'))
              )
            },
          }),
        ])
      },
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

  const data = pots.data?.data
  const hosted = data?.hosted ?? []
  const joined = data?.joined ?? []
  const all = data?.all ?? []

  // 저장 직후 갱신된 카드를 300ms 동안 보여준 다음 상세 시트를 연다. 새 카드가 목록에 생겼다는
  // 맥락을 인지할 수 있으면서도 다른 조작을 시작하기에는 짧은 간격이다.
  const revealPotIsVisible = hosted.some((pot) => pot.potId === revealPotId)
  useEffect(() => {
    if (!revealPotId || pots.isFetching || !revealPotIsVisible) return

    const timer = window.setTimeout(() => {
      void navigate({ to: '/', search: { openPotId: revealPotId }, replace: true })
    }, 300)

    return () => window.clearTimeout(timer)
  }, [navigate, pots.isFetching, revealPotId, revealPotIsVisible])

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

  return (
    <main aria-label="배달팟 홈" className="app-shell">
      <div className="relative flex h-full flex-col">
        <header className="bg-bg z-20 shrink-0 px-5 pb-2">
          <AppLogoHeader compact />
          <button
            type="button"
            onClick={openAddressPicker}
            aria-label="내 위치 변경"
            className="flex items-center gap-2 py-2 text-[15px] font-bold"
          >
            {formatLocalAddress(member?.roadAddress || member?.address || member?.jibunAddress) ||
              '주소를 설정해주세요'}
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

        <div
          ref={pullToRefreshRef}
          className="min-h-0 flex-1 overflow-y-auto overscroll-y-contain px-5 pb-32"
        >
          <PullToRefreshIndicator pullDistance={pullDistance} isRefreshing={isRefreshing} />
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
            onClose={() =>
              navigate({
                to: '/',
                search: () => ({}),
                replace: true,
              })
            }
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
