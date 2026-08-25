import { Link } from '@tanstack/react-router'

import { Card, CardContent } from '@/components/ui/card'
import type { ChatRoomSummaryResponse } from '@/api/generated/model'

interface ChatRoomListItemProps {
  room: ChatRoomSummaryResponse
}

export function ChatRoomListItem({ room }: ChatRoomListItemProps) {
  const hasUnread = !!room.unreadCount && room.unreadCount > 0

  return (
    <Link to="/chat/$roomId" params={{ roomId: String(room.roomId) }} className="block">
      <Card className="hover:bg-muted transition-colors">
        <CardContent className="flex items-center justify-between gap-3 py-3">
          <div className="min-w-0 flex-1">
            <p className="truncate font-medium">{room.name}</p>
            <p className="text-muted-fg truncate text-sm">
              {room.lastMessagePreview ?? '메시지가 아직 없어요.'}
            </p>
          </div>
          <div className="flex flex-col items-end gap-1">
            {room.lastMessageAt && (
              <span className="text-muted-fg text-xs">
                {new Date(room.lastMessageAt).toLocaleTimeString('ko-KR', {
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </span>
            )}
            {hasUnread && (
              <span className="bg-primary text-primary-fg flex h-5 min-w-5 items-center justify-center rounded-full px-1.5 text-xs font-semibold">
                {room.unreadCount}
              </span>
            )}
          </div>
        </CardContent>
      </Card>
    </Link>
  )
}
