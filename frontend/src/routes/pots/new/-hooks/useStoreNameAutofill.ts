import { useCallback, useRef, useState, type ClipboardEvent } from 'react'

import { useExtractStoreName } from '@/api/generated/store/store'

import { parseShareText } from '../-utils/shareText'

/**
 * 가게 링크에서 가게명을 자동으로 채운다. 두 단계로 시도하고, 둘 다 실패하면 손입력으로 남긴다.
 *
 * 1단 — 붙여넣은 공유 텍스트 파싱({@link parseShareText}). 네트워크를 타지 않는다.
 *   배민·요기요 앱의 공유 버튼이 문구와 함께 복사해 주므로 그 안의 가게명을 쓴다.
 *   배민은 서버에서 가게명을 얻을 방법이 없어(응답에 문자열 자체가 없다) 이 경로가 유일하다.
 * 2단 — 서버 추출(`POST /api/pots/store-name`). 1단에서 이름을 못 얻었을 때만 부른다.
 *   순수 링크만 공유되는 쿠팡이츠가 주로 여기로 온다.
 *
 * 1단을 먼저 두는 이유는 공짜인데 커버리지가 더 넓기 때문이다. 순서를 뒤집으면 배민이 영원히 안 된다.
 *
 * 추출 실패는 에러가 아니다. 서버도 200 + `storeName: null`로 응답하고, 화면은 에러 배너 대신
 * 회색 안내 문구만 띄운 채 가게명 칸을 손입력 상태로 둔다.
 */
type Options = {
  /** 링크만 잘라낸 순수 URL을 폼에 반영한다. */
  onStoreUrl: (storeUrl: string) => void
  /** 추출에 성공했을 때만 호출된다. 실패 시 기존 입력값을 지우지 않는다. */
  onStoreName: (storeName: string) => void
}

type Status = 'idle' | 'loading' | 'filled' | 'failed'

const FALLBACK_FAILURE_HINT = '링크에서 가게명을 가져오지 못했어요. 직접 입력해주세요.'

const HINTS: Record<Status, string | undefined> = {
  idle: undefined,
  loading: '링크에서 가게명을 불러오는 중이에요…',
  filled: '링크에서 가져왔어요 · 직접 수정할 수 있어요',
  failed: undefined,
}

export function useStoreNameAutofill({ onStoreUrl, onStoreName }: Options) {
  const [status, setStatus] = useState<Status>('idle')
  const [failureHint, setFailureHint] = useState(FALLBACK_FAILURE_HINT)

  /**
   * 서버에 물어본 마지막 URL. blur가 여러 번 발생해도(링크 칸을 다시 눌렀다 뗀 경우)
   * 같은 링크로 요청이 반복되지 않게 막는다.
   */
  const lookedUpUrl = useRef('')

  const extract = useExtractStoreName()

  const lookup = useCallback(
    (storeUrl: string) => {
      lookedUpUrl.current = storeUrl
      setStatus('loading')
      extract.mutate(
        { data: { storeUrl } },
        {
          onSuccess: (response) => {
            const storeName = response.data?.storeName
            if (storeName) {
              onStoreName(storeName)
              setStatus('filled')
              return
            }
            // 배민·미지원 호스트가 여기로 온다. 서버가 사용자용 문구까지 함께 내려준다.
            setFailureHint(response.data?.reason || FALLBACK_FAILURE_HINT)
            setStatus('failed')
          },
          // 통신 자체가 실패해도 폼을 막지 않는다. 자동 기입은 편의 기능이다.
          onError: () => {
            setFailureHint(FALLBACK_FAILURE_HINT)
            setStatus('failed')
          },
        },
      )
    },
    [extract, onStoreName],
  )

  /**
   * 링크 칸 붙여넣기를 가로챈다. 공유 문구가 섞인 텍스트를 그대로 두면
   * `<input type="url">` 검증에 걸려 제출이 조용히 막힌다.
   *
   * 링크가 없는 텍스트는 가로채지 않는다 — 사용자가 무엇을 붙여넣었는지 화면에서 볼 수 있어야 한다.
   */
  const handlePaste = useCallback(
    (event: ClipboardEvent<HTMLInputElement>) => {
      const parsed = parseShareText(event.clipboardData.getData('text'))
      if (!parsed) return

      event.preventDefault()
      onStoreUrl(parsed.storeUrl)

      if (parsed.storeName) {
        onStoreName(parsed.storeName)
        lookedUpUrl.current = parsed.storeUrl
        setStatus('filled')
        return
      }
      lookup(parsed.storeUrl)
    },
    [lookup, onStoreName, onStoreUrl],
  )

  /** 링크를 손으로 타이핑해 넣은 경우를 받는다. 타이핑 중에는 요청하지 않는다. */
  const handleBlur = useCallback(
    (storeUrl: string) => {
      const trimmed = storeUrl.trim()
      if (!trimmed || trimmed === lookedUpUrl.current) return
      lookup(trimmed)
    },
    [lookup],
  )

  /** 사용자가 가게명을 직접 고치면 "링크에서 가져왔어요" 안내를 내린다. */
  const handleManualEdit = useCallback(() => setStatus('idle'), [])

  return {
    hint: status === 'failed' ? failureHint : HINTS[status],
    handlePaste,
    handleBlur,
    handleManualEdit,
  }
}
