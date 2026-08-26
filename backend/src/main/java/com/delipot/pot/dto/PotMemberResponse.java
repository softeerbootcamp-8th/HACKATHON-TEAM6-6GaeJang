package com.delipot.pot.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 카드 우측에 겹쳐 그리는 참여자 아바타 한 명. 프로필 이미지가 없어 프론트가 닉네임 첫 글자를 그린다. */
@Schema(description = "팟 참여자")
public record PotMemberResponse(

	@Schema(description = "회원 ID", example = "7")
	Long memberId,

	@Schema(description = "닉네임", example = "연희동주민")
	String nickname,

	@Schema(description = "이 팟의 총대인지. true면 아바타를 주황색으로 그린다", example = "false")
	boolean isHost
) {
}
