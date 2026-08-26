import { useState } from 'react'
import { createFileRoute, useNavigate, useParams } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'

import {
  getGetPotQueryKey,
  getGetPotsQueryKey,
  useExpandRecruitment,
  useGetPot,
  useUpdatePot,
} from '@/api/generated/pot/pot'
import { requireAuth } from '@/lib/authGuard'
import type { PotDetailResponse } from '@/api/generated/model'

import { PotForm, type PotFormInitialValues } from '../../-components/PotForm'
import { RecruitmentForm } from '../../-components/RecruitmentForm'

/** 마감 시간 선택 휠이 30분 단위라, 남은 시간을 역산할 때도 같은 단위로 맞춘다. */
const MINUTE_STEP = 30

export const Route = createFileRoute('/pots/$potId/edit/')({
  beforeLoad: ({ context }) => requireAuth(context.queryClient),
  component: EditPotPage,
})

/**
 * 배달팟 정보 수정. 홈 카드의 "내용 수정"에서 들어온다.
 *
 * <p>참여자 유무에 따라 화면과 API가 갈린다.
 *
 * <ul>
 *   <li>총대 혼자 — 전체 폼 + {@code PUT /api/pots/{potId}}. 무엇이든 바꿀 수 있다.</li>
 *   <li>참여자 있음 — 잠금 화면 + {@code PATCH /api/pots/{potId}/recruitment}.
 *       배달팟 인원·마감 시간만, 그것도 늘리는 방향으로만 바뀐다.</li>
 * </ul>
 *
 * <p>이 화면을 열어 둔 사이 누가 참여하면 판정이 뒤집힌다. 그때는 전체 수정 저장이 서버에서
 * POT_NOT_EDITABLE로 거부된다 — 화면이 미리 알 방법이 없으므로 저장 시점의 서버 판정이 정본이다.
 */
function EditPotPage() {
  const { potId: potIdParam } = useParams({ from: '/pots/$potId/edit/' })
  const potId = Number(potIdParam)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [errorMessage, setErrorMessage] = useState('')

  const { data, isPending, error } = useGetPot(potId)
  const pot = data?.data

  const invalidatePot = () => {
    void queryClient.invalidateQueries({ queryKey: getGetPotsQueryKey() })
    void queryClient.invalidateQueries({ queryKey: getGetPotQueryKey(potId) })
  }

  const backToPot = () => navigate({ to: '/', search: { openPotId: potId } })
  const revealUpdatedPot = () => navigate({ to: '/', search: { revealPotId: potId } })

  const expandRecruitment = useExpandRecruitment({
    mutation: {
      onSuccess: () => {
        invalidatePot()
        revealUpdatedPot()
      },
      onError: (mutationError) => setErrorMessage(mutationError.message),
    },
  })

  const updatePot = useUpdatePot({
    mutation: {
      onSuccess: () => {
        invalidatePot()
        revealUpdatedPot()
      },
      onError: (mutationError) => setErrorMessage(mutationError.message),
    },
  })

  if (isPending) {
    return <EditPotShell>{null}</EditPotShell>
  }

  if (error || !pot) {
    return (
      <EditPotShell>
        <p role="alert" className="text-down px-5 pt-10 text-sm">
          {error?.message || '배달팟 정보를 불러오지 못했어요.'}
        </p>
      </EditPotShell>
    )
  }

  // 총대는 항상 참여자로 세어지므로, 2명부터가 "다른 사람이 들어온" 상태다.
  const hasParticipants = (pot.currentMemberCount ?? 1) > 1

  if (hasParticipants) {
    return (
      <EditPotShell>
        <RecruitmentForm
          pot={pot}
          isSubmitting={expandRecruitment.isPending}
          externalError={errorMessage}
          onSubmit={(data) => {
            setErrorMessage('')
            expandRecruitment.mutate({ potId, data })
          }}
          onClose={backToPot}
        />
      </EditPotShell>
    )
  }

  return (
    <EditPotShell>
      <PotForm
        heading="배달팟 정보 수정"
        submitLabel="저장"
        submittingLabel="저장 중…"
        initialValues={toInitialValues(pot)}
        isSubmitting={updatePot.isPending}
        externalError={errorMessage}
        onSubmit={(request) => {
          setErrorMessage('')
          updatePot.mutate({ potId, data: request })
        }}
        onClose={backToPot}
      />
    </EditPotShell>
  )
}

function EditPotShell({ children }: { children: React.ReactNode }) {
  return (
    <main
      aria-label="배달팟 정보 수정"
      className="bg-bg mx-auto h-dvh max-w-[393px] overflow-hidden shadow-xl"
    >
      {children}
    </main>
  )
}

/**
 * 상세 응답을 폼 초기값으로 옮긴다.
 *
 * <p>계좌는 참여자에게만 내려오는 필드지만 총대는 항상 참여자로 기록돼 있어 채워져 온다.
 * 그래도 옵셔널로 다루는 이유는 생성된 타입이 전 필드 옵셔널이기 때문이다 — 빈 문자열로
 * 떨어뜨리면 폼의 required가 잡아 준다.
 */
function toInitialValues(pot: PotDetailResponse): PotFormInitialValues {
  return {
    fields: {
      title: pot.title ?? '',
      storeName: pot.storeName ?? '',
      storeUrl: pot.storeUrl ?? '',
      capacity: pot.capacity ?? 2,
      description: pot.description ?? '',
      bankName: pot.account?.bankName ?? '',
      accountNumber: pot.account?.accountNumber ?? '',
      accountHolder: pot.account?.accountHolder ?? '',
    },
    meetingLocation:
      pot.latitude != null && pot.longitude != null
        ? {
            address: pot.meetingPlace ?? '',
            roadAddress: pot.meetingRoadAddress ?? '',
            jibunAddress: pot.meetingJibunAddress ?? '',
            latitude: pot.latitude,
            longitude: pot.longitude,
          }
        : null,
    remaining: remainingUntil(pot.deadline),
  }
}

/**
 * 저장된 절대 마감시각을 "지금부터 N시간 M분"으로 되돌린다. 폼이 상대값으로만 마감을 다루기
 * 때문에 필요한 변환이다.
 *
 * <p>이미 지난 마감은 null로 돌려준다 — 0시간 0분을 채워 두면 총대가 마감 칸을 건드리지 않고
 * 저장해 서버의 "최소 30분" 규칙에 걸린다. null이면 폼이 "마감 시간을 선택해주세요"로 남긴다.
 */
function remainingUntil(deadline: string | undefined) {
  if (!deadline) return null

  const remainingMinutes = Math.round((new Date(deadline).getTime() - Date.now()) / 60_000)
  if (remainingMinutes < MINUTE_STEP) return null

  const stepped = Math.floor(remainingMinutes / MINUTE_STEP) * MINUTE_STEP
  return { hours: Math.floor(stepped / 60), minutes: stepped % 60 }
}
