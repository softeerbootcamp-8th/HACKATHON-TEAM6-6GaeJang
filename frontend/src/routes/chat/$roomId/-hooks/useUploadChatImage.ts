import { useMutation } from '@tanstack/react-query'

import { customInstance } from '@/lib/axios'
import type { ApiResponseChatMessageResponse } from '@/api/generated/model'

/**
 * Orval이 멀티파트 요청 바디를 JSON처럼 취급해서 생성해버려(Content-Type: application/json +
 * 평범한 객체) 그대로 못 쓴다. customInstance는 그대로 재사용하되 FormData를 직접 만들어
 * 보낸다 — axios가 FormData를 보면 알아서 multipart 경계(boundary)를 붙여준다.
 */
function uploadChatImage(roomId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return customInstance<ApiResponseChatMessageResponse>({
    url: `/api/chat/rooms/${roomId}/images`,
    method: 'POST',
    data: formData,
  })
}

export function useUploadChatImage() {
  return useMutation({
    mutationFn: ({ roomId, file }: { roomId: number; file: File }) => uploadChatImage(roomId, file),
  })
}
