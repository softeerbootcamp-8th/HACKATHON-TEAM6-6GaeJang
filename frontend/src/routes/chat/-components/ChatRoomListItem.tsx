import { Link } from '@tanstack/react-router'

import { cn } from '@/lib/utils'
import type { ChatRoomSummaryResponse } from '@/api/generated/model'

interface ChatRoomListItemProps {
  room: ChatRoomSummaryResponse
  /** 연결된 배달팟이 나눔 완료(DONE)됐는지. 종료된 방은 글씨를 옅게 표시한다. */
  isDone?: boolean
}

function isToday(iso: string) {
  const date = new Date(iso)
  const now = new Date()
  return (
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()
  )
}

/** 오늘이면 시각(hh:mm), 그 이전이면 날짜(MM월 dd일)만 보여준다. */
function formatListTimestamp(iso: string) {
  const date = new Date(iso)
  if (isToday(iso)) {
    return date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false })
  }
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${month}월 ${day}일`
}

export function ChatRoomListItem({ room, isDone }: ChatRoomListItemProps) {
  const hasUnread = !!room.unreadCount && room.unreadCount > 0

  return (
    <Link
      to="/chat/$roomId"
      params={{ roomId: String(room.roomId) }}
      className="flex items-center gap-3 border-b py-4 last:border-b-0"
    >
      <span
        className={cn(
          'flex size-12 shrink-0 items-center justify-center rounded-full text-base font-medium',
          isDone ? 'bg-fg/20 text-muted-fg' : 'bg-fg/40 text-primary-fg',
        )}
      >
        {room.name?.trim().charAt(0) || '?'}
      </span>
      <div className="min-w-0 flex-1">
        <p className={cn('truncate text-sm font-semibold', isDone && 'text-muted-fg')}>
          {room.name}
          {isDone && ' · 종료됨'}
        </p>
        <p className="text-muted-fg mt-1 truncate text-xs">
          {room.lastMessagePreview ?? '메시지가 아직 없어요'}
        </p>
      </div>
      <div className="flex shrink-0 flex-col items-end gap-1.5">
        {room.lastMessageAt && (
          <span className="text-muted-fg/70 text-xs">{formatListTimestamp(room.lastMessageAt)}</span>
        )}
        {hasUnread && !isDone && (
          <span className="bg-primary text-primary-fg flex size-5 items-center justify-center rounded-full text-xs font-medium">
            {room.unreadCount}
          </span>
        )}
      </div>
    </Link>
  )
}
