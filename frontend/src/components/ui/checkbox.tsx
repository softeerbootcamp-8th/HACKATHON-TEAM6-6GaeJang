import type { ComponentProps } from 'react'

import { cn } from '@/lib/utils'

/** 라벨과 함께 쓰는 단순 체크박스. 브랜드색은 accent-color 로 입힌다. */
export function Checkbox({ className, ...props }: ComponentProps<'input'>) {
  return (
    <input
      type="checkbox"
      className={cn(
        'size-4 cursor-pointer accent-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary',
        className,
      )}
      {...props}
    />
  )
}
