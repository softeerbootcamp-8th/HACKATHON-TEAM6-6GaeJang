import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useState } from 'react'

import { useLogin } from '@/api/generated/auth/auth'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { formatPhoneNumber, unformatPhoneNumber } from '@/lib/phoneFormatter'

export const Route = createFileRoute('/login/')({
  component: LoginPage,
})

function LoginPage() {
  const navigate = useNavigate()
  const [phoneNumber, setPhoneNumber] = useState('')
  const [password, setPassword] = useState('')
  const [rememberMe, setRememberMe] = useState(false)

  const rawPhone = unformatPhoneNumber(phoneNumber)
  const isValid = rawPhone.length >= 10 && password.length > 0

  const login = useLogin({
    mutation: {
      onSuccess: () => void navigate({ to: '/' }),
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (isValid && !login.isPending) {
      login.mutate({
        data: {
          phoneNumber: rawPhone,
          password,
          rememberMe,
        },
      })
    }
  }

  return (
    <main
      aria-label="로그인"
      className="mx-auto flex min-h-dvh max-w-md flex-col justify-between bg-bg px-6 py-10"
    >
      <div className="flex flex-col">
        {/* 상단 로고 */}
        <header className="mb-8">
          <span className="text-xl font-bold tracking-tight text-fg">Delipot</span>
        </header>

        {/* 메인 타이틀 */}
        <div className="mb-8">
          <h1 className="text-2xl font-bold leading-snug text-fg">
            반가워요!
            <br />
            로그인을 해주세요
          </h1>
        </div>

        {/* 로그인 폼 */}
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {/* 전화번호 입력 필드 (+82 🇰🇷 고정 접두사) */}
          <div className="flex h-13 items-center rounded-xl border border-border bg-bg px-3.5 transition-colors focus-within:border-primary">
            <span className="text-muted-fg mr-2 flex shrink-0 select-none items-center whitespace-nowrap text-sm font-medium">
              +82 🇰🇷
            </span>
            <input
              type="tel"
              inputMode="numeric"
              autoFocus
              placeholder="전화번호 입력"
              value={phoneNumber}
              maxLength={13} // 010-1234-5678 (13자)
              onChange={(e) => setPhoneNumber(formatPhoneNumber(e.target.value))}
              aria-label="전화번호 입력"
              className="flex-1 bg-transparent text-base text-fg outline-none placeholder:text-muted-fg"
            />
          </div>

          {/* 비밀번호 입력 필드 (최대 20자) */}
          <div className="flex h-13 items-center rounded-xl border border-border bg-bg px-3.5 transition-colors focus-within:border-primary">
            <input
              type="password"
              placeholder="비밀번호 입력"
              value={password}
              maxLength={20}
              onChange={(e) => setPassword(e.target.value)}
              aria-label="비밀번호 입력"
              className="w-full bg-transparent text-base text-fg outline-none placeholder:text-muted-fg"
            />
          </div>

          {/* 자동 로그인 체크박스 */}
          <div className="mt-1 flex items-center justify-between">
            <label className="text-muted-fg flex cursor-pointer select-none items-center gap-2 text-sm">
              <Checkbox
                checked={rememberMe}
                onChange={(e) => setRememberMe(e.target.checked)}
              />
              자동 로그인
            </label>
          </div>

          {login.error && (
            <p role="alert" className="text-down mt-1 text-sm">
              {login.error.message || '전화번호 또는 비밀번호가 올바르지 않습니다.'}
            </p>
          )}

          {/* 로그인 버튼 */}
          <Button
            type="submit"
            disabled={!isValid || login.isPending}
            className="mt-4 h-13 w-full rounded-xl text-base font-semibold transition-opacity"
          >
            {login.isPending ? '로그인 중…' : '로그인하기'}
          </Button>
        </form>
      </div>

      {/* 하단 회원가입 이동 */}
      <footer className="mt-10 text-center">
        <Link
          to="/onboarding"
          className="text-muted-fg hover:text-fg text-sm underline-offset-4 hover:underline"
        >
          회원가입
        </Link>
      </footer>
    </main>
  )
}
