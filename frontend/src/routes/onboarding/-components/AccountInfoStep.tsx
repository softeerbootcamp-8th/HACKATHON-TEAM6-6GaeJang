import { useEffect, useState, useRef } from 'react'
import { Loader2 } from 'lucide-react'

import { customInstance } from '@/lib/axios'
import { formatPhoneNumber, unformatPhoneNumber } from '@/lib/phoneFormatter'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { useCapsLockWarning } from '@/hooks/useCapsLockWarning'
import { useNicknameAvailability } from '../../-hooks/useNicknameAvailability'

/** 비밀번호: 영어 대소문자, 숫자, 특수문자만. 백엔드 SignupRequest의 @Pattern과 동일하게 맞춘다. */
const PASSWORD_PATTERN = /^[!-~]*$/

type AccountInfoStepProps = {
  phoneNumber: string
  onChangePhoneNumber: (value: string) => void
  password: string
  onChangePassword: (value: string) => void
  nickname: string
  onChangeNickname: (value: string) => void
  onNext: () => void
}

export function AccountInfoStep({
  phoneNumber,
  onChangePhoneNumber,
  password,
  onChangePassword,
  nickname,
  onChangeNickname,
  onNext,
}: AccountInfoStepProps) {
  const [phoneError, setPhoneError] = useState<string | null>(null)
  const [isCheckingPhone, setIsCheckingPhone] = useState(false)
  const [hasRequestedCode, setHasRequestedCode] = useState(false)
  const [verificationCode, setVerificationCode] = useState('')
  const [timerSeconds, setTimerSeconds] = useState(180) // 3분
  const [isGeneratingCode, setIsGeneratingCode] = useState(false)

  const timerRef = useRef<number | null>(null)
  const rawPhone = unformatPhoneNumber(phoneNumber)
  const { capsLockOn, onKeyUp, onBlur } = useCapsLockWarning()

  const { status: nickStatus, isAvailable: isNickAvailable } =
    useNicknameAvailability(nickname)

  // 3분 카운트다운 타이머
  useEffect(() => {
    if (hasRequestedCode && timerSeconds > 0) {
      timerRef.current = window.setInterval(() => {
        setTimerSeconds((prev) => {
          if (prev <= 1) {
            if (timerRef.current) clearInterval(timerRef.current)
            return 0
          }
          return prev - 1
        })
      }, 1000)
    }
    return () => {
      if (timerRef.current) clearInterval(timerRef.current)
    }
  }, [hasRequestedCode, timerSeconds])

  // 가짜 6자리 인증번호 생성 (2초 후 자동 입력)
  const triggerMockCodeGeneration = () => {
    setIsGeneratingCode(true)
    setVerificationCode('')
    setTimeout(() => {
      const mockCode = Math.floor(100000 + Math.random() * 900000).toString()
      setVerificationCode(mockCode)
      setIsGeneratingCode(false)
    }, 2000)
  }

  // 인증하기 버튼 클릭
  const handleRequestVerification = async () => {
    if (rawPhone.length < 10 || isCheckingPhone) return
    setPhoneError(null)
    setIsCheckingPhone(true)

    try {
      const response = await customInstance<{ success: boolean; data: { available: boolean } }>({
        url: '/api/members/check-phone',
        method: 'GET',
        params: { phoneNumber: rawPhone },
      })

      if (response.data?.available) {
        setHasRequestedCode(true)
        setTimerSeconds(180)
        triggerMockCodeGeneration()
      } else {
        setPhoneError('이미 사용 중인 휴대폰 번호입니다')
      }
    } catch {
      setPhoneError('전화번호 확인 중 오류가 발생했습니다.')
    } finally {
      setIsCheckingPhone(false)
    }
  }

  // 재요청 버튼 클릭
  const handleResend = () => {
    setTimerSeconds(180)
    triggerMockCodeGeneration()
  }

  // 남은 시간 mm:ss 포맷팅
  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60)
    const secs = seconds % 60
    return `${mins}:${secs.toString().padStart(2, '0')}`
  }

  // 폼 전체 유효성 검사 (State I)
  const isPhoneValid = rawPhone.length >= 10
  const isCodeValid = hasRequestedCode && verificationCode.length === 6 && timerSeconds > 0
  const isPasswordFormatValid = PASSWORD_PATTERN.test(password)
  const isPasswordValid = password.length >= 1 && password.length <= 20 && isPasswordFormatValid
  const isFormValid = isPhoneValid && isCodeValid && isPasswordValid && isNickAvailable

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (isFormValid) {
      onNext()
    }
  }

  return (
    <div className="flex min-h-dvh flex-col justify-between bg-bg px-6 py-10">
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
            회원가입을 진행해주세요
          </h1>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-5">
          {/* 휴대폰 번호 영역 */}
          <div className="flex flex-col gap-1.5">
            <label className="text-muted-fg text-xs font-semibold">휴대폰 번호</label>
            <div className="flex gap-2">
              <div className="flex h-13 min-w-0 flex-1 items-center rounded-xl border border-border bg-bg px-3.5 transition-colors focus-within:border-primary">
                <span className="text-muted-fg mr-2 flex shrink-0 select-none items-center whitespace-nowrap text-sm font-medium">
                  +82 🇰🇷
                </span>
                <input
                  type="tel"
                  inputMode="numeric"
                  autoFocus
                  placeholder="휴대폰 번호 입력"
                  value={phoneNumber}
                  maxLength={13}
                  onChange={(e) => {
                    setPhoneError(null)
                    onChangePhoneNumber(formatPhoneNumber(e.target.value))
                  }}
                  className="min-w-0 flex-1 bg-transparent text-base text-fg outline-none placeholder:text-muted-fg"
                />
              </div>
              <Button
                type="button"
                variant={hasRequestedCode ? 'outline' : 'default'}
                disabled={rawPhone.length < 10 || isCheckingPhone}
                onClick={handleRequestVerification}
                className="h-13 px-4 rounded-xl text-sm font-semibold shrink-0"
              >
                {isCheckingPhone ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : hasRequestedCode ? (
                  '번호 변경'
                ) : (
                  '인증하기'
                )}
              </Button>
            </div>

            {phoneError && (
              <p role="alert" className="text-down text-xs">
                {phoneError}
              </p>
            )}
          </div>

          {/* 인증번호 입력 영역 (State F-1) */}
          {hasRequestedCode && (
            <div className="flex flex-col gap-1.5 animate-in fade-in slide-in-from-top-2 duration-200">
              <div className="flex h-13 items-center rounded-xl border border-border bg-muted/40 px-3.5 transition-colors focus-within:border-primary">
                <input
                  type="text"
                  readOnly
                  placeholder={isGeneratingCode ? '인증번호 수신 대기 중…' : '인증번호 6자리'}
                  value={verificationCode}
                  aria-label="인증번호"
                  className="min-w-0 flex-1 bg-transparent text-base font-semibold tracking-wider text-fg outline-none select-none cursor-default placeholder:text-muted-fg placeholder:font-normal placeholder:tracking-normal"
                />
                <div className="flex shrink-0 items-center gap-3">
                  <span className={cn('text-xs font-medium', timerSeconds <= 30 ? 'text-down' : 'text-primary')}>
                    {formatTime(timerSeconds)}
                  </span>
                  <button
                    type="button"
                    onClick={handleResend}
                    disabled={isGeneratingCode}
                    className="text-muted-fg hover:text-fg whitespace-nowrap text-xs font-semibold underline underline-offset-2"
                  >
                    재요청
                  </button>
                </div>
              </div>
              {timerSeconds === 0 && (
                <p role="alert" className="text-down text-xs">
                  인증 시간이 만료되었습니다. 재요청 버튼을 눌러주세요.
                </p>
              )}
            </div>
          )}

          {/* 비밀번호 영역 (State G) */}
          <div className="flex flex-col gap-1.5">
            <label className="text-muted-fg text-xs font-semibold">비밀번호</label>
            <div className="flex h-13 items-center rounded-xl border border-border bg-bg px-3.5 transition-colors focus-within:border-primary">
              <input
                type="password"
                placeholder="영어 대소문자/숫자/특수 문자 사용 가능"
                value={password}
                maxLength={20}
                onChange={(e) => onChangePassword(e.target.value)}
                onKeyUp={onKeyUp}
                onBlur={onBlur}
                aria-label="비밀번호"
                className="w-full bg-transparent text-base text-fg outline-none placeholder:text-muted-fg"
              />
            </div>
            {capsLockOn && (
              <p role="alert" className="text-down text-xs">
                Caps Lock이 켜져 있습니다.
              </p>
            )}
            {password && !isPasswordFormatValid && (
              <p role="alert" className="text-down text-xs">
                영문, 숫자, 특수 문자만 사용 가능합니다.
              </p>
            )}
          </div>

          {/* 닉네임 영역 (State H) */}
          <div className="flex flex-col gap-1.5">
            <label className="text-muted-fg text-xs font-semibold">닉네임</label>
            <div className="relative flex h-13 items-center rounded-xl border border-border bg-bg px-3.5 transition-colors focus-within:border-primary">
              <input
                type="text"
                placeholder="2-10자, 한글/영문/숫자 사용 가능"
                value={nickname}
                maxLength={10}
                onChange={(e) => onChangeNickname(e.target.value)}
                aria-label="닉네임"
                className="w-full bg-transparent text-base text-fg outline-none placeholder:text-muted-fg"
              />
              {nickStatus === 'checking' && (
                <Loader2 className="text-muted-fg size-4 animate-spin" />
              )}
            </div>

            {nickStatus === 'taken' && (
              <p role="alert" className="text-down text-xs">
                이미 사용 중인 닉네임입니다
              </p>
            )}
            {nickStatus === 'invalid' && nickname.length > 0 && (
              <p role="alert" className="text-down text-xs">
                2~10자의 한글, 영문, 숫자를 입력해주세요.
              </p>
            )}
            {nickStatus === 'available' && (
              <p className="text-up text-xs font-medium">
                사용할 수 있는 닉네임이에요.
              </p>
            )}
          </div>

          {/* 다음 버튼 */}
          <Button
            type="submit"
            disabled={!isFormValid}
            className="mt-6 h-13 w-full rounded-xl text-base font-semibold transition-opacity"
          >
            다음
          </Button>
        </form>
      </div>
    </div>
  )
}
