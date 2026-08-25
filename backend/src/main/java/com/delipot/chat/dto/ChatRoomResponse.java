package com.delipot.chat.dto;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "채팅방 생성 응답")
public record ChatRoomResponse(
	@Schema(description = "채팅방 id") Long id,
	@Schema(description = "채팅방 이름") String name,
	@Schema(description = "생성 시각") OffsetDateTime createdAt
) {
}
