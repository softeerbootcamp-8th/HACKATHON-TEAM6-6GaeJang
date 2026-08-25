import { cn } from '@/lib/utils'
import { ChatMessageResponseType } from '@/api/generated/model'
import type { ChatMessageResponse } from '@/api/generated/model'

interface MessageBubbleProps {
  message: ChatMessageResponse
  isMine: boolean
}

export function MessageBubble({ message, isMine }: MessageBubbleProps) {
  if (message.type === ChatMessageResponseType.SYSTEM_JOIN) {
    return <p className="text-muted-fg py-1 text-center text-xs">{message.content}</p>
  }

  if (message.type === ChatMessageResponseType.SYSTEM_MENU) {
    return (
      <div className={cn('flex', isMine ? 'justify-end' : 'justify-start')}>
        <div
          className={cn(
            'max-w-[75%] space-y-1.5 rounded-2xl px-4 py-3',
            isMine ? 'rounded-tr-sm bg-fg text-bg' : 'rounded-tl-sm bg-muted text-fg',
          )}
        >
          <p className="text-sm break-words">{message.content}</p>
          {message.menuPrice != null && (
            <p className="text-sm font-bold">{message.menuPrice.toLocaleString()}원</p>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className={cn('flex', isMine ? 'justify-end' : 'justify-start')}>
      <p
        className={cn(
          'max-w-[75%] rounded-2xl px-3.5 py-2 text-sm break-words',
          isMine ? 'rounded-tr-sm bg-primary text-primary-fg' : 'rounded-tl-sm bg-muted text-fg',
        )}
      >
        {message.content}
      </p>
    </div>
  )
}
