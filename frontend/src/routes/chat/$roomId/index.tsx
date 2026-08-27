import { Fragment, useEffect, useRef, useState } from 'react'
import { createFileRoute, Link, useNavigate, useParams } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, LogOut } from 'lucide-react'

import { useMe } from '@/api/generated/auth/auth'
import {
  getGetMyRoomsQueryKey,
  useGetMessages,
  useGetRoom,
  useMarkRead,
} from '@/api/generated/chat/chat'
import { PotDetailResponseStatus } from '@/api/generated/model'
import type { ChatMessageResponse } from '@/api/generated/model'
import { useGetPotByChatRoom, useLeavePot } from '@/api/generated/pot/pot'
import { Button, buttonVariants } from '@/components/ui/button'
import { requireAuth } from '@/lib/authGuard'
import { formatDeadline } from '@/routes/pots/-utils/formatDeadline'

import { DateDivider } from './-components/DateDivider'
import { LeavePotDialog } from './-components/LeavePotDialog'
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
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  )
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
  const [isLeaveDialogOpen, setIsLeaveDialogOpen] = useState(false)

  const leavePot = useLeavePot({
    mutation: {
      onSuccess: () => {
        setIsLeaveDialogOpen(false)
        queryClient.invalidateQueries({ queryKey: getGetMyRoomsQueryKey() })
        void navigate({ to: '/chat' })
      },
    },
  })

  const handleLeave = () => {
    if (!potData?.potId) return
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

  const { connected, error, sendMessage } = useChatSocket(
    roomId,
    (message) => {
      setLiveMessages((prev) => [...prev, message])
      markRead.mutate({ roomId })
    },
    // 소켓이 끊겼던 동안(백그라운드 전환·네트워크 변경) 온 메시지는 브로드캐스트를 놓쳤다.
    // 재연결 시 이력을 다시 받아 메운다. liveMessages와 겹치는 건 아래 id 기준 dedupe가 걸러낸다.
    () => {
      void history.refetch()
    },
  )

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
  // 재연결 시 이력을 다시 받으면 소켓으로 이미 받아둔 메시지와 겹친다. id로 걸러 중복 표시를
  // 막는다. 이력을 먼저 두었으므로 겹칠 땐 서버가 확정한 쪽이 남는다.
  const seenMessageIds = new Set<number>()
  const messages = [...historyMessages, ...liveMessages].filter((message) => {
    if (message.id == null) return true
    if (seenMessageIds.has(message.id)) return false
    seenMessageIds.add(message.id)
    return true
  })

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
  const subtitle = [memberCount != null ? `멤버 ${memberCount}명` : null, location]
    .filter(Boolean)
    .join(' · ')

  return (
    <main aria-label={roomName} className="app-shell h-[100dvh]">
      <div className="flex h-full flex-col">
        <header className="flex shrink-0 items-center justify-between gap-1 px-3 pt-[max(12px,env(safe-area-inset-top))] pb-2">
          <div className="flex min-w-0 items-center">
            <Link
              to="/chat"
              aria-label="채팅 목록으로 돌아가기"
              className={buttonVariants({
                variant: 'ghost',
                size: 'icon',
                className: 'size-11 shrink-0 rounded-full',
              })}
            >
              <ChevronLeft className="size-5" aria-hidden />
            </Link>
            <div className="min-w-0">
              <div className="flex min-w-0 items-center gap-1.5">
                <h1 className="truncate font-semibold">{roomName}</h1>
                {potData?.deadline && (
                  <span className="bg-primary-soft text-primary shrink-0 rounded-md px-1.5 py-0.5 text-[11px] font-semibold">
                    {formatDeadline(potData.deadline)}
                  </span>
                )}
              </div>
              {subtitle && <p className="text-muted-fg truncate text-xs">{subtitle}</p>}
            </div>
          </div>
          {potData && (!potData.isHost || potData.status === PotDetailResponseStatus.DONE) && (
            <Button
              type="button"
              variant="ghost"
              size="icon"
              onClick={() => setIsLeaveDialogOpen(true)}
              disabled={leavePot.isPending}
              aria-label="배달팟 나가기"
              className="text-muted-fg hover:text-down size-11 shrink-0 rounded-full"
            >
              <LogOut className="size-5" />
            </Button>
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

        <div className="flex-1 space-y-3 overflow-y-auto px-5 py-3">
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
                    nickname={
                      message.senderId != null ? nicknameById.get(message.senderId) : undefined
                    }
                    myNickname={myNickname}
                  />
                </Fragment>
              )
            })
          )}
          <div ref={bottomRef} />
        </div>

        <MessageComposer disabled={!connected} onSend={sendMessage} onSendImage={handleSendImage} />
      </div>

      <LeavePotDialog
        open={isLeaveDialogOpen}
        isLeaving={leavePot.isPending}
        onCancel={() => setIsLeaveDialogOpen(false)}
        onConfirm={handleLeave}
      />
    </main>
  )
}
