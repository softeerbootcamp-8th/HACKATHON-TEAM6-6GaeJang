import type { ReactNode } from 'react'
import { createFileRoute, Link } from '@tanstack/react-router'

import { useMe } from '@/api/generated/auth/auth'
import { useGetMyRooms } from '@/api/generated/chat/chat'
import { requireAuth } from '@/lib/authGuard'

import { MobileBottomNav } from '../-components/MobileBottomNav'
import { ChatRoomListItem } from './-components/ChatRoomListItem'

export const Route = createFileRoute('/chat/')({
  beforeLoad: ({ context }) => requireAuth(context.queryClient),
  component: ChatRoomListPage,
})

function ChatRoomListPage() {
  // 401(미인증)은 react-query에선 에러다. AuthStatus와 동일하게 재시도하지 않는다.
  const me = useMe({ query: { retry: false } })
  const member = me.isError ? undefined : me.data?.data

  const rooms = useGetMyRooms({ query: { enabled: !!member } })

  return (
    <main
      aria-label="채팅방 목록"
      className="bg-bg mx-auto h-dvh max-w-[393px] overflow-hidden shadow-xl"
    >
      <div className="relative h-full">
        <div className="h-full overflow-y-auto px-5 pt-[max(28px,env(safe-area-inset-top))] pb-32">
          <header className="bg-bg sticky top-0 z-20 -mx-5 px-5 pb-4">
            <h1 className="text-lg font-bold">채팅</h1>
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
                  <ChatRoomListItem room={room} />
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
