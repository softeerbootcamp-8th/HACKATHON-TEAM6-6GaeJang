import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useState } from 'react'

import { useSignup } from '@/api/generated/auth/auth'
import { Card, CardContent } from '@/components/ui/card'

import { AddressStep } from './-components/AddressStep'
import { NicknameStep } from './-components/NicknameStep'
import { OnboardingProgress } from './-components/OnboardingProgress'
import { PasswordStep } from './-components/PasswordStep'
import { PhoneStep } from './-components/PhoneStep'
import { VerifyStep } from './-components/VerifyStep'

export const Route = createFileRoute('/onboarding/')({
  component: OnboardingPage,
})

type Step = 0 | 1 | 2 | 3 | 4

const LAST_STEP = 4

function OnboardingPage() {
  const navigate = useNavigate()
  const [step, setStep] = useState<Step>(0)
  const [phoneNumber, setPhoneNumber] = useState('')
  const [password, setPassword] = useState('')
  const [nickname, setNickname] = useState('')
  const [address, setAddress] = useState('')
  // 본인 기기에서 가입하는 흐름이라 기본 on. 원치 않으면 마지막 단계에서 끌 수 있다.
  const [rememberMe, setRememberMe] = useState(true)

  const signup = useSignup({
    mutation: {
      // 가입 성공 = 세션 쿠키 발급 완료 → 홈으로.
      onSuccess: () => void navigate({ to: '/' }),
    },
  })

  const next = () => setStep((s) => Math.min(s + 1, LAST_STEP) as Step)
  const back = () => setStep((s) => Math.max(s - 1, 0) as Step)

  const submit = () => {
    signup.mutate({ data: { phoneNumber, password, nickname, address, rememberMe } })
  }

  return (
    <main aria-label="온보딩" className="mx-auto flex min-h-dvh max-w-md flex-col px-4 py-8">
      <div className="mb-6">
        <OnboardingProgress current={step} />
      </div>

      <Card className="flex-1">
        <CardContent className="pt-6">
          {step === 0 && <PhoneStep value={phoneNumber} onChange={setPhoneNumber} onNext={next} />}
          {step === 1 && (
            <VerifyStep phoneNumber={phoneNumber} onNext={next} onBack={back} />
          )}
          {step === 2 && (
            <PasswordStep value={password} onChange={setPassword} onNext={next} onBack={back} />
          )}
          {step === 3 && (
            <NicknameStep value={nickname} onChange={setNickname} onNext={next} onBack={back} />
          )}
          {step === 4 && (
            <AddressStep
              value={address}
              onChange={setAddress}
              rememberMe={rememberMe}
              onRememberMeChange={setRememberMe}
              onSubmit={submit}
              onBack={back}
              isSubmitting={signup.isPending}
              error={signup.error?.message}
            />
          )}
        </CardContent>
      </Card>

      <p className="text-muted-fg mt-6 text-center text-sm">
        이미 계정이 있나요?{' '}
        <Link to="/login" className="text-primary font-medium">
          로그인
        </Link>
      </p>
    </main>
  )
}
