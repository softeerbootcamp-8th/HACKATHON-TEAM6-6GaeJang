import { useEffect, useRef } from 'react'
import { Client } from '@stomp/stompjs'

/**
 * 채팅방 목록 화면 전용 구독. 방 상세 화면의 useChatSocket을 방 개수만큼 그대로 붙이면
 * 커넥션이 N개로 늘어나므로, 커넥션 하나로 내가 속한 모든 방 topic(/topic/rooms/{roomId})을
 * 구독해 어느 방이든 새 메시지가 오면 알려준다. 목록 화면은 메시지 내용 자체가 필요 없고
 * "새로고침해야 할 시점"만 알면 되므로 페이로드는 호출자에게 넘기지 않는다.
 */
export function useChatRoomsSocket(roomIds: number[], onMessage: () => void) {
  const onMessageRef = useRef(onMessage)
  const roomIdsKey = roomIds
    .slice()
    .sort((a, b) => a - b)
    .join(',')

  useEffect(() => {
    onMessageRef.current = onMessage
  }, [onMessage])

  useEffect(() => {
    if (roomIdsKey === '') return

    const ids = roomIdsKey.split(',').map(Number)
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    let hasConnected = false
    const client = new Client({
      brokerURL: `${protocol}://${window.location.host}/ws`,
      reconnectDelay: 3000,
      onConnect: () => {
        for (const roomId of ids) {
          client.subscribe(`/topic/rooms/${roomId}`, () => {
            onMessageRef.current()
          })
        }

        // 소켓은 "지금부터 오는 것"만 전달한다. 끊겨 있던 동안 도착한 메시지는 아무도 알려주지
        // 않으므로 재연결 시 목록을 한 번 다시 받아 공백을 메운다. 최초 연결 때는 목록을 막
        // 받아온 직후라 건너뛴다.
        if (hasConnected) onMessageRef.current()
        hasConnected = true
      },
    })

    client.activate()

    return () => {
      void client.deactivate()
    }
  }, [roomIdsKey])
}
