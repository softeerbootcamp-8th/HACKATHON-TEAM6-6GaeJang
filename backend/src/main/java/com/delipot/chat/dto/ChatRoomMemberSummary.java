package com.delipot.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "채팅방 참여자 요약")
public record ChatRoomMemberSummary(
	@Schema(description = "회원 id") Long memberId,
	@Schema(description = "닉네임") String nickname
) {
}
