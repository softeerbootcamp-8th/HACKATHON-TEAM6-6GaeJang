package com.delipot.chat.dto;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 채팅방 목록의 항목 하나")
public record ChatRoomSummaryResponse(
	@Schema(description = "채팅방 id") Long roomId,
	@Schema(description = "채팅방 이름") String name,
	@Schema(description = "마지막 메시지 미리보기 (메시지가 없으면 null)") String lastMessagePreview,
	@Schema(description = "마지막 메시지 시각 (메시지가 없으면 null)") OffsetDateTime lastMessageAt,
	@Schema(description = "내가 안 읽은 메시지 개수") long unreadCount
) {
}
