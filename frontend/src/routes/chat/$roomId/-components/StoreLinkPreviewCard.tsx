import { ExternalLink } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'

import { cn } from '@/lib/utils'
import { extractStoreName } from '@/api/generated/store/store'

interface StoreLinkPreviewCardProps {
  /** 생성된 API 타입상 옵셔널이지만(스키마 관례), LINK 메시지는 항상 URL을 담고 있다. */
  url: string | undefined
  isMine: boolean
}

function hostnameOf(url: string) {
  try {
    return new URL(url).hostname
  } catch {
    return url
  }
}

/**
 * 팟 생성 시 총대가 넣은 가게 링크 말풍선의 미리보기 카드.
 *
 * 메시지 자체에는 URL만 저장돼 있다(백엔드 계약) — 제목·이미지·설명은 방을 열람할 때 이 컴포넌트가
 * `POST /api/pots/store-name`으로 지연 조회한다. staleTime을 무한으로 두는 이유는 같은 링크를
 * 다시 물어봐도 값이 바뀌지 않아서다(방을 나갔다 들어와도 재요청하지 않는다).
 *
 * 배민 링크·미지원 호스트·통신 실패는 전부 "미리보기 없음"으로 수렴한다 — 실패해도 URL 자체는
 * 항상 눌러서 열 수 있어야 한다.
 */
export function StoreLinkPreviewCard({ url, isMine }: StoreLinkPreviewCardProps) {
  const { data, isPending } = useQuery({
    queryKey: ['storeLinkPreview', url],
    queryFn: ({ signal }) => extractStoreName({ storeUrl: url as string }, undefined, signal),
    staleTime: Infinity,
    retry: false,
    enabled: !!url,
  })

  // 이론상 없을 수 없다(백엔드가 LINK 메시지엔 항상 URL을 채운다) — 타입만 옵셔널이라 방어만 한다.
  if (!url) {
    return null
  }

  const preview = data?.data
  const hasPreview = !!preview?.storeName

  if (isPending) {
    return (
      <div className="h-[88px] w-[250px] animate-pulse rounded-2xl bg-muted" aria-hidden />
    )
  }

  return (
    <a
      href={url}
      target="_blank"
      rel="noreferrer"
      className={cn(
        'flex w-[250px] flex-col overflow-hidden rounded-2xl border text-sm',
        isMine ? 'border-fg/10 bg-fg text-bg' : 'border-border bg-muted text-fg',
      )}
    >
      {hasPreview && preview.imageUrl && (
        <img src={preview.imageUrl} alt="" className="h-28 w-full object-cover" />
      )}
      <div className="flex flex-col gap-1 px-3.5 py-2.5">
        <p className="line-clamp-1 font-semibold break-words">
          {hasPreview ? preview.storeName : '가게 링크'}
        </p>
        {hasPreview && preview.description && (
          <p className={cn('line-clamp-2 text-xs break-words', isMine ? 'text-bg/70' : 'text-muted-fg')}>
            {preview.description}
          </p>
        )}
        <span
          className={cn(
            'flex items-center gap-1 truncate text-xs',
            isMine ? 'text-bg/60' : 'text-muted-fg',
          )}
        >
          <ExternalLink className="size-3 shrink-0" />
          {hasPreview ? hostnameOf(url) : (preview?.reason ?? '링크를 눌러 확인해요')}
        </span>
      </div>
    </a>
  )
}
