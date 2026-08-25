import { Link } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'

import { getMeQueryKey, useLogout, useMe } from '@/api/generated/auth/auth'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'

/** 홈 상단 로그인 상태. 미인증(401)은 정상 상태라 재시도하지 않는다. */
export function AuthStatus() {
  const queryClient = useQueryClient()
  const me = useMe({ query: { retry: false } })

  const logout = useLogout({
    mutation: {
      onSuccess: () => queryClient.invalidateQueries({ queryKey: getMeQueryKey() }),
    },
  })

  // 401(미인증)은 react-query 에선 에러다. 에러 시 직전 성공 데이터가 남으므로,
  // 에러 상태면 로그아웃으로 간주해 stale 한 member 를 무시한다.
  const member = me.isError ? undefined : me.data?.data

  return (
    <Card>
      <CardContent className="flex items-center justify-between gap-4 py-4">
        {me.isPending ? (
          <span className="text-muted-fg text-sm">로그인 상태 확인 중…</span>
        ) : member ? (
          <>
            <span className="text-sm">
              <b className="font-semibold">{member.nickname}</b> 님, 안녕하세요.
            </span>
            <Button
              variant="outline"
              size="sm"
              onClick={() => logout.mutate()}
              disabled={logout.isPending}
            >
              로그아웃
            </Button>
          </>
        ) : (
          <>
            <span className="text-muted-fg text-sm">로그인이 필요해요.</span>
            <div className="flex gap-2">
              <Link to="/login" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
                로그인
              </Link>
              <Link to="/onboarding" className={buttonVariants({ size: 'sm' })}>
                가입하기
              </Link>
            </div>
          </>
        )}
      </CardContent>
    </Card>
  )
}
