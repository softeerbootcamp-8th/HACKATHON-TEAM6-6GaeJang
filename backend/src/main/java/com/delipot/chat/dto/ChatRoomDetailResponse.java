package com.delipot.chat.dto;

import java.time.OffsetDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "채팅방 상세 (헤더용)")
public record ChatRoomDetailResponse(
	@Schema(description = "채팅방 id") Long id,
	@Schema(description = "채팅방 이름") String name,
	@Schema(description = "만날 장소 (없으면 null)") String location,
	@Schema(description = "참여자 수") int memberCount,
	@Schema(description = "참여자 목록") List<ChatRoomMemberSummary> members,
	@Schema(description = "생성 시각") OffsetDateTime createdAt
) {
}
