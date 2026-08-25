package com.delipot.pot.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 목록 응답.
 *
 * <p>배열을 그대로 내리지 않고 객체로 감싼다. 나중에 페이징 정보(전체 건수, 다음 커서)를 붙일 때
 * 필드 추가만으로 끝나기 때문이다. 배열로 내려두면 그때 프론트 계약을 깨야 한다.
 * 반대로 지금 필요 없는 필드는 넣지 않는다 — 필드 추가는 계약을 깨지 않지만 제거는 깬다.
 */
@Schema(description = "팟 목록 응답")
public record PotListResponse(

	@Schema(description = "마감 임박순으로 정렬된 팟 목록")
	List<PotSummaryResponse> pots
) {
}
