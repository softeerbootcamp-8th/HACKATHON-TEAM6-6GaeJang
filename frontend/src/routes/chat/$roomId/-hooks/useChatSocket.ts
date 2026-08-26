import { useEffect, useRef, useState } from 'react'
import { Client, type IMessage } from '@stomp/stompjs'

import type { ChatMessageResponse } from '@/api/generated/model'

/**
 * STOMP SEND(/app/rooms/{roomId}/messages) 페이로드.
 * 백엔드 ChatMessageSendRequest는 REST가 아니라 Orval 대상이 아니라서 직접 타입을 맞춘다.
 */
interface ChatMessageSendRequest {
  content: string
}

/** 백엔드 ChatErrorMessage record와 동일한 모양. */
interface ChatErrorMessage {
  code: string
  message: string
}

interface UseChatSocketResult {
  connected: boolean
  error: string | null
  sendMessage: (content: string) => void
}

/**
 * 채팅방 하나에 대한 STOMP 연결. 세션 쿠키는 /ws 핸드셰이크에 자동으로 실리므로
 * 별도 인증 헤더가 필요 없다(WebSocketHandshakeInterceptor 참고).
 */
export function useChatSocket(
  roomId: number,
  onMessage: (message: ChatMessageResponse) => void,
): UseChatSocketResult {
  const [connected, setConnected] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const clientRef = useRef<Client | null>(null)
  const onMessageRef = useRef(onMessage)

  useEffect(() => {
    onMessageRef.current = onMessage
  }, [onMessage])

  useEffect(() => {
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const client = new Client({
      // vite proxy(/ws → localhost:8080, ws:true)와 배포 시 CloudFront가 동일하게 프록시한다.
      brokerURL: `${protocol}://${window.location.host}/ws`,
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true)
        setError(null)

        client.subscribe(`/topic/rooms/${roomId}`, (message: IMessage) => {
          onMessageRef.current(JSON.parse(message.body) as ChatMessageResponse)
        })

        client.subscribe('/user/queue/errors', (message: IMessage) => {
          const body = JSON.parse(message.body) as ChatErrorMessage
          setError(body.message)
        })
      },
      onWebSocketClose: () => setConnected(false),
      onStompError: (frame) => {
        setError(frame.headers.message ?? '채팅 연결에 문제가 발생했습니다.')
      },
    })

    client.activate()
    clientRef.current = client

    return () => {
      clientRef.current = null
      void client.deactivate()
    }
  }, [roomId])

  const sendMessage = (content: string) => {
    const client = clientRef.current
    if (!client?.connected) return

    const payload: ChatMessageSendRequest = { content }
    client.publish({
      destination: `/app/rooms/${roomId}/messages`,
      body: JSON.stringify(payload),
    })
  }

  return { connected, error, sendMessage }
}
