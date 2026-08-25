import { Link } from '@tanstack/react-router'
import { UsersRound } from 'lucide-react'

import type { PotSummaryResponse } from '@/api/generated/model'
import { formatDeadline } from '@/routes/pots/-utils/formatDeadline'

type PotCardProps = {
  pot: PotSummaryResponse
  onComplete?: (potId: number) => void
  isCompleting?: boolean
}

export function PotCard({ pot, onComplete, isCompleting }: PotCardProps) {
  return (
    <article className="bg-bg rounded-[20px] border p-5 shadow-sm">
      <Link to="/pots/$potId" params={{ potId: String(pot.potId) }} className="block">
        <div className="flex items-center justify-between gap-3">
          <span className="bg-primary-soft text-primary rounded-full px-2.5 py-1 text-xs font-semibold">
            {formatDeadline(pot.deadline)}
          </span>
          <span className="bg-chip text-muted-fg flex items-center gap-1 rounded-full px-2.5 py-1 text-xs">
            <UsersRound className="size-3.5" />
            {pot.currentMemberCount ?? 0}/{pot.capacity ?? 0}
          </span>
        </div>
        <h3 className="mt-3 text-[17px] font-bold">{pot.storeName}</h3>
        <p className="mt-1 line-clamp-1 text-sm">{pot.title}</p>
        <div className="mt-5 flex items-center justify-between gap-3">
          <p className="min-w-0 text-sm">
            <span className="text-muted-fg mr-3">만날 장소</span>
            <span className="font-semibold">{pot.meetingPlace}</span>
          </p>
          <MemberStack members={pot.members} />
        </div>
      </Link>

      {pot.isHost && (
        <div className="mt-5 grid grid-cols-2 gap-2 border-t pt-4">
          <button
            type="button"
            disabled
            title="내용 수정 API가 준비되면 사용할 수 있어요"
            className="text-muted-fg h-10 rounded-lg border text-sm disabled:opacity-70"
          >
            내용 수정
          </button>
          <button
            type="button"
            disabled={isCompleting || !pot.potId}
            onClick={() => pot.potId && onComplete?.(pot.potId)}
            className="bg-fg/55 text-primary-fg h-10 rounded-lg text-sm font-semibold disabled:opacity-50"
          >
            {isCompleting ? '처리 중…' : '나눔 완료'}
          </button>
        </div>
      )}
    </article>
  )
}

function MemberStack({ members = [] }: Pick<PotSummaryResponse, 'members'>) {
  return (
    <div className="flex shrink-0 -space-x-2" aria-label={`참여자 ${members.length}명`}>
      {members.slice(0, 3).map((member, index) => (
        <span
          key={member.memberId ?? index}
          title={member.nickname}
          className={`border-bg text-primary-fg flex size-6 items-center justify-center rounded-full border-2 text-[10px] font-semibold ${
            index === members.length - 1 ? 'bg-primary' : 'bg-fg/35'
          }`}
        >
          {member.nickname?.trim().charAt(0) || '?'}
        </span>
      ))}
    </div>
  )
}
