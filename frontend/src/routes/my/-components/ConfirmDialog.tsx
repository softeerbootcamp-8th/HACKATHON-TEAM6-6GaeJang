import { Button } from '@/components/ui/button'

type ConfirmDialogProps = {
  open: boolean
  title: string
  description?: string
  cancelLabel: string
  confirmLabel: string
  onCancel: () => void
  onConfirm: () => void
  isConfirming?: boolean
}

/** 로그아웃/회원탈퇴처럼 "정말 할까요?"를 묻는 두 버튼 확인 모달. */
export function ConfirmDialog({
  open,
  title,
  description,
  cancelLabel,
  confirmLabel,
  onCancel,
  onConfirm,
  isConfirming,
}: ConfirmDialogProps) {
  if (!open) return null

  return (
    <div
      role="presentation"
      onMouseDown={onCancel}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-6"
    >
      <div
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        onMouseDown={(event) => event.stopPropagation()}
        className="w-full max-w-sm rounded-2xl bg-bg p-6 text-center shadow-xl"
      >
        <h3 id="confirm-dialog-title" className="text-lg font-bold text-fg">
          {title}
        </h3>
        {description && (
          <p className="text-muted-fg mt-2 text-xs leading-relaxed">{description}</p>
        )}
        <div className="mt-6 flex gap-2">
          <Button
            type="button"
            onClick={onCancel}
            disabled={isConfirming}
            className="bg-muted text-fg h-12 flex-1 rounded-xl text-sm font-semibold hover:opacity-90"
          >
            {cancelLabel}
          </Button>
          <Button
            type="button"
            onClick={onConfirm}
            disabled={isConfirming}
            className="h-12 flex-1 rounded-xl text-sm font-semibold"
          >
            {isConfirming ? '처리 중…' : confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  )
}
