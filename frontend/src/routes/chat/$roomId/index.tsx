import { useEffect, useRef, useState } from 'react'
import { createFileRoute, Link, useParams } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { ArrowLeft } from 'lucide-react'

import { useMe } from '@/api/generated/auth/auth'
import { getGetMyRoomsQueryKey, useGetMessages, useMarkRead } from '@/api/generated/chat/chat'
import type { ApiResponseListChatRoomSummaryResponse, ChatMessageResponse } from '@/api/generated/model'
import { requireAuth } from '@/lib/authGuard'

import { MessageBubble } from './-components/MessageBubble'
import { MessageComposer } from './-components/MessageComposer'
import { useChatSocket } from './-hooks/useChatSocket'

export const Route = createFileRoute('/chat/$roomId/')({
  beforeLoad: ({ context }) => requireAuth(context.queryClient),
  component: ChatRoomPage,
})

function ChatRoomPage() {
  const { roomId: roomIdParam } = useParams({ from: '/chat/$roomId/' })
  const roomId = Number(roomIdParam)

  const queryClient = useQueryClient()
  const me = useMe({ query: { retry: false } })
  const member = me.isError ? undefined : me.data?.data

  const history = useGetMessages(
    roomId,
    { size: 50 },
    { query: { enabled: !!member } },
  )

  const markRead = useMarkRead({
    mutation: {
      onSuccess: () => queryClient.invalidateQueries({ queryKey: getGetMyRoomsQueryKey() }),
    },
  })

  const [liveMessages, setLiveMessages] = useState<ChatMessageResponse[]>([])
  const bottomRef = useRef<HTMLDivElement>(null)

  const { connected, error, sendMessage } = useChatSocket(roomId, (message) => {
    setLiveMessages((prev) => [...prev, message])
    markRead.mutate({ roomId })
  })

  // 방에 들어오면(이력 로드 완료 시점) 최신 메시지까지 읽음 처리한다.
  useEffect(() => {
    if (member && history.isSuccess) {
      markRead.mutate({ roomId })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomId, member, history.isSuccess])

  const historyMessages = [...(history.data?.data?.messages ?? [])].reverse()
  const messages = [...historyMessages, ...liveMessages]

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'end' })
  }, [messages.length])

  const roomName = useRoomName(roomId) ?? `채팅방 ${roomId}`

  if (me.isPending) {
    return (
      <main aria-label="채팅방" className="mx-auto max-w-md px-4 py-6">
        <p className="text-muted-fg text-sm">로그인 상태 확인 중…</p>
      </main>
    )
  }

  if (!member) {
    return (
      <main aria-label="채팅방" className="mx-auto max-w-md px-4 py-6">
        <p className="text-muted-fg text-sm">
          로그인이 필요해요.{' '}
          <Link to="/login" className="text-primary underline">
            로그인
          </Link>
        </p>
      </main>
    )
  }

  const groups = groupMessagesByDate(messages)

  return (
    <main
      aria-label={roomName}
      className="bg-bg mx-auto flex h-dvh max-w-[393px] flex-col overflow-hidden shadow-xl"
    >
      <header className="flex shrink-0 items-center gap-3 px-4 pt-[max(20px,env(safe-area-inset-top))] pb-4">
        <Link to="/chat" aria-label="채팅 목록으로" className="flex size-6 items-center justify-center">
          <ArrowLeft className="size-6" />
        </Link>
        <h1 className="truncate text-base font-semibold">{roomName}</h1>
      </header>

      {error && (
        <p role="alert" className="text-down bg-muted px-4 py-2 text-center text-xs">
          {error}
        </p>
      )}

      <div className="min-h-0 flex-1 space-y-4 overflow-y-auto px-4 py-3">
        {history.isPending ? (
          <p className="text-muted-fg text-center text-sm">불러오는 중…</p>
        ) : history.isError ? (
          <p role="alert" className="text-down text-center text-sm">
            {history.error.message}
          </p>
        ) : (
          groups.map((group) => (
            <div key={group.label} className="space-y-2">
              <p className="text-muted-fg py-1 text-center text-xs">{group.label}</p>
              {group.messages.map((message) => (
                <MessageBubble
                  key={message.id}
                  message={message}
                  isMine={message.senderId === member.id}
                />
              ))}
            </div>
          ))
        )}
        <div ref={bottomRef} />
      </div>

      <MessageComposer disabled={!connected} onSend={sendMessage} />
    </main>
  )
}

function groupMessagesByDate(messages: ChatMessageResponse[]) {
  const today = new Date().toDateString()
  const groups: { label: string; messages: ChatMessageResponse[] }[] = []

  for (const message of messages) {
    if (!message.createdAt) continue
    const date = new Date(message.createdAt)
    const label =
      date.toDateString() === today
        ? '오늘'
        : date.toLocaleDateString('ko-KR', { month: 'long', day: 'numeric' })

    const lastGroup = groups[groups.length - 1]
    if (lastGroup?.label === label) lastGroup.messages.push(message)
    else groups.push({ label, messages: [message] })
  }

  return groups
}

/** getMyRooms 캐시에서 방 이름을 찾는다 — 별도 "방 상세 조회" API가 없어 목록 캐시를 재사용한다. */
function useRoomName(roomId: number): string | undefined {
  const queryClient = useQueryClient()
  const rooms = queryClient.getQueryData<ApiResponseListChatRoomSummaryResponse>(
    getGetMyRoomsQueryKey(),
  )
  return rooms?.data?.find((room) => room.roomId === roomId)?.name
}
