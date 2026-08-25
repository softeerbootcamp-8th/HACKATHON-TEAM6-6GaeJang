package com.delipot.pot.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 홈 목록 응답. 화면의 세 섹션과 1:1로 대응한다.
 *
 * <p>한 배열로 내리고 프론트가 나누게 하지 않는 이유는 섹션마다 조회 조건이 실제로 다르기 때문이다
 * ({@code hosted}/{@code joined}는 반경도 마감시간도 보지 않고, {@code all}은 반경 300m +
 * 마감 전 + 정원 여유). 한 배열로는 이 차이를 표현할 수 없다.
 *
 * <p>같은 팟이 두 배열에 동시에 들어가는 일은 없다. 총대면 {@code hosted}, 참여자면 {@code joined},
 * 둘 다 아니면 {@code all}이다.
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
