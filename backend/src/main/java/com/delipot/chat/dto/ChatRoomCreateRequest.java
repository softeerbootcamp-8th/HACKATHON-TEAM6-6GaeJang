package com.delipot.chat.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "채팅방 생성 요청")
public record ChatRoomCreateRequest(
	@Schema(description = "채팅방 이름", example = "배송 #1234 문의")
	@NotBlank String name,

	@Schema(description = "참여자 memberId 목록 (요청자 본인 포함 필수)", example = "[1, 2, 3]")
	@NotEmpty List<Long> memberIds
) {
}
