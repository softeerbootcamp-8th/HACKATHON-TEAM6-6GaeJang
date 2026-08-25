import { useState, type ReactNode } from 'react'
import { createFileRoute, Link } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { ChevronDown, Plus, Search } from 'lucide-react'

import { useMe } from '@/api/generated/auth/auth'
import { getGetPotsQueryKey, useCompletePot, useGetPots } from '@/api/generated/pot/pot'
import type { PotSummaryResponse } from '@/api/generated/model'
import { requireAuth } from '@/lib/authGuard'

import { MobileBottomNav } from './-components/MobileBottomNav'
import { PotCard } from './-components/PotCard'

export const Route = createFileRoute('/')({
  beforeLoad: ({ context }) => requireAuth(context.queryClient),
  component: HomePage,
})

function HomePage() {
  const [keyword, setKeyword] = useState('')
  const [actionError, setActionError] = useState('')
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

  const data = pots.data?.data
  const normalizedKeyword = keyword.trim().toLocaleLowerCase('ko-KR')
  const filterPots = (items?: PotSummaryResponse[]) =>
    (items ?? []).filter((pot) =>
      [pot.storeName, pot.title].some((value) =>
        value?.toLocaleLowerCase('ko-KR').includes(normalizedKeyword),
      ),
    )
  const hosted = filterPots(data?.hosted)
  const joined = filterPots(data?.joined)
  const all = filterPots(data?.all)

  return (
    <main
      aria-label="배달팟 홈"
      className="bg-bg mx-auto h-dvh max-w-[393px] overflow-hidden shadow-xl"
    >
      <div className="relative h-full">
        <div className="h-full overflow-y-auto px-5 pt-[max(28px,env(safe-area-inset-top))] pb-32">
          <header className="bg-bg sticky top-0 z-20 -mx-5 px-5 pb-4">
            <button type="button" className="flex items-center gap-2 py-2 text-[15px] font-bold">
              {member?.address || member?.roadAddress || '주소를 설정해주세요'}
              <ChevronDown className="fill-fg size-4" />
            </button>
            <label className="bg-surface text-muted-fg mt-3 flex h-12 items-center gap-3 rounded-xl px-3">
              <Search className="size-5" />
              <input
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
                placeholder="지금 먹고 싶은 음식이 있나요?"
                aria-label="가게 검색"
                className="text-fg placeholder:text-muted-fg w-full bg-transparent text-[15px] outline-none"
              />
            </label>
            <p className="text-muted-fg mt-6 text-center text-xs">내 주변 300m 내의 배달팟이에요</p>
          </header>

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
            <div className="mt-6 space-y-9">
              {actionError && (
                <p role="alert" className="text-down text-sm">
                  {actionError}
                </p>
              )}
              <PotSection
                title="내가 연 배달팟"
                items={hosted}
                onComplete={(potId) => complete.mutate({ potId })}
                completingId={complete.variables?.potId}
              />
              <PotSection title="참여중인 배달팟" items={joined} />
              <PotSection title="전체 배달팟" items={all} />
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
      </div>
    </main>
  )
}

type PotSectionProps = {
  title: string
  items: PotSummaryResponse[]
  onComplete?: (potId: number) => void
  completingId?: number
}

function PotSection({ title, items, onComplete, completingId }: PotSectionProps) {
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
