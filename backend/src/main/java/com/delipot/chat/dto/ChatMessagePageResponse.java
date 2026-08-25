package com.delipot.chat.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "커서 기반 메시지 이력 페이지")
public record ChatMessagePageResponse(
	@Schema(description = "메시지 목록 (최신순)") List<ChatMessageResponse> messages,
	@Schema(description = "다음 페이지 조회용 커서. 더 없으면 null") Long nextCursor,
	@Schema(description = "다음 페이지 존재 여부") boolean hasNext
) {
}
