import { Link } from '@tanstack/react-router'
import { Home, MessageCircle, UserRound } from 'lucide-react'

type MobileBottomNavProps = {
  active: 'home' | 'chat' | 'my'
}

export function MobileBottomNav({ active }: MobileBottomNavProps) {
  const itemClass = (name: MobileBottomNavProps['active']) =>
    `flex min-w-16 flex-col items-center gap-1 text-[11px] ${
      active === name ? 'text-primary' : 'text-fg'
    }`

  return (
    <nav
      aria-label="주요 메뉴"
      className="bg-bg absolute inset-x-0 bottom-0 z-30 flex h-[84px] items-start justify-around border-t pt-3 pb-[env(safe-area-inset-bottom)]"
    >
      <Link to="/" className={itemClass('home')}>
        <Home className="size-6" strokeWidth={active === 'home' ? 2.3 : 1.8} />
        <span>홈</span>
      </Link>
      <Link to="/chat" className={itemClass('chat')}>
        <MessageCircle className="size-6" strokeWidth={active === 'chat' ? 2.3 : 1.8} />
        <span>채팅</span>
      </Link>
      <span aria-disabled="true" className={itemClass('my')}>
        <UserRound className="size-6" strokeWidth={active === 'my' ? 2.3 : 1.8} />
        <span>마이</span>
      </span>
    </nav>
  )
}
