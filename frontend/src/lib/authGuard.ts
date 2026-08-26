import type { QueryClient } from '@tanstack/react-query'
import { redirect } from '@tanstack/react-router'

import { getMeQueryKey, getMeQueryOptions } from '@/api/generated/auth/auth'

/**
 * /api/auth/me 로 로그인 여부를 판단한다. 401은 에러로 오므로 미인증으로 간주한다.
 * react-query 는 에러 이후에도 직전 성공 데이터를 캐시에 남겨두므로, ensureQueryData 가
 * 캐시된 값을 그냥 반환해버려도 실제 쿼리 상태가 error 면 로그아웃(미인증)으로 취급한다.
 */
async function isAuthenticated(queryClient: QueryClient): Promise<boolean> {
  try {
    await queryClient.ensureQueryData(getMeQueryOptions({ query: { retry: false } }))
  } catch {
    return false
  }
  return queryClient.getQueryState(getMeQueryKey())?.status !== 'error'
}

/** 비로그인 상태면 /login 으로 보낸다. 보호 라우트의 beforeLoad 에서 사용. */
export async function requireAuth(queryClient: QueryClient) {
  if (!(await isAuthenticated(queryClient))) {
    throw redirect({ to: '/login' })
  }
}

/** 로그인 상태면 / 로 보낸다. /login, /onboarding 처럼 게스트 전용 라우트에서 사용. */
export async function redirectIfAuthenticated(queryClient: QueryClient) {
  if (await isAuthenticated(queryClient)) {
    throw redirect({ to: '/' })
  }
}
