package com.delipot.pot.dto;

import java.time.OffsetDateTime;

import com.delipot.pot.Pot;
import com.delipot.pot.PotStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 팟 생성 결과. 프론트는 이 {@code potId}로 상세 화면으로 이동한다.
 * 생성 직후 화면 전환에 필요한 최소 정보만 담는다 — 전체 필드는 상세 조회 API가 준다.
 */
@Schema(description = "팟 생성 응답")
public record PotCreateResponse(

	@Schema(description = "생성된 팟 ID", example = "1")
	Long potId,

	@Schema(description = "팟 상태", example = "RECRUITING")
	PotStatus status,

	@Schema(description = "총대 포함 현재 참여 인원", example = "1")
	int currentMemberCount,

	@Schema(description = "생성 시각")
	OffsetDateTime createdAt
) {

	public static PotCreateResponse from(Pot pot) {
		return new PotCreateResponse(pot.getId(), pot.getStatus(), pot.getCurrentMemberCount(), pot.getCreatedAt());
	}
}
