import { useState } from 'react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

interface MessageComposerProps {
  disabled: boolean
  onSend: (content: string) => void
}

export function MessageComposer({ disabled, onSend }: MessageComposerProps) {
  const [content, setContent] = useState('')

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const trimmed = content.trim()
    if (!trimmed || disabled) return
    onSend(trimmed)
    setContent('')
  }

  return (
    <form onSubmit={handleSubmit} className="flex gap-2 border-t p-3">
      <Input
        value={content}
        onChange={(e) => setContent(e.target.value)}
        placeholder={disabled ? '연결 중…' : '메시지 입력'}
        maxLength={2000}
        disabled={disabled}
        aria-label="메시지 입력"
      />
      <Button type="submit" disabled={disabled || content.trim().length === 0}>
        전송
      </Button>
    </form>
  )
}
