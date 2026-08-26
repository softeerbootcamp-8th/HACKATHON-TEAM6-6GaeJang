import { useState } from 'react'
import { Megaphone, TriangleAlert } from 'lucide-react'

import type { AccountResponse } from '@/api/generated/model'

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
    <div className="bg-primary/10 flex items-start gap-3 px-4 py-3 text-sm">
      <Megaphone className="text-primary mt-0.5 size-5 shrink-0" aria-hidden />
      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-muted-fg text-xs">{account.bankName}</p>
            <p className="truncate font-semibold">
              {account.accountNumber}
              {account.accountHolder && ` ${maskAccountHolder(account.accountHolder)}`}
            </p>
          </div>
          <button
            type="button"
            onClick={handleCopy}
            className="border-primary text-primary shrink-0 rounded-full border bg-transparent px-3 py-1 text-xs font-medium"
          >
            {copied ? '복사됨' : '복사'}
          </button>
        </div>
        <p className="text-down mt-1 flex items-center gap-1 text-xs">
          <TriangleAlert className="size-3.5 shrink-0" aria-hidden />
          총대의 주문이 확정된 이후에 입금해주세요
        </p>
      </div>
    </div>
  )
}
