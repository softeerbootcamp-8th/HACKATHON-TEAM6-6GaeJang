import { useState } from 'react'
import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'

import { getGetPotsQueryKey, useCreatePot } from '@/api/generated/pot/pot'
import { requireAuth } from '@/lib/authGuard'

import { emptyPotFormValues, PotForm } from '../-components/PotForm'

export const Route = createFileRoute('/pots/new/')({
  beforeLoad: ({ context }) => requireAuth(context.queryClient),
  component: NewPotPage,
})

function NewPotPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [errorMessage, setErrorMessage] = useState('')

  const createPot = useCreatePot({
    mutation: {
      onSuccess: (response) => {
        void queryClient.invalidateQueries({ queryKey: getGetPotsQueryKey() })
        const potId = response.data?.potId
        navigate({ to: '/', search: potId ? { openPotId: potId } : {} })
      },
      onError: (error) => setErrorMessage(error.message),
    },
  })

  return (
    <main
      aria-label="새 배달팟 만들기"
      className="bg-bg mx-auto h-dvh max-w-[393px] overflow-hidden shadow-xl"
    >
      <PotForm
        heading="새 배달팟 만들기"
        submitLabel="팟 만들기"
        submittingLabel="만드는 중…"
        initialValues={emptyPotFormValues}
        isSubmitting={createPot.isPending}
        externalError={errorMessage}
        onSubmit={(data) => {
          setErrorMessage('')
          createPot.mutate({ data })
        }}
        onClose={() => navigate({ to: '/' })}
      />
    </main>
  )
}
