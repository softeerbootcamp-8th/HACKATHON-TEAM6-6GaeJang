import { useState, type FormEvent } from 'react'
import { ArrowUp } from 'lucide-react'

interface MessageComposerProps {
  disabled: boolean
  onSend: (content: string) => void
}

export function MessageComposer({ disabled, onSend }: MessageComposerProps) {
  const [content, setContent] = useState('')

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    const trimmed = content.trim()
    if (!trimmed || disabled) return
    onSend(trimmed)
    setContent('')
  }

  return (
    <form onSubmit={handleSubmit} className="flex items-center gap-3 border-t px-4 py-2">
      <input
        value={content}
        onChange={(e) => setContent(e.target.value)}
        placeholder={disabled ? '연결 중…' : '메시지를 입력하세요'}
        maxLength={2000}
        disabled={disabled}
        aria-label="메시지 입력"
        className="bg-surface text-fg placeholder:text-muted-fg h-10 flex-1 rounded-full px-4 text-sm outline-none"
      />
      <button
        type="submit"
        disabled={disabled || content.trim().length === 0}
        aria-label="메시지 보내기"
        className="bg-primary text-primary-fg flex size-10 shrink-0 items-center justify-center rounded-full disabled:opacity-50"
      >
        <ArrowUp className="size-5" />
      </button>
    </form>
  )
}
