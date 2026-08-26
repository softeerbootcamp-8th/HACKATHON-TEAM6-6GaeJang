export function formatDeadline(value?: string) {
  if (!value) return '마감 시간 미정'

  const deadline = new Date(value)
  const now = new Date()
  const time = new Intl.DateTimeFormat('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(deadline)
  const sameDay = deadline.toDateString() === now.toDateString()

  return `${sameDay ? '오늘' : `${deadline.getMonth() + 1}/${deadline.getDate()}`} ${time} 마감`
}

export function hasDeadlinePassed(value?: string, now = new Date()) {
  if (!value) return false

  const deadline = new Date(value)
  if (Number.isNaN(deadline.getTime())) return false

  return deadline.getTime() <= now.getTime()
}
