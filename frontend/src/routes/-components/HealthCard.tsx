import { RefreshCw } from 'lucide-react'

import { useHealth } from '@/api/generated/health/health'
import { HealthResponseStatus } from '@/api/generated/model'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { cn } from '@/lib/utils'

export function HealthCard() {
  const { data, error, isPending, isFetching, refetch } = useHealth({
    query: { refetchInterval: 10_000 },
  })

  const health = data?.data

  return (
    <Card>
      <CardHeader>
        <div className="flex items-start justify-between gap-4">
          <div>
            <CardTitle>백엔드 연결 상태</CardTitle>
            <CardDescription>GET /api/health · 10초마다 자동 갱신</CardDescription>
          </div>
          <Button
            variant="outline"
            size="icon"
            onClick={() => void refetch()}
            disabled={isFetching}
            aria-label="상태 다시 확인"
          >
            <RefreshCw className={cn('size-4', isFetching && 'animate-spin')} />
          </Button>
        </div>
      </CardHeader>
      <CardContent>
        {isPending && <p className="text-muted-fg text-sm">확인 중…</p>}

        {error && (
          <p role="alert" className="text-down text-sm">
            백엔드에 연결할 수 없다. 8080 포트에서 서버가 떠 있는지 확인해라.
          </p>
        )}

        {health && (
          <dl aria-live="polite" className="grid grid-cols-2 gap-x-6 gap-y-3 text-sm">
            <Row label="서버" value={<StatusText status={health.status} />} />
            <Row label="DB" value={<StatusText status={health.database} />} />
            <Row label="프로파일" value={health.profile} />
            <Row label="버전" value={health.version} />
            <Row
              label="서버 시각"
              value={health.serverTime ? new Date(health.serverTime).toLocaleString('ko-KR') : '-'}
            />
          </dl>
        )}
      </CardContent>
    </Card>
  )
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-muted-fg text-xs">{label}</dt>
      <dd className="font-medium">{value ?? '-'}</dd>
    </div>
  )
}

function StatusText({ status }: { status?: HealthResponseStatus }) {
  const isUp = status === HealthResponseStatus.UP
  return (
    <span className={cn('font-semibold', isUp ? 'text-up' : 'text-down')}>{status ?? '?'}</span>
  )
}
