package com.delipot.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * STOMP SEND(/app/rooms/{roomId}/messages) 페이로드.
 * REST가 아니라 OpenAPI/Orval 대상이 아니다 — 프론트는 이 모양을 stompjs 쪽에 직접 타입으로 맞춰야 한다.
 */
public record ChatMessageSendRequest(
	@NotBlank @Size(max = 2000) String content
) {
}
