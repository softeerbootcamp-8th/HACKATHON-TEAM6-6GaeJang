interface DateDividerProps {
  iso: string
}

function formatDateLabel(iso: string) {
  return new Date(iso).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })
}

export function DateDivider({ iso }: DateDividerProps) {
  return (
    <div className="flex items-center justify-center py-2">
      <span className="bg-muted text-muted-fg rounded-full px-3 py-1 text-xs">{formatDateLabel(iso)}</span>
    </div>
  )
}
