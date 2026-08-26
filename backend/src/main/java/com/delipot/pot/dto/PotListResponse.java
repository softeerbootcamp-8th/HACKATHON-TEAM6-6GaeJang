package com.delipot.pot.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 홈 목록 응답. 화면의 세 섹션과 1:1로 대응한다. 섹션마다 조회 조건이 달라 한 배열로는 표현할 수 없다.
 * 같은 팟이 두 배열에 동시에 들어가는 일은 없다.
 */
@Schema(description = "홈 목록 응답")
public record PotListResponse(

	@Schema(description = "내가 연 배달팟. 마감시간이 지난 팟도 나눔 완료 전까지 포함된다")
	List<PotSummaryResponse> hosted,

	@Schema(description = "내가 참여중인 배달팟. 마감시간이 지난 팟도 나눔 완료 전까지 포함된다")
	List<PotSummaryResponse> joined,

	@Schema(description = "300m 이내에서 새로 참여할 수 있는 배달팟. 마감 임박순")
	List<PotSummaryResponse> all
) {
}
