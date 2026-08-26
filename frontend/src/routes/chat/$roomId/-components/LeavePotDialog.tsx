import { useEffect, useRef } from 'react'

import { Button } from '@/components/ui/button'

interface LeavePotDialogProps {
  open: boolean
  isLeaving: boolean
  onCancel: () => void
  onConfirm: () => void
}

export function LeavePotDialog({ open, isLeaving, onCancel, onConfirm }: LeavePotDialogProps) {
  const dialogRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return

    dialogRef.current?.focus()

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !isLeaving) onCancel()
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [isLeaving, onCancel, open])

  if (!open) return null

  return (
    <div
      className="bg-scrim absolute inset-0 z-50 flex items-center justify-center px-5"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !isLeaving) onCancel()
      }}
    >
      <div
        ref={dialogRef}
        tabIndex={-1}
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="leave-pot-title"
        aria-describedby="leave-pot-description"
        className="bg-bg w-full max-w-[353px] rounded-[20px] px-8 pt-8 pb-7 text-center shadow-xl outline-none"
      >
        <h2 id="leave-pot-title" className="text-lg font-bold">
          배달팟을 나갈까요?
        </h2>
        <p id="leave-pot-description" className="text-muted-fg mt-3 text-sm leading-5">
          나가면 채팅 목록에서 사라져요.
          <br />
          팟이 닫힌 뒤에는 다시 들어올 수 없어요.
        </p>
        <div className="mt-8 grid grid-cols-2 gap-2.5">
          <Button
            type="button"
            variant="ghost"
            onClick={onCancel}
            disabled={isLeaving}
            className="bg-muted hover:bg-muted/80 h-[52px] rounded-xl text-base font-semibold"
          >
            아니요
          </Button>
          <Button
            type="button"
            onClick={onConfirm}
            disabled={isLeaving}
            className="h-[52px] rounded-xl text-base font-semibold"
          >
            {isLeaving ? '나가는 중…' : '나가기'}
          </Button>
        </div>
      </div>
    </div>
  )
}
