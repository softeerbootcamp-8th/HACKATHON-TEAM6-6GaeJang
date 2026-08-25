import { cn } from '@/lib/utils'
import { ChatMessageResponseType } from '@/api/generated/model'
import type { ChatMessageResponse } from '@/api/generated/model'

interface MessageBubbleProps {
  message: ChatMessageResponse
  isMine: boolean
  nickname?: string
}

function formatTime(iso?: string) {
  if (!iso) return ''
  return new Date(iso).toLocaleTimeString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

function Avatar({ nickname, isMine }: { nickname?: string; isMine: boolean }) {
  return (
    <div
      className={cn(
        'flex size-8 shrink-0 items-center justify-center rounded-full text-sm font-semibold',
        isMine ? 'bg-primary text-primary-fg' : 'bg-muted text-fg',
      )}
      aria-hidden
    >
      {nickname?.[0] ?? '?'}
    </div>
  )
}

export function MessageBubble({ message, isMine, nickname }: MessageBubbleProps) {
  // SYSTEM_JOIN(입장/나눔완료 공지)만 가운데 정렬 안내문이다. SYSTEM_MENU는 제출한 사람이
  // 있는 실체가 있는 내용이라 일반 메시지처럼(색만 다르게) 아바타·닉네임을 달고 보여준다.
  if (message.type === ChatMessageResponseType.SYSTEM_JOIN) {
    return <p className="text-muted-fg py-1 text-center text-xs">{message.content}</p>
  }

  const time = formatTime(message.createdAt)
  const isImage = message.type === ChatMessageResponseType.IMAGE
  const isMenu = message.type === ChatMessageResponseType.SYSTEM_MENU

  return (
    <div className={cn('flex items-end gap-2', isMine ? 'flex-row-reverse' : 'flex-row')}>
      <Avatar nickname={nickname} isMine={isMine} />
      <div className={cn('flex flex-col gap-1', isMine ? 'items-end' : 'items-start')}>
        {!isMine && nickname && <span className="text-muted-fg px-0.5 text-xs">{nickname}</span>}
        <div className={cn('flex items-end gap-1.5', isMine && 'flex-row-reverse')}>
          {isImage ? (
            <img
              src={message.content}
              alt="전송된 사진"
              className="max-h-64 max-w-[70%] rounded-2xl object-cover"
            />
          ) : isMenu ? (
            <div className="bg-fg text-bg max-w-[70%] rounded-2xl px-3.5 py-2.5 text-sm">
              <p className="break-words">{message.content}</p>
              {message.menuPrice != null && (
                <p className="mt-1 font-semibold">{message.menuPrice.toLocaleString()}원</p>
              )}
            </div>
          ) : (
            <p
              className={cn(
                'max-w-[70%] rounded-2xl px-3.5 py-2 text-sm break-words',
                isMine ? 'bg-primary text-primary-fg' : 'bg-muted text-fg',
              )}
            >
              {message.content}
            </p>
          )}
          {time && <span className="text-muted-fg shrink-0 text-[11px]">{time}</span>}
        </div>
      </div>
    </div>
  )
}
