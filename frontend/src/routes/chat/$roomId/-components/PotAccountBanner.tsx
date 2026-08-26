import { useState } from 'react'
import { Megaphone, TriangleAlert } from 'lucide-react'

import type { AccountResponse } from '@/api/generated/model'
import { Button } from '@/components/ui/button'

interface PotAccountBannerProps {
  account: AccountResponse
}

/** 개인정보 보호를 위해 예금주명 가운데 글자를 가린다 (홍길동 → 홍*동, 2글자면 홍* 처럼 마지막 글자를 가린다). */
function maskAccountHolder(name: string): string {
  if (name.length <= 1) return name
  if (name.length === 2) return `${name[0]}*`
  return `${name[0]}${'*'.repeat(name.length - 2)}${name[name.length - 1]}`
}

export function PotAccountBanner({ account }: PotAccountBannerProps) {
  const [copied, setCopied] = useState(false)

  const handleCopy = async () => {
    if (!account.accountNumber) return
    await navigator.clipboard.writeText(account.accountNumber)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  return (
    <section
      aria-label="입금 계좌 안내"
      className="bg-primary-soft border-primary/10 mx-5 mt-2 mb-2 flex items-start gap-3 rounded-2xl border px-4 py-4 text-sm"
    >
      <div className="bg-primary/10 text-primary flex size-8 shrink-0 items-center justify-center rounded-full">
        <Megaphone className="size-4" aria-hidden />
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-center justify-between gap-3">
          <div className="min-w-0">
            <p className="text-muted-fg text-[11px] font-medium">{account.bankName}</p>
            <p className="truncate text-[15px] font-bold tracking-[-0.01em]">
              {account.accountNumber}
              {account.accountHolder && ` ${maskAccountHolder(account.accountHolder)}`}
            </p>
          </div>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={handleCopy}
            className="border-primary/20 bg-bg/70 text-primary hover:bg-bg h-8 shrink-0 rounded-full px-3 text-xs font-semibold"
            aria-live="polite"
          >
            {copied ? '복사됨' : '복사'}
          </Button>
        </div>
        <p className="text-down mt-1.5 flex items-center gap-1 text-[11px] leading-4">
          <TriangleAlert className="size-3.5 shrink-0" aria-hidden />
          총대의 주문이 확정된 이후에 입금해주세요
        </p>
      </div>
    </section>
  )
}
