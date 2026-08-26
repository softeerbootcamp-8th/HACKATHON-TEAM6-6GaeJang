import type { ReactNode } from 'react'
import { createFileRoute, Link } from '@tanstack/react-router'
import { useQueries, useQueryClient } from '@tanstack/react-query'

import { useMe } from '@/api/generated/auth/auth'
import { getGetMyRoomsQueryKey, useGetMyRooms } from '@/api/generated/chat/chat'
import { PotDetailResponseStatus } from '@/api/generated/model'
import { getGetPotByChatRoomQueryOptions } from '@/api/generated/pot/pot'
import { requireAuth } from '@/lib/authGuard'

import { AppLogoHeader } from '../-components/AppLogoHeader'
import { MobileBottomNav } from '../-components/MobileBottomNav'
import { ChatRoomListItem } from './-components/ChatRoomListItem'
import { useChatRoomsSocket } from './-hooks/useChatRoomsSocket'

export const Route = createFileRoute('/chat/')({
  beforeLoad: ({ context }) => requireAuth(context.queryClient),
  component: ChatRoomListPage,
})

function ChatRoomListPage() {
  // 401(미인증)은 react-query에선 에러다. AuthStatus와 동일하게 재시도하지 않는다.
  const me = useMe({ query: { retry: false } })
  const member = me.isError ? undefined : me.data?.data

  const queryClient = useQueryClient()
  const rooms = useGetMyRooms({ query: { enabled: !!member } })
  const roomIds = rooms.data?.data?.map((room) => room.roomId).filter((id): id is number => id != null) ?? []

  // 목록 화면에 머무는 동안 어느 방이든 새 메시지가 오면 목록을 다시 받아온다.
  // 방마다 안읽음 개수·마지막 메시지 미리보기를 프론트가 직접 계산하지 않고 서버 응답을 그대로 신뢰한다.
  useChatRoomsSocket(roomIds, () => {
    queryClient.invalidateQueries({ queryKey: getGetMyRoomsQueryKey() })
  })

  // 방마다 연결된 배달팟의 종료 여부를 알아야 리스트를 옅게 표시할 수 있다.
  // 목록 API(GET /api/pots)는 DONE 상태를 아예 안 돌려주므로(문서화된 정책) 방별로 역조회한다.
  const potQueries = useQueries({
    queries: roomIds.map((roomId) => ({
      ...getGetPotByChatRoomQueryOptions(roomId, { query: { retry: false } }),
    })),
  })
  const doneRoomIds = new Set(
    roomIds.filter((_, index) => potQueries[index]?.data?.data?.status === PotDetailResponseStatus.DONE),
  )

  return (
    <main aria-label="채팅방 목록" className="app-shell">
      <div className="relative h-full">
        <div className="h-full overflow-y-auto overscroll-y-contain px-5 pb-32">
          <header className="bg-bg sticky top-0 z-20 -mx-5 px-5 pb-2">
            <AppLogoHeader />
            <div className="flex h-14 items-center">
              <h1 className="text-lg font-bold">채팅</h1>
            </div>
          </header>

          {me.isPending ? (
            <StateMessage>로그인 상태 확인 중…</StateMessage>
          ) : !member ? (
            <StateMessage>
              로그인이 필요해요.
              <Link to="/login" className="text-primary ml-1 underline">
                로그인하기
              </Link>
            </StateMessage>
          ) : rooms.isPending ? (
            <StateMessage>불러오는 중…</StateMessage>
          ) : rooms.isError ? (
            <p role="alert" className="text-down mt-24 text-center text-sm">
              {rooms.error.message}
            </p>
          ) : rooms.data?.data?.length === 0 ? (
            <StateMessage>참여 중인 채팅방이 없어요</StateMessage>
          ) : (
            <ul className="mt-2 flex flex-col">
              {rooms.data?.data?.map((room) => (
                <li key={room.roomId}>
                  <ChatRoomListItem room={room} isDone={room.roomId != null && doneRoomIds.has(room.roomId)} />
                </li>
              ))}
            </ul>
          )}
        </div>

        <MobileBottomNav active="chat" />
      </div>
    </main>
  )
}

function StateMessage({ children }: { children: ReactNode }) {
  return <p className="text-muted-fg mt-28 text-center text-sm leading-6">{children}</p>
}
