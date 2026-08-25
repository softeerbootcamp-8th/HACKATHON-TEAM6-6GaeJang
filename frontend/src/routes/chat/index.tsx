import { createFileRoute, Link } from '@tanstack/react-router'

import { useMe } from '@/api/generated/auth/auth'
import { useGetMyRooms } from '@/api/generated/chat/chat'
import { requireAuth } from '@/lib/authGuard'

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
    <main aria-label="채팅방 목록" className="mx-auto max-w-md px-4 py-6">
      <h1 className="text-xl font-semibold">채팅</h1>

      {me.isPending ? (
        <p className="text-muted-fg mt-6 text-sm">로그인 상태 확인 중…</p>
      ) : !member ? (
        <p className="text-muted-fg mt-6 text-sm">
          로그인이 필요해요.{' '}
          <Link to="/login" className="text-primary underline">
            로그인
          </Link>
        </p>
      ) : rooms.isPending ? (
        <p className="text-muted-fg mt-6 text-sm">불러오는 중…</p>
      ) : rooms.isError ? (
        <p role="alert" className="text-down mt-6 text-sm">
          {rooms.error.message}
        </p>
      ) : rooms.data?.data?.length === 0 ? (
        <p className="text-muted-fg mt-6 text-sm">참여 중인 채팅방이 없어요.</p>
      ) : (
        <ul className="mt-4 flex flex-col gap-2">
          {rooms.data?.data?.map((room) => (
            <li key={room.roomId}>
              <ChatRoomListItem room={room} />
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}
