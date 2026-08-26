import { Fragment, useEffect, useRef, useState } from 'react'
import { createFileRoute, Link, useNavigate, useParams } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { LogOut } from 'lucide-react'

import { useMe } from '@/api/generated/auth/auth'
import { getGetMyRoomsQueryKey, useGetMessages, useGetRoom, useMarkRead } from '@/api/generated/chat/chat'
import { PotDetailResponseStatus } from '@/api/generated/model'
import type { ChatMessageResponse } from '@/api/generated/model'
import { useGetPotByChatRoom, useLeavePot } from '@/api/generated/pot/pot'
import { requireAuth } from '@/lib/authGuard'

import { DateDivider } from './-components/DateDivider'
import { MessageBubble } from './-components/MessageBubble'
import { MessageComposer } from './-components/MessageComposer'
import { PotAccountBanner } from './-components/PotAccountBanner'
import { useChatSocket } from './-hooks/useChatSocket'
import { useUploadChatImage } from './-hooks/useUploadChatImage'

export const Route = createFileRoute('/chat/$roomId/')({
  beforeLoad: ({ context }) => requireAuth(context.queryClient),
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
  const navigate = useNavigate()
  const me = useMe({ query: { retry: false } })
  const member = me.isError ? undefined : me.data?.data

  const room = useGetRoom(roomId, { query: { enabled: !!member } })
  const history = useGetMessages(roomId, { size: 50 }, { query: { enabled: !!member } })

  // 배달팟이 아닌 방(수동 생성 등)이면 404가 정상이라 재시도하지 않는다 — 배너/나가기 버튼만 조용히 숨긴다.
  const pot = useGetPotByChatRoom(roomId, { query: { enabled: !!member, retry: false } })
  const potData = pot.data?.data

  const leavePot = useLeavePot({
    mutation: {
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: getGetMyRoomsQueryKey() })
        void navigate({ to: '/chat' })
      },
    },
  })

  const handleLeave = () => {
    if (!potData?.potId) return
    if (!window.confirm('배달팟에서 나가시겠어요? 이 채팅방을 더 이상 볼 수 없어요.')) return
    leavePot.mutate({ potId: potData.potId })
  }

  const nicknameById = new Map(
    (room.data?.data?.members ?? []).map((m) => [m.memberId, m.nickname]),
  )
  const myNickname = member ? nicknameById.get(member.id) : undefined

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
      <main aria-label="채팅방" className="mx-auto max-w-[393px] px-4 py-6">
        <p className="text-muted-fg text-sm">로그인 상태 확인 중…</p>
      </main>
    )
  }

  if (!member) {
    return (
      <main aria-label="채팅방" className="mx-auto max-w-[393px] px-4 py-6">
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
    <main aria-label={roomName} className="bg-bg mx-auto flex h-dvh max-w-[393px] flex-col shadow-xl">
      <header className="flex items-center justify-between gap-2 border-b px-4 py-3">
        <div className="flex min-w-0 items-center gap-2">
          <Link to="/chat" className="text-muted-fg hover:text-fg text-sm">
            ←
          </Link>
          <div className="min-w-0">
            <h1 className="truncate font-semibold">{roomName}</h1>
            {subtitle && <p className="text-muted-fg truncate text-xs">{subtitle}</p>}
          </div>
        </div>
        {potData && (!potData.isHost || potData.status === PotDetailResponseStatus.DONE) && (
          <button
            type="button"
            onClick={handleLeave}
            disabled={leavePot.isPending}
            aria-label="배달팟 나가기"
            className="text-muted-fg hover:text-down shrink-0 disabled:opacity-50"
          >
            <LogOut className="size-5" />
          </button>
        )}
      </header>

      {potData?.account && <PotAccountBanner account={potData.account} />}

      {/* 사진 업로드 실패(용량 제한 등으로 인프라 단에서 403이 나는 경우 포함)는 기획 요구사항에 따라
          에러 문구를 노출하지 않는다 — 사용자는 그냥 사진이 전송되지 않은 채로 다시 시도하면 된다. */}
      {(error ?? leavePot.error?.message) && (
        <p role="alert" className="text-down bg-muted px-4 py-2 text-center text-xs">
          {error ?? leavePot.error?.message}
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
                  myNickname={myNickname}
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
