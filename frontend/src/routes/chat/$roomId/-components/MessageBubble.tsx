import { cn } from '@/lib/utils'
import { ChatMessageResponseType } from '@/api/generated/model'
import type { ChatMessageResponse } from '@/api/generated/model'

interface MessageBubbleProps {
  message: ChatMessageResponse
  isMine: boolean
}

export function MessageBubble({ message, isMine }: MessageBubbleProps) {
  if (message.type !== ChatMessageResponseType.TEXT) {
    return (
      <p className="text-muted-fg py-1 text-center text-xs">
        {message.content}
        {message.menuPrice != null && ` (${message.menuPrice.toLocaleString()}원)`}
      </p>
    )
  }

  return (
    <div className={cn('flex', isMine ? 'justify-end' : 'justify-start')}>
      <p
        className={cn(
          'max-w-[75%] rounded-2xl px-3.5 py-2 text-sm break-words',
          isMine ? 'bg-primary text-primary-fg' : 'bg-muted text-fg',
        )}
      >
        {message.content}
      </p>
    </div>
  )
}
