import { useState } from 'react'

import type { AccountResponse } from '@/api/generated/model'

interface PotAccountBannerProps {
  account: AccountResponse
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
    <div className="bg-primary/10 flex items-center justify-between gap-3 px-4 py-3 text-sm">
      <div className="min-w-0">
        <p className="text-muted-fg text-xs">총대 입금 계좌</p>
        <p className="truncate font-semibold">
          {account.bankName} {account.accountNumber}
        </p>
        <p className="text-down text-xs">총대의 주문이 확정된 이후에 입금해주세요</p>
      </div>
      <button
        type="button"
        onClick={handleCopy}
        className="border-primary text-primary shrink-0 rounded-full border bg-transparent px-3 py-1 text-xs font-medium"
      >
        {copied ? '복사됨' : '복사'}
      </button>
    </div>
  )
}
