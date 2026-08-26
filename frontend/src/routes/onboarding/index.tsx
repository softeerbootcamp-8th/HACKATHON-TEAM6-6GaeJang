import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'

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

  // 주소입력 단계는 URL이 안 바뀌어 브라우저 히스토리에 항목이 안 쌓인다. 그대로 두면
  // 뒤로가기를 눌렀을 때 계정정보 단계가 아니라 /onboarding 이전 페이지(로그인 등)로
  // 튕긴다. pushState로 항목을 하나 쌓고 popstate에서 계정정보로 돌려보내 맞춘다.
  const goToAddressStep = () => {
    window.history.pushState({ onboardingStep: 'address' }, '')
    setStep('address')
  }

  useEffect(() => {
    // AddressSetupStep이 지도 화면용으로 항목을 하나 더 쌓을 수 있으니, 무조건 계정정보로
    // 돌리지 않고 popstate가 남긴 state를 보고 판단한다 — 지도→검색처럼 이 단계 안쪽으로
    // 돌아온 경우까지 계정정보로 튕겨버리면 안 된다.
    const handlePopState = (event: PopStateEvent) => {
      setStep(event.state?.onboardingStep === 'address' ? 'address' : 'account')
    }
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

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
    <main aria-label="온보딩" className="bg-bg mx-auto min-h-dvh max-w-[393px] shadow-xl">
      {step === 'account' && (
        <>
          <AccountInfoStep
            phoneNumber={phoneNumber}
            onChangePhoneNumber={setPhoneNumber}
            password={password}
            onChangePassword={setPassword}
            nickname={nickname}
            onChangeNickname={setNickname}
            onNext={goToAddressStep}
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
          onBack={() => window.history.back()}
          onComplete={handleCompleteAddress}
          isSubmitting={signup.isPending}
        />
      )}
    </main>
  )
}
