import { useRef, useState } from 'react'
import { ArrowUp, Paperclip } from 'lucide-react'

import { cn } from '@/lib/utils'

interface MessageComposerProps {
  disabled: boolean
  onSend: (content: string) => void
  onSendImage: (file: File) => void
}

export function MessageComposer({ disabled, onSend, onSendImage }: MessageComposerProps) {
  const [content, setContent] = useState('')
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const trimmed = content.trim()
    if (!trimmed || disabled) return
    onSend(trimmed)
    setContent('')
  }

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (file) onSendImage(file)
  }

  return (
    <form onSubmit={handleSubmit} className="flex items-center gap-2 border-t p-3">
      <div className="border-border flex flex-1 items-center gap-1 rounded-2xl border px-2">
        <button
          type="button"
          disabled={disabled}
          onClick={() => fileInputRef.current?.click()}
          aria-label="사진 첨부"
          className="text-muted-fg hover:text-fg flex size-9 shrink-0 items-center justify-center disabled:opacity-50"
        >
          <Paperclip className="size-5" />
        </button>
        <input
          ref={fileInputRef}
          type="file"
          accept="image/*"
          onChange={handleFileChange}
          className="hidden"
        />
        <input
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="메시지를 입력하세요"
          maxLength={2000}
          disabled={disabled}
          aria-label="메시지 입력"
          className="text-fg placeholder:text-muted-fg flex-1 bg-transparent py-2 text-sm outline-none disabled:opacity-50"
        />
      </div>
      <button
        type="submit"
        disabled={disabled || content.trim().length === 0}
        aria-label="전송"
        className={cn(
          'flex size-10 shrink-0 items-center justify-center rounded-full transition-colors',
          'bg-primary text-primary-fg disabled:opacity-50',
        )}
      >
        <ArrowUp className="size-5" />
      </button>
    </form>
  )
}
