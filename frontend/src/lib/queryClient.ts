import { QueryClient } from '@tanstack/react-query'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
      // 모바일에서는 앱을 자주 들락거린다(특히 가게 링크로 배달앱에 갔다 오는 흐름).
      // 복귀 시점에 자동으로 최신화한다. staleTime을 존중하므로 짧은 간격의 포커스 전환이
      // 반복돼도 요청은 30초에 한 번을 넘지 않는다.
      refetchOnWindowFocus: true,
    },
  },
})
