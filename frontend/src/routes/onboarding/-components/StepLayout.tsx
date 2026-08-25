import type { ReactNode } from 'react'

import { Button } from '@/components/ui/button'

type StepLayoutProps = {
  title: string
  description?: string
  children: ReactNode
  onBack?: () => void
  onNext: () => void
  nextLabel?: string
  nextDisabled?: boolean
  nextLoading?: boolean
}

/** 온보딩 각 단계의 공통 골격 — 제목/설명 + 본문 + 하단 이전/다음 버튼. */
export function StepLayout({
  title,
  description,
  children,
  onBack,
  onNext,
  nextLabel = '다음',
  nextDisabled,
  nextLoading,
}: StepLayoutProps) {
  return (
    <form
      className="flex flex-col gap-6"
      onSubmit={(e) => {
        e.preventDefault()
        if (!nextDisabled && !nextLoading) onNext()
      }}
    >
      <header className="flex flex-col gap-1">
        <h2 className="text-xl font-semibold">{title}</h2>
        {description && <p className="text-muted-fg text-sm">{description}</p>}
      </header>

      {children}

      <div className="flex gap-2">
        {onBack && (
          <Button type="button" variant="outline" onClick={onBack} className="flex-1">
            이전
          </Button>
        )}
        <Button type="submit" disabled={nextDisabled || nextLoading} className="flex-1">
          {nextLoading ? '처리 중…' : nextLabel}
        </Button>
      </div>
    </form>
  )
}
