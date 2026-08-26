package com.delipot.pot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 참여 = 메뉴 전달. "총대에게 메뉴 전달하기" 버튼이 이 본문으로 참여 API를 부른다.
 * 메뉴 없이 참여하는 경로는 두지 않는다 — 메뉴 없는 참여자가 정원을 차지하면 최소주문금액을 못 채운다.
 */
@Schema(description = "팟 참여 요청 (메뉴 입력)")
public record PotJoinRequest(

	@Schema(description = "메뉴·옵션 자유 입력. 참여 기록에 저장되고 총대가 주문할 때 읽는다",
		example = "허니콤보 세트 (순살로 변경) + 콜라 제로 500ml")
	@NotBlank(message = "메뉴를 입력해주세요.")
	@Size(max = 500, message = "메뉴는 500자 이하여야 합니다.")
	String menuContent,

	@Schema(description = "내가 낼 금액(원)", example = "12000")
	@NotNull(message = "금액을 입력해주세요.")
	@Min(value = 0, message = "금액은 0원 이상이어야 합니다.")
	Integer menuPrice
) {
}
