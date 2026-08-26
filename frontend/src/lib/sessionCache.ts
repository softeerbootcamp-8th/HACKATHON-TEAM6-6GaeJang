import type { QueryClient } from '@tanstack/react-query'

/**
 * 로그인 사용자가 바뀌는 경계에서 이전 사용자의 조회 결과를 제거한다.
 * 먼저 진행 중인 요청을 취소해야 늦게 도착한 이전 응답이 제거한 캐시를 다시 채우지 않는다.
 */
export async function clearSessionQueries(queryClient: QueryClient) {
  await queryClient.cancelQueries()
  queryClient.removeQueries()
}
