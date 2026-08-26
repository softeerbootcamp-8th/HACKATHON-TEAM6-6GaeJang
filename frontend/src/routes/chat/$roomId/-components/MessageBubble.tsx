import { Check } from 'lucide-react'

import { cn } from '@/lib/utils'
import { ChatMessageResponseType } from '@/api/generated/model'
import type { ChatMessageResponse } from '@/api/generated/model'

import { StoreLinkPreviewCard } from './StoreLinkPreviewCard'

interface MessageBubbleProps {
  message: ChatMessageResponse
  isMine: boolean
  nickname?: string
  /** SYSTEM_JOIN 문구에 "(나)"를 붙일지 판단하는 용도. 서버는 입장 메시지에 senderId를 채우지 않아 내용 문자열로 비교한다. */
  myNickname?: string
}

function formatTime(iso?: string) {
  if (!iso) return ''
  return new Date(iso).toLocaleTimeString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

function Avatar({ nickname, isMine }: { nickname?: string; isMine: boolean }) {
  return (
    <div
      className={cn(
        'flex size-9 shrink-0 items-center justify-center rounded-full text-sm font-semibold',
        isMine ? 'bg-primary text-primary-fg' : 'bg-muted text-fg',
      )}
      aria-hidden
    >
      {nickname?.[0] ?? '?'}
    </div>
  )
}

export function MessageBubble({ message, isMine, nickname, myNickname }: MessageBubbleProps) {
  // SYSTEM_JOIN(입장/나눔완료 공지)만 가운데 정렬 안내문이다. SYSTEM_MENU는 제출한 사람이
  // 있는 실체가 있는 내용이라 일반 메시지처럼(색만 다르게) 아바타·닉네임을 달고 보여준다.
  if (message.type === ChatMessageResponseType.SYSTEM_JOIN) {
    // 서버는 입장 메시지에 senderId를 채우지 않아 내용 문자열로만 "내 입장"인지 판별한다.
    const isMyJoin = !!myNickname && message.content === `${myNickname}님이 들어왔어요`
    const content = isMyJoin ? `${myNickname}(나)님이 들어왔어요` : message.content
    return <p className="text-muted-fg py-1 text-center text-xs">{content}</p>
  }

  const time = formatTime(message.createdAt)
  const isImage = message.type === ChatMessageResponseType.IMAGE
  const isMenu = message.type === ChatMessageResponseType.SYSTEM_MENU
  const isLink = message.type === ChatMessageResponseType.LINK

  return (
    <div className={cn('flex items-start gap-2.5', isMine ? 'flex-row-reverse' : 'flex-row')}>
      {!isMine && <Avatar nickname={nickname} isMine={isMine} />}
      <div className={cn('flex flex-col gap-1', isMine ? 'items-end' : 'items-start')}>
        {!isMine && nickname && <span className="text-muted-fg px-0.5 text-xs">{nickname}</span>}
        {isMine && isMenu && (
          <span className="text-muted-fg flex items-center gap-1 px-0.5 text-xs">
            <Check className="size-3.5" />
            메뉴가 자동으로 전달됐어요
          </span>
        )}
        <div className={cn('flex items-end gap-1.5', isMine && 'flex-row-reverse')}>
          {isImage ? (
            <img
              src={message.content}
              alt="전송된 사진"
              className="max-h-64 max-w-[70%] rounded-2xl object-cover"
            />
          ) : isLink ? (
            <StoreLinkPreviewCard url={message.content} isMine={isMine} />
          ) : isMenu ? (
            <div
              className={cn(
                'max-w-[250px] rounded-2xl px-3.5 py-2.5 text-sm',
                isMine ? 'bg-fg text-bg' : 'bg-muted text-fg',
              )}
            >
              <p className="break-words">{message.content}</p>
              {message.menuPrice != null && (
                <p className="mt-1 font-semibold">{message.menuPrice.toLocaleString()}원</p>
              )}
            </div>
          ) : (
            <p
              className={cn(
                'max-w-[250px] rounded-2xl px-3.5 py-2 text-sm break-words',
                isMine ? 'bg-fg text-bg' : 'bg-muted text-fg',
              )}
            >
              {message.content}
            </p>
          )}
          {time && <span className="text-muted-fg shrink-0 text-[11px]">{time}</span>}
        </div>
      </div>
    </div>
  )
}
