import { useRef, useState } from 'react'
import { ArrowUp, Plus } from 'lucide-react'

import { Button } from '@/components/ui/button'
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
    <form
      onSubmit={handleSubmit}
      className="bg-bg flex shrink-0 items-center gap-3 border-t px-4 pt-3 pb-[max(16px,env(safe-area-inset-bottom))]"
    >
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        onChange={handleFileChange}
        className="hidden"
      />
      <div className="bg-muted flex min-w-0 flex-1 items-center rounded-full">
        <Button
          type="button"
          variant="ghost"
          size="icon"
          disabled={disabled}
          onClick={() => fileInputRef.current?.click()}
          aria-label="사진 첨부"
          className="text-muted-fg hover:bg-border/50 size-11 shrink-0 rounded-full"
        >
          <Plus className="size-5" aria-hidden />
        </Button>
        <input
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="메시지를 입력하세요"
          maxLength={2000}
          disabled={disabled}
          aria-label="메시지 입력"
          className="text-fg placeholder:text-muted-fg min-w-0 flex-1 bg-transparent py-2.5 pr-4 text-base leading-6 outline-none disabled:opacity-50"
        />
      </div>
      <Button
        type="submit"
        size="icon"
        disabled={disabled || content.trim().length === 0}
        aria-label="전송"
        className={cn(
          'size-11 shrink-0 rounded-full transition-colors',
          'bg-primary text-primary-fg disabled:opacity-50',
        )}
      >
        <ArrowUp className="size-5" aria-hidden />
      </Button>
    </form>
  )
}
