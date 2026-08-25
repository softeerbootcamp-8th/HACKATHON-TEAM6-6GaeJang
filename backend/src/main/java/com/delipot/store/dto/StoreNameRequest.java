package com.delipot.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 가게명 추출 요청.
 *
 * <p>여기 오는 값은 이미 프론트에서 링크만 잘라낸 순수 URL이다. 배민·요기요 앱의 공유 버튼은
 * {@code '호백반점 본점' 어때요? 배달의민족 앱에서...\nhttps://s.baemin.com/...} 처럼 문구까지 함께
 * 복사되므로, 붙여넣기를 가로채 URL만 추출하는 일은 프론트가 한다.
 *
 * <p>{@code @Pattern}으로 스킴을 강제하지 않고 서비스에서 URI 파싱으로 확인하는 이유는, 형식이
 * 틀린 링크도 400이 아니라 "추출 실패"로 흘려보내야 하기 때문이다 — 붙여넣는 중간 상태에서
 * 에러 배너가 뜨면 안 된다. 길이 상한만 여기서 막는다.
 */
@Schema(description = "가게명 추출 요청")
public record StoreNameRequest(

	@Schema(description = "배달앱 가게 링크", example = "https://web.coupangeats.com/share?storeId=781313")
	@NotBlank(message = "가게 링크는 필수입니다.")
	@Size(max = 500, message = "가게 링크는 500자 이하여야 합니다.")
	String storeUrl
) {
}
