package com.delipot.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "전화번호 중복확인 응답")
public record PhoneAvailabilityResponse(
	@Schema(description = "가입 가능 여부 (true면 중복 아님, 가입 가능)", example = "true")
	boolean available
) {
}
