import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { getMeQueryKey, useSignup } from '@/api/generated/auth/auth'
import { redirectIfAuthenticated } from '@/lib/authGuard'
import { unformatPhoneNumber } from '@/lib/phoneFormatter'
import { AccountInfoStep } from './-components/AccountInfoStep'
import { AddressSetupStep } from '../-components/address/AddressSetupStep'
import type { SelectedLocation } from '../-components/address/KakaoMapPicker'

export const Route = createFileRoute('/onboarding/')({
  beforeLoad: ({ context }) => redirectIfAuthenticated(context.queryClient),
  component: OnboardingPage,
})

type OnboardingStep = 'account' | 'address'

function OnboardingPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [step, setStep] = useState<OnboardingStep>('account')

  const [phoneNumber, setPhoneNumber] = useState('')
  const [password, setPassword] = useState('')
  const [nickname, setNickname] = useState('')

  const signup = useSignup({
    mutation: {
      // 가입 성공 = 세션 쿠키 발급 완료 → 홈(/)으로 이동.
      // me 쿼리 캐시를 갱신하지 않으면 로그아웃 이력이 있는 세션에서 '/'의 requireAuth 가드가
      // 캐시된 error 상태를 보고 미인증으로 오판해 도로 튕겨낸다.
      onSuccess: (data) => {
        queryClient.setQueryData(getMeQueryKey(), data)
        void navigate({ to: '/' })
      },
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
