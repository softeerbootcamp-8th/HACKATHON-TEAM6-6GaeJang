import { createFileRoute, useNavigate, useParams } from '@tanstack/react-router'

import { requireAuth } from '@/lib/authGuard'
import { PotDetailSheet } from '../../-components/PotDetailSheet'

export const Route = createFileRoute('/pots/$potId/')({
  beforeLoad: ({ context }) => requireAuth(context.queryClient),
  component: PotDetailPage,
})

/**
 * 공유 링크 등으로 직접 들어왔을 때를 위한 진입점. 홈에서 카드를 눌렀을 때는 이 라우트로
 * 이동하지 않고 홈 화면 위에 {@link PotDetailSheet}를 오버레이로 띄운다 — 뒤에 홈 화면이
 * 그대로 보여야 하기 때문이다. 직접 진입 시에는 보여줄 배경이 없어 무채색 배경으로 대신한다.
 */
function PotDetailPage() {
  const { potId: potIdParam } = useParams({ from: '/pots/$potId/' })
  const navigate = useNavigate()

  return (
    <main
      aria-label="배달팟 상세"
      className="bg-muted relative mx-auto h-dvh max-w-[393px] overflow-hidden shadow-xl"
    >
      <PotDetailSheet potId={Number(potIdParam)} onClose={() => navigate({ to: '/' })} />
    </main>
  )
}
