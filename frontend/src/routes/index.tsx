import { createFileRoute } from '@tanstack/react-router'

import { HealthCard } from './-components/HealthCard'

export const Route = createFileRoute('/')({
  component: HomePage,
})

function HomePage() {
  return (
    <main aria-label="프로젝트 상태" className="mx-auto max-w-3xl px-4 py-10">
      <h1 className="text-2xl font-semibold">프로젝트 틀 확인</h1>
      <p className="text-muted-fg mt-1 text-sm">
        프론트 → 백엔드 → DB 까지 연결됐는지 헬스체크로 확인한다.
      </p>
      <div className="mt-6">
        <HealthCard />
      </div>
    </main>
  )
}
