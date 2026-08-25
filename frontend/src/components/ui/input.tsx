import type { ComponentProps } from 'react'

import { cn } from '@/lib/utils'

export function Input({ className, ...props }: ComponentProps<'input'>) {
  return (
    <input
      className={cn(
        'h-11 w-full rounded-md border bg-bg px-3 text-base outline-none transition-colors',
        'placeholder:text-muted-fg focus-visible:border-primary focus-visible:outline-2',
        'focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50',
        'aria-[invalid=true]:border-down aria-[invalid=true]:focus-visible:outline-down',
        className,
      )}
      {...props}
    />
  )
}
