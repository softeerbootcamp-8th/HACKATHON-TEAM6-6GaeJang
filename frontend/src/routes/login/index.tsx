import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useState } from 'react'

import { useLogin } from '@/api/generated/auth/auth'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'

export const Route = createFileRoute('/login/')({
  component: LoginPage,
})

function LoginPage() {
  const navigate = useNavigate()
  const [phoneNumber, setPhoneNumber] = useState('')
  const [password, setPassword] = useState('')
  const [rememberMe, setRememberMe] = useState(false)
  const valid = /^\d{11}$/.test(phoneNumber) && password.length > 0

  const login = useLogin({
    mutation: {
      onSuccess: () => void navigate({ to: '/' }),
    },
  })

  return (
    <main aria-label="로그인" className="mx-auto flex min-h-dvh max-w-md flex-col justify-center px-4 py-8">
      <Card>
        <CardHeader>
          <CardTitle>로그인</CardTitle>
          <CardDescription>가입한 휴대폰 번호를 입력해주세요.</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            className="flex flex-col gap-4"
            onSubmit={(e) => {
              e.preventDefault()
              if (valid && !login.isPending)
                login.mutate({ data: { phoneNumber, password, rememberMe } })
            }}
          >
            <Input
              type="tel"
              inputMode="numeric"
              autoFocus
              placeholder="01012345678"
              value={phoneNumber}
              onChange={(e) => setPhoneNumber(e.target.value.replace(/\D/g, '').slice(0, 11))}
              aria-label="휴대폰 번호"
            />
            <Input
              type="password"
              placeholder="비밀번호"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              aria-label="비밀번호"
            />
            <label className="flex cursor-pointer items-center gap-2 text-sm">
              <Checkbox
                checked={rememberMe}
                onChange={(e) => setRememberMe(e.target.checked)}
              />
              자동 로그인
            </label>
            {login.error && (
              <p role="alert" className="text-down text-sm">
                {login.error.message}
              </p>
            )}
            <Button type="submit" disabled={!valid || login.isPending}>
              {login.isPending ? '로그인 중…' : '로그인'}
            </Button>
          </form>
        </CardContent>
      </Card>

      <p className="text-muted-fg mt-6 text-center text-sm">
        처음이신가요?{' '}
        <Link to="/onboarding" className="text-primary font-medium">
          가입하기
        </Link>
      </p>
    </main>
  )
}
