package com.delipot.pot.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 카드 우측에 겹쳐 그리는 참여자 아바타 한 명.
 *
 * <p>프로필 이미지 컬럼이 없어 닉네임만 준다. 프론트는 첫 글자를 원 안에 그린다.
 * 이미지가 생기면 필드 추가만 하면 된다 — 추가는 프론트 계약을 깨지 않는다.
 */
@Schema(description = "팟 참여자")
public record PotMemberResponse(

	@Schema(description = "회원 ID", example = "7")
	Long memberId,

	@Schema(description = "닉네임", example = "연희동주민")
	String nickname
) {
}
