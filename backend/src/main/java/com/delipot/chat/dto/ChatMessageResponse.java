package com.delipot.chat.dto;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

import com.delipot.chat.ChatMessage.MessageType;

@Schema(description = "채팅 메시지")
public record ChatMessageResponse(
	@Schema(description = "메시지 id") Long id,
	@Schema(description = "메시지 종류") MessageType type,
	@Schema(description = "보낸 사람 memberId (SYSTEM류는 null)") Long senderId,
	@Schema(description = "내용") String content,
	@Schema(description = "메뉴 금액 (SYSTEM_MENU일 때만 존재)") Integer menuPrice,
	@Schema(description = "전송 시각") OffsetDateTime createdAt
) {
}
