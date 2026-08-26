import { useRef, useState, type FormEvent, type PointerEvent as ReactPointerEvent } from 'react'
import { Link, useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { ExternalLink, MapPin, Store, UsersRound, X } from 'lucide-react'

import {
  getGetPotQueryKey,
  getGetPotsQueryKey,
  useGetPot,
  useJoinPot,
} from '@/api/generated/pot/pot'
import type { PotDetailResponse } from '@/api/generated/model'
import { formatDistrictAddress } from '@/lib/addressFormatter'

import { formatDeadline } from '../pots/-utils/formatDeadline'

/** 시트 높이의 35% 이상 끌어내리면 닫힘으로 판단한다. */
const DRAG_CLOSE_THRESHOLD_RATIO = 0.35

type PotDetailSheetProps = {
  potId: number
  onClose: () => void
}

/**
 * 팟 카드를 누르면 홈 화면 위로 올라오는 바텀시트. 상태 표시 영역만 남기고 주소·검색 영역부터
 * 덮어서 상세 내용에 충분한 높이를 확보하며, 손잡이를 끌어내리면 닫힌다.
 */
export function PotDetailSheet({ potId, onClose }: PotDetailSheetProps) {
  const [isEnteringMenu, setIsEnteringMenu] = useState(false)
  const detailQuery = useGetPot(potId, { query: { enabled: Number.isFinite(potId) } })
  const detail = detailQuery.data?.data

  const sheetRef = useRef<HTMLDivElement>(null)
  const drag = useSheetDrag(sheetRef, onClose)

  let body: React.ReactNode
  if (!Number.isFinite(potId)) body = <SheetState error="잘못된 배달팟 주소예요." />
  else if (detailQuery.isPending) body = <SheetState>배달팟을 불러오는 중이에요…</SheetState>
  else if (detailQuery.isError) body = <SheetState error={detailQuery.error.message} />
  else if (!detail) body = <SheetState error="배달팟 정보를 찾을 수 없어요." />
  else if (isEnteringMenu) body = <MenuEntry detail={detail} onClose={() => setIsEnteringMenu(false)} />
  else body = <PotOverview detail={detail} onJoin={() => setIsEnteringMenu(true)} />

  return (
    <div className="absolute inset-0 z-40">
      <div
        className="bg-scrim absolute inset-0"
        style={{ opacity: 1 - drag.progress * 0.6 }}
        role="presentation"
        onClick={onClose}
      />
      <div
        ref={sheetRef}
        className={`sheet-slide-up bg-bg absolute inset-x-0 top-[104px] bottom-0 flex flex-col rounded-t-[34px] pt-3 ${
          drag.isDragging ? '' : 'transition-transform duration-200'
        }`}
        style={{ transform: `translateY(${drag.offset}px)` }}
      >
        <button
          type="button"
          aria-label="배달팟 상세 닫기"
          className="flex shrink-0 touch-none items-center justify-center py-2"
          onPointerDown={drag.onPointerDown}
          onPointerMove={drag.onPointerMove}
          onPointerUp={drag.onPointerUp}
        >
          <span className="bg-border h-1 w-32 rounded-full" />
        </button>
        <div className="min-h-0 flex-1">{body}</div>
      </div>
    </div>
  )
}

/** 손잡이를 눌러 끄는 동작. 끄는 동안은 손가락을 그대로 따라가고, 놓으면 임계값에 따라 닫히거나 되돌아간다. */
function useSheetDrag(sheetRef: React.RefObject<HTMLDivElement | null>, onClose: () => void) {
  const [offset, setOffset] = useState(0)
  const [isDragging, setIsDragging] = useState(false)
  const startY = useRef(0)
  const [sheetHeight, setSheetHeight] = useState(1)

  const onPointerDown = (event: ReactPointerEvent) => {
    event.currentTarget.setPointerCapture(event.pointerId)
    startY.current = event.clientY
    setSheetHeight(sheetRef.current?.clientHeight || 1)
    setIsDragging(true)
  }

  const onPointerMove = (event: ReactPointerEvent) => {
    if (!isDragging) return
    const delta = event.clientY - startY.current
    setOffset(Math.max(0, delta))
  }

  const onPointerUp = () => {
    setIsDragging(false)
    if (offset > sheetHeight * DRAG_CLOSE_THRESHOLD_RATIO) {
      onClose()
    } else {
      setOffset(0)
    }
  }

  return {
    offset,
    isDragging,
    progress: Math.min(1, offset / sheetHeight),
    onPointerDown,
    onPointerMove,
    onPointerUp,
  }
}

function SheetState({ children, error }: { children?: string; error?: string }) {
  return (
    <div className="flex h-full items-center justify-center px-5">
      <p role={error ? 'alert' : undefined} className={`text-center text-sm ${error ? 'text-down' : 'text-muted-fg'}`}>
        {error || children}
      </p>
    </div>
  )
}

function PotOverview({ detail, onJoin }: { detail: PotDetailResponse; onJoin: () => void }) {
  const canJoin =
    detail.status === 'ACTIVE' &&
    !detail.isDeadlinePassed &&
    (detail.currentMemberCount ?? 0) < (detail.capacity ?? 0)
  const chatPath = detail.chatRoomId ? `/chat/${detail.chatRoomId}` : undefined
  const meetingAddress = formatDistrictAddress(
    detail.meetingRoadAddress || detail.meetingPlace,
  )
  const meetingJibunAddress = formatDistrictAddress(detail.meetingJibunAddress)

  return (
    <div className="relative flex h-full flex-col">
      <article className="min-h-0 flex-1 overflow-y-auto px-5 pt-5 pb-28">
        <div className="flex items-center justify-between">
          <span className="bg-primary-soft text-primary rounded-full px-2.5 py-1 text-xs font-semibold">
            {formatDeadline(detail.deadline)}
          </span>
          <span className="bg-chip text-muted-fg flex items-center gap-1 rounded-full px-2.5 py-1 text-xs">
            <UsersRound className="size-3.5" />
            {detail.currentMemberCount ?? 0}/{detail.capacity ?? 0}
          </span>
        </div>
        <h1 className="mt-7 text-lg font-bold">{detail.storeName}</h1>
        <p className="mt-1 text-sm">{detail.title}</p>

        <div className="mt-4 flex items-center gap-2">
          <span className="bg-primary text-primary-fg flex size-7 items-center justify-center rounded-full text-xs font-bold">
            {detail.hostNickname?.charAt(0) || '총'}
          </span>
          <span className="text-muted-fg text-sm">{detail.hostNickname}</span>
          <span className="bg-primary-soft text-primary rounded px-2 py-1 text-xs font-semibold">
            총대 {detail.hostPotCount ?? 0}회
          </span>
        </div>

        <div className="bg-surface mt-6 space-y-4 rounded-[18px] p-5 text-sm">
          <div className="flex items-center gap-3">
            <Store className="text-muted-fg/50 size-5" />
            <span className="text-muted-fg w-14">가게</span>
            <a
              href={detail.storeUrl}
              target="_blank"
              rel="noreferrer"
              className="flex min-w-0 items-center gap-1 font-semibold underline underline-offset-4"
            >
              <span className="truncate">{detail.storeName}</span>
              <ExternalLink className="size-3.5" />
            </a>
          </div>
          <div className="flex items-start gap-3">
            <MapPin className="text-muted-fg/50 mt-0.5 size-5 shrink-0" />
            <span className="text-muted-fg w-14 shrink-0">만날 장소</span>
            <div className="min-w-0">
              <span className="font-semibold">{meetingAddress}</span>
              {/* 지번은 도로명과 다른 값이 있을 때만 보조로 보여준다. */}
              {meetingJibunAddress && meetingJibunAddress !== meetingAddress && (
                <span className="text-muted-fg/70 mt-0.5 block text-xs">
                  {meetingJibunAddress}
                </span>
              )}
            </div>
          </div>
        </div>

        <section className="mt-6 border-t pt-6">
          <h2 className="text-sm font-bold">상세 내용</h2>
          <p className="text-fg/80 mt-4 text-sm leading-6 whitespace-pre-wrap">
            {detail.description || '등록된 상세 설명이 없어요.'}
          </p>
        </section>

        {detail.account && (
          <section className="bg-primary-soft mt-7 rounded-xl p-4">
            <h2 className="text-sm font-bold">정산 계좌</h2>
            <p className="mt-2 text-sm">
              {detail.account.bankName} {detail.account.accountNumber} · {detail.account.accountHolder}
            </p>
          </section>
        )}

        <section className="mt-8">
          <h2 className="text-sm font-bold">현재 참여 멤버</h2>
          <ul className="mt-4 flex flex-wrap gap-7">
            {detail.members?.map((member, index) => (
              <li key={member.memberId ?? index} className="flex max-w-20 flex-col items-center gap-2 text-center">
                <span
                  className={`text-primary-fg flex size-8 items-center justify-center rounded-full text-xs font-bold ${member.isHost ? 'bg-primary' : 'bg-fg/40'}`}
                >
                  {member.nickname?.charAt(0) || '?'}
                </span>
                <span className={`text-[11px] ${member.isHost ? 'text-primary' : 'text-muted-fg'}`}>
                  {member.nickname}
                </span>
              </li>
            ))}
          </ul>
        </section>
      </article>

      <div className="bg-bg absolute inset-x-0 bottom-0 z-20 px-5 pt-3 pb-[max(24px,env(safe-area-inset-bottom))]">
        {detail.isJoined && chatPath ? (
          <Link
            to={chatPath}
            className="bg-primary text-primary-fg flex h-13 w-full items-center justify-center rounded-xl text-sm font-bold"
          >
            참여중인 채팅 보기
          </Link>
        ) : (
          <button
            type="button"
            disabled={!canJoin}
            onClick={onJoin}
            className="bg-primary text-primary-fg disabled:bg-muted-fg/30 h-13 w-full rounded-xl text-sm font-bold"
          >
            {canJoin ? '배달팟 참여하기' : '참여가 마감된 배달팟이에요'}
          </button>
        )}
      </div>
    </div>
  )
}

function MenuEntry({ detail, onClose }: { detail: PotDetailResponse; onClose: () => void }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [menuContent, setMenuContent] = useState('')
  const [menuPrice, setMenuPrice] = useState('')
  const [errorMessage, setErrorMessage] = useState('')
  const join = useJoinPot({
    mutation: {
      onSuccess: (response) => {
        queryClient.invalidateQueries({ queryKey: getGetPotQueryKey(detail.potId ?? 0) })
        queryClient.invalidateQueries({ queryKey: getGetPotsQueryKey() })
        if (response.data?.chatRoomId)
          navigate({ to: '/chat/$roomId', params: { roomId: String(response.data.chatRoomId) } })
        else onClose()
      },
      onError: (error) => setErrorMessage(error.message),
    },
  })

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (!detail.potId) return
    join.mutate({ potId: detail.potId, data: { menuContent, menuPrice: Number(menuPrice) } })
  }

  return (
    <div className="relative flex h-full flex-col">
      <header className="flex shrink-0 items-center justify-center px-5 pb-4">
        <h1 className="text-lg font-bold">메뉴 입력</h1>
        <button
          type="button"
          onClick={onClose}
          aria-label="메뉴 입력 닫기"
          className="absolute right-4 bottom-2 flex size-11 items-center justify-center"
        >
          <X className="size-6" />
        </button>
      </header>
      <form id="join-pot-form" onSubmit={submit} className="min-h-0 flex-1 overflow-y-auto px-5 pt-2 pb-28">
        <p className="text-muted-fg text-sm leading-6">
          가게 링크에서 메뉴를 확인한 뒤, 어떤 메뉴를 어떤 옵션으로 시킬지 아래에 적어주세요.
        </p>
        <a
          href={detail.storeUrl}
          target="_blank"
          rel="noreferrer"
          className="border-primary/30 bg-primary-soft mt-3 flex items-center justify-between rounded-[18px] border p-4"
        >
          <span>
            <strong className="block text-sm">{detail.storeName} 바로가기</strong>
            <span className="text-muted-fg mt-1 block text-xs">배달앱에서 메뉴를 확인해요</span>
          </span>
          <ExternalLink className="text-primary size-5" />
        </a>
        <label className="mt-10 block">
          <span className="text-muted-fg mb-2 block text-sm font-semibold">메뉴 · 옵션</span>
          <textarea
            required
            maxLength={500}
            value={menuContent}
            onChange={(e) => setMenuContent(e.target.value)}
            placeholder="허니콤보 세트 (순살로 변경) + 콜라 제로 500ml"
            className="form-control h-28 resize-none py-4"
          />
        </label>
        <label className="mt-7 block">
          <span className="text-muted-fg mb-2 block text-sm font-semibold">내 금액</span>
          <span className="relative block">
            <input
              required
              min={0}
              type="number"
              inputMode="numeric"
              value={menuPrice}
              onChange={(e) => setMenuPrice(e.target.value)}
              placeholder="12,000"
              className="form-control pr-10 font-bold"
            />
            <span className="text-muted-fg absolute top-1/2 right-4 -translate-y-1/2 text-sm">원</span>
          </span>
        </label>
        {errorMessage && (
          <p role="alert" className="text-down mt-5 text-sm">
            {errorMessage}
          </p>
        )}
      </form>
      <div className="bg-bg absolute inset-x-0 bottom-0 px-5 pt-3 pb-[max(24px,env(safe-area-inset-bottom))]">
        <button
          form="join-pot-form"
          type="submit"
          disabled={join.isPending}
          className="bg-primary text-primary-fg h-13 w-full rounded-xl text-sm font-bold disabled:opacity-50"
        >
          {join.isPending ? '전달하는 중…' : '총대에게 메뉴 전달하기'}
        </button>
      </div>
    </div>
  )
}
