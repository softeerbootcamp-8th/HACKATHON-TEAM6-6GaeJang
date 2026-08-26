import { useCallback, useState } from 'react'
import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { ChevronRight } from 'lucide-react'

import { useLogout, useMe, useWithdraw } from '@/api/generated/auth/auth'
import { requireAuth } from '@/lib/authGuard'
import { clearSessionQueries } from '@/lib/sessionCache'

import { AppLogoHeader } from '../-components/AppLogoHeader'
import { MobileBottomNav } from '../-components/MobileBottomNav'
import { PullToRefreshIndicator, usePullToRefresh } from '../-components/PullToRefresh'
import { ConfirmDialog } from './-components/ConfirmDialog'

export const Route = createFileRoute('/my/')({
  beforeLoad: ({ context }) => requireAuth(context.queryClient),
  component: MyPage,
})

const APP_VERSION = 'v1.0.0'

function MyPage() {
  const navigate = useNavigate()
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

  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false)
  const [showWithdrawConfirm, setShowWithdrawConfirm] = useState(false)
  const [withdrawError, setWithdrawError] = useState('')

  const goToLogin = async () => {
    await clearSessionQueries(queryClient)
    await navigate({ to: '/login' })
  }

  const logout = useLogout({
    mutation: { onSuccess: () => void goToLogin() },
  })

  const withdraw = useWithdraw({
    mutation: {
      onSuccess: () => void goToLogin(),
      onError: (error) => {
        setShowWithdrawConfirm(false)
        setWithdrawError(error.message)
      },
    },
  })

  return (
    <main aria-label="마이페이지" className="app-shell">
      <div className="relative h-full">
        <div
          ref={pullToRefreshRef}
          className="h-full overflow-y-auto overscroll-y-contain px-5 pb-32"
        >
          <header>
            <AppLogoHeader compact />
            <PullToRefreshIndicator pullDistance={pullDistance} isRefreshing={isRefreshing} />
            <div className="flex h-14 items-center">
              <h1 className="text-xl font-bold">마이페이지</h1>
            </div>
          </header>

          {me.isPending ? (
            <p className="text-muted-fg mt-6 text-sm">불러오는 중…</p>
          ) : !member ? (
            <p className="text-muted-fg mt-6 text-sm">
              로그인이 필요해요.{' '}
              <Link to="/login" className="text-primary underline">
                로그인
              </Link>
            </p>
          ) : (
            <>
              <div className="mt-4 flex items-center gap-3 rounded-2xl border px-4 py-4">
                <div className="bg-muted text-fg flex size-11 shrink-0 items-center justify-center rounded-full text-base font-bold">
                  {member.nickname?.[0] ?? '?'}
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-1.5">
                    <span className="truncate text-[15px] font-bold">{member.nickname}</span>
                    <span className="bg-primary/10 text-primary shrink-0 rounded-full px-2 py-0.5 text-[11px] font-semibold">
                      총대 {member.hostedPotCount ?? 0}회
                    </span>
                  </div>
                  <p className="text-muted-fg mt-0.5 text-xs leading-5 break-words">
                    {member.roadAddress || member.address || member.jibunAddress}
                  </p>
                </div>
              </div>

              <div className="bg-surface -mx-5 mt-6 h-2" />

              <nav aria-label="마이페이지 메뉴">
                <ul className="divide-border divide-y">
                  <li>
                    <Link
                      to="/my/edit"
                      className="flex items-center justify-between py-4 text-sm font-medium"
                    >
                      프로필 수정
                      <ChevronRight className="text-muted-fg size-4" />
                    </Link>
                  </li>
                  <li>
                    <span
                      aria-disabled="true"
                      className="text-muted-fg flex items-center justify-between py-4 text-sm font-medium"
                    >
                      신고하기
                      <ChevronRight className="text-muted-fg/50 size-4" />
                    </span>
                  </li>
                  <li>
                    <span
                      aria-disabled="true"
                      className="text-muted-fg flex items-center justify-between py-4 text-sm font-medium"
                    >
                      알림 설정
                      <ChevronRight className="text-muted-fg/50 size-4" />
                    </span>
                  </li>
                </ul>

                <div className="bg-surface -mx-5 h-2" />

                <ul className="divide-border divide-y">
                  <li>
                    <button
                      type="button"
                      onClick={() => setShowLogoutConfirm(true)}
                      className="flex w-full items-center justify-between py-4 text-left text-sm font-medium"
                    >
                      로그 아웃
                      <ChevronRight className="text-muted-fg size-4" />
                    </button>
                  </li>
                  <li>
                    <button
                      type="button"
                      onClick={() => setShowWithdrawConfirm(true)}
                      className="text-down flex w-full items-center justify-between py-4 text-left text-sm font-medium"
                    >
                      회원 탈퇴
                      <ChevronRight className="text-down/60 size-4" />
                    </button>
                  </li>
                </ul>
              </nav>

              <div className="bg-surface -mx-5 mb-6 h-2" />

              <div className="flex items-center justify-between text-sm">
                <span>앱 버전정보</span>
                <span className="text-muted-fg">{APP_VERSION}</span>
              </div>

              {withdrawError && (
                <p role="alert" className="text-down mt-6 text-sm">
                  {withdrawError}
                </p>
              )}
            </>
          )}
        </div>

        <MobileBottomNav active="my" />
      </div>

      <ConfirmDialog
        open={showLogoutConfirm}
        title="로그아웃 할까요?"
        cancelLabel="아니요"
        confirmLabel="예"
        onCancel={() => setShowLogoutConfirm(false)}
        onConfirm={() => logout.mutate()}
        isConfirming={logout.isPending}
      />

      <ConfirmDialog
        open={showWithdrawConfirm}
        title="탈퇴할까요?"
        description="참여 중인 팟은 자동으로 나가기 처리돼요."
        cancelLabel="아니요"
        confirmLabel="탈퇴"
        onCancel={() => setShowWithdrawConfirm(false)}
        onConfirm={() => {
          setWithdrawError('')
          withdraw.mutate()
        }}
        isConfirming={withdraw.isPending}
      />
    </main>
  )
}
