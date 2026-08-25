import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useState } from 'react'

import { useSignup } from '@/api/generated/auth/auth'
import { redirectIfAuthenticated } from '@/lib/authGuard'
import { unformatPhoneNumber } from '@/lib/phoneFormatter'
import { AccountInfoStep } from './-components/AccountInfoStep'
import { AddressSetupStep } from './-components/AddressSetupStep'
import type { SelectedLocation } from './-components/KakaoMapPicker'

export const Route = createFileRoute('/onboarding/')({
  beforeLoad: ({ context }) => redirectIfAuthenticated(context.queryClient),
  component: OnboardingPage,
})

type OnboardingStep = 'account' | 'address'

function OnboardingPage() {
  const navigate = useNavigate()
  const [step, setStep] = useState<OnboardingStep>('account')

  const [phoneNumber, setPhoneNumber] = useState('')
  const [password, setPassword] = useState('')
  const [nickname, setNickname] = useState('')

  const signup = useSignup({
    mutation: {
      // 가입 성공 = 세션 쿠키 발급 완료 → 홈(/)으로 이동
      onSuccess: () => void navigate({ to: '/' }),
    },
  })

  // 최종 주소 선택 완료 시 가입 API 호출
  const handleCompleteAddress = (selected: SelectedLocation) => {
    const rawPhone = unformatPhoneNumber(phoneNumber)
    signup.mutate({
      data: {
        phoneNumber: rawPhone,
        password,
        nickname,
        address: selected.address,
        roadAddress: selected.roadAddress,
        jibunAddress: selected.jibunAddress,
        latitude: selected.latitude,
        longitude: selected.longitude,
        rememberMe: true,
      },
    })
  }

  return (
    <main aria-label="온보딩" className="mx-auto min-h-dvh max-w-md bg-bg">
      {step === 'account' && (
        <>
          <AccountInfoStep
            phoneNumber={phoneNumber}
            onChangePhoneNumber={setPhoneNumber}
            password={password}
            onChangePassword={setPassword}
            nickname={nickname}
            onChangeNickname={setNickname}
            onNext={() => setStep('address')}
          />
          <footer className="-mt-8 pb-10 text-center">
            <p className="text-muted-fg text-sm">
              이미 계정이 있나요?{' '}
              <Link to="/login" className="text-primary font-semibold hover:underline">
                로그인
              </Link>
            </p>
          </footer>
        </>
      )}

      {step === 'address' && (
        <AddressSetupStep
          onBack={() => setStep('account')}
          onComplete={handleCompleteAddress}
          isSubmitting={signup.isPending}
        />
      )}
    </main>
  )
}
