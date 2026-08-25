import { useState } from 'react'
import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, Loader2 } from 'lucide-react'

import { getMeQueryKey, useMe, useUpdateProfile } from '@/api/generated/auth/auth'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { requireAuth } from '@/lib/authGuard'
import { useNicknameAvailability } from '../../-hooks/useNicknameAvailability'
import { AddressSetupStep } from '../../-components/address/AddressSetupStep'
import type { SelectedLocation } from '../../-components/address/KakaoMapPicker'

export const Route = createFileRoute('/my/edit/')({
  beforeLoad: ({ context }) => requireAuth(context.queryClient),
  component: ProfileEditPage,
})

function ProfileEditPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const me = useMe({ query: { retry: false } })
  const member = me.isError ? undefined : me.data?.data

  const [nickname, setNickname] = useState('')
  // 조회가 끝나 닉네임이 처음 도착한 시점을 표시 — 렌더 중 상태를 맞추는 공식 패턴
  // (https://react.dev/learn/you-might-not-need-an-effect#adjusting-some-state-when-a-prop-changes).
  const [loadedNickname, setLoadedNickname] = useState<string | null>(null)
  const [showAddressPicker, setShowAddressPicker] = useState(false)

  if (member && (member.nickname ?? '') !== loadedNickname) {
    setLoadedNickname(member.nickname ?? '')
    setNickname(member.nickname ?? '')
  }

  const nicknameChanged = !!member && nickname !== member.nickname
  // 바뀐 경우에만 중복확인을 묻는다 — 본인의 현재 닉네임은 애초에 검사할 필요가 없다.
  const { status: nickStatus, isAvailable } = useNicknameAvailability(
    nicknameChanged ? nickname : '',
  )
  const canSave = nicknameChanged && isAvailable

  const invalidateMe = () => queryClient.invalidateQueries({ queryKey: getMeQueryKey() })

  const saveNickname = useUpdateProfile({
    mutation: {
      onSuccess: async () => {
        await invalidateMe()
        navigate({ to: '/my' })
      },
    },
  })

  const saveAddress = useUpdateProfile({
    mutation: {
      onSuccess: async () => {
        await invalidateMe()
        setShowAddressPicker(false)
      },
    },
  })

  if (showAddressPicker) {
    const handleAddressComplete = (location: SelectedLocation) => {
      saveAddress.mutate({
        data: {
          address: location.address,
          roadAddress: location.roadAddress,
          jibunAddress: location.jibunAddress,
          latitude: location.latitude,
          longitude: location.longitude,
        },
      })
    }

    return (
      <AddressSetupStep
        onBack={() => setShowAddressPicker(false)}
        onComplete={handleAddressComplete}
        isSubmitting={saveAddress.isPending}
        confirmLabel="완료"
        submittingLabel="저장 중…"
      />
    )
  }

  return (
    <main aria-label="프로필 수정" className="bg-bg mx-auto flex min-h-dvh max-w-[393px] flex-col shadow-xl">
      <header className="flex h-14 items-center justify-between border-b border-border px-4">
        <button
          type="button"
          onClick={() => navigate({ to: '/my' })}
          aria-label="뒤로가기"
          className="hover:bg-muted -ml-2 flex size-10 items-center justify-center rounded-full transition-colors"
        >
          <ChevronLeft className="size-6 text-fg" />
        </button>
        <h2 className="text-base font-bold text-fg">프로필 수정</h2>
        <div className="size-8" />
      </header>

      {me.isPending || !member ? (
        <p className="text-muted-fg px-6 py-10 text-sm">불러오는 중…</p>
      ) : (
        <div className="flex flex-1 flex-col px-6 py-6">
          <div className="flex flex-col gap-1.5">
            <label htmlFor="nickname" className="text-muted-fg text-xs font-semibold">
              닉네임
            </label>
            <div className="relative flex items-center">
              <Input
                id="nickname"
                value={nickname}
                maxLength={10}
                onChange={(event) => setNickname(event.target.value)}
                aria-invalid={nicknameChanged && nickStatus === 'taken'}
                className="h-13 rounded-xl pr-9 text-base"
              />
              {nicknameChanged && nickStatus === 'checking' && (
                <Loader2 className="text-muted-fg absolute right-3 size-4 animate-spin" />
              )}
            </div>
            {nicknameChanged && nickStatus === 'taken' && (
              <p role="alert" className="text-down text-xs">
                이미 사용 중인 닉네임입니다
              </p>
            )}
            {nicknameChanged && nickStatus === 'invalid' && (
              <p role="alert" className="text-down text-xs">
                2~10자의 한글, 영문, 숫자를 입력해주세요.
              </p>
            )}
            {saveNickname.isError && (
              <p role="alert" className="text-down text-xs">
                {saveNickname.error.message}
              </p>
            )}
          </div>

          <div className="mt-6 flex flex-col gap-1.5">
            <label className="text-muted-fg text-xs font-semibold">주소</label>
            <div className="flex h-13 items-center justify-between rounded-xl border border-border bg-bg px-3.5">
              <span className="truncate text-base text-fg">
                {member.roadAddress || member.address}
              </span>
              <Button
                type="button"
                variant="outline"
                onClick={() => setShowAddressPicker(true)}
                className="bg-muted h-8 shrink-0 rounded-lg border-transparent px-3 text-xs font-semibold"
              >
                변경
              </Button>
            </div>
          </div>

          <Button
            type="button"
            disabled={!canSave || saveNickname.isPending}
            onClick={() => saveNickname.mutate({ data: { nickname } })}
            className="mt-auto h-13 w-full rounded-xl text-base font-semibold"
          >
            {saveNickname.isPending ? '저장 중…' : '저장'}
          </Button>
        </div>
      )}
    </main>
  )
}
