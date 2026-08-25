import { Fragment, useEffect, useRef, useState } from 'react'
import { createFileRoute, Link, useParams } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'

import { useMe } from '@/api/generated/auth/auth'
import { getGetMyRoomsQueryKey, useGetMessages, useGetRoom, useMarkRead } from '@/api/generated/chat/chat'
import type { ChatMessageResponse } from '@/api/generated/model'

import { DateDivider } from './-components/DateDivider'
import { MessageBubble } from './-components/MessageBubble'
import { MessageComposer } from './-components/MessageComposer'
import { useChatSocket } from './-hooks/useChatSocket'
import { useUploadChatImage } from './-hooks/useUploadChatImage'

export const Route = createFileRoute('/chat/$roomId/')({
  component: ChatRoomPage,
})

function sameDay(aIso?: string, bIso?: string) {
  if (!aIso || !bIso) return false
  const a = new Date(aIso)
  const b = new Date(bIso)
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
}

function ChatRoomPage() {
  const { roomId: roomIdParam } = useParams({ from: '/chat/$roomId/' })
  const roomId = Number(roomIdParam)

  const queryClient = useQueryClient()
  const me = useMe({ query: { retry: false } })
  const member = me.isError ? undefined : me.data?.data

  const room = useGetRoom(roomId, { query: { enabled: !!member } })
  const history = useGetMessages(roomId, { size: 50 }, { query: { enabled: !!member } })

  const nicknameById = new Map(
    (room.data?.data?.members ?? []).map((m) => [m.memberId, m.nickname]),
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

  // 업로드가 성공하면 서버가 같은 /topic/rooms/{roomId}로 브로드캐스트하므로,
  // 여기서 직접 liveMessages에 추가하지 않는다(중복 렌더 방지) — useChatSocket의 onMessage가 받는다.
  const uploadImage = useUploadChatImage()
  const handleSendImage = (file: File) => {
    uploadImage.mutate({ roomId, file })
  }

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

  const roomName = room.data?.data?.name ?? `채팅방 ${roomId}`
  const memberCount = room.data?.data?.memberCount
  const location = room.data?.data?.location
  const subtitle = [memberCount != null ? `멤버 ${memberCount}명` : null, location].filter(Boolean).join(' · ')

  return (
    <main aria-label={roomName} className="mx-auto flex h-[calc(100dvh-57px)] max-w-md flex-col">
      <header className="flex items-center gap-2 border-b px-4 py-3">
        <Link to="/chat" className="text-muted-fg hover:text-fg text-sm">
          ←
        </Link>
        <div className="min-w-0">
          <h1 className="truncate font-semibold">{roomName}</h1>
          {subtitle && <p className="text-muted-fg truncate text-xs">{subtitle}</p>}
        </div>
      </header>

      {(error ?? uploadImage.error?.message) && (
        <p role="alert" className="text-down bg-muted px-4 py-2 text-center text-xs">
          {error ?? uploadImage.error?.message}
        </p>
      )}

      <div className="flex-1 space-y-2 overflow-y-auto px-4 py-3">
        {history.isPending ? (
          <p className="text-muted-fg text-center text-sm">불러오는 중…</p>
        ) : history.isError ? (
          <p role="alert" className="text-down text-center text-sm">
            {history.error.message}
          </p>
        ) : (
          messages.map((message, index) => {
            const previous = messages[index - 1]
            const showDivider = !sameDay(message.createdAt, previous?.createdAt)
            return (
              <Fragment key={message.id}>
                {showDivider && message.createdAt && <DateDivider iso={message.createdAt} />}
                <MessageBubble
                  message={message}
                  isMine={message.senderId === member.id}
                  nickname={message.senderId != null ? nicknameById.get(message.senderId) : undefined}
                />
              </Fragment>
            )
          })
        )}
        <div ref={bottomRef} />
      </div>

      <MessageComposer disabled={!connected} onSend={sendMessage} onSendImage={handleSendImage} />
    </main>
  )
}
