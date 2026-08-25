import { Link } from '@tanstack/react-router'

import type { ChatRoomSummaryResponse } from '@/api/generated/model'

interface ChatRoomListItemProps {
  room: ChatRoomSummaryResponse
}

export function ChatRoomListItem({ room }: ChatRoomListItemProps) {
  const hasUnread = !!room.unreadCount && room.unreadCount > 0

  return (
    <Link
      to="/chat/$roomId"
      params={{ roomId: String(room.roomId) }}
      className="flex items-center gap-3 border-b py-4 last:border-b-0"
    >
      <span className="bg-fg/40 text-primary-fg flex size-12 shrink-0 items-center justify-center rounded-full text-base font-medium">
        {room.name?.trim().charAt(0) || '?'}
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-semibold">{room.name}</p>
        <p className="text-muted-fg mt-1 truncate text-xs">
          {room.lastMessagePreview ?? '메시지가 아직 없어요'}
        </p>
      </div>
      <div className="flex shrink-0 flex-col items-end gap-1.5">
        {room.lastMessageAt && (
          <span className="text-muted-fg/70 text-xs">
            {new Date(room.lastMessageAt).toLocaleTimeString('ko-KR', {
              hour: '2-digit',
              minute: '2-digit',
            })}
          </span>
        )}
        {hasUnread && (
          <span className="bg-primary text-primary-fg flex size-5 items-center justify-center rounded-full text-xs font-medium">
            {room.unreadCount}
          </span>
        )}
      </div>
    </Link>
  )
}
