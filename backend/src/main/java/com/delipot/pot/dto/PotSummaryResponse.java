package com.delipot.pot.dto;

import java.time.OffsetDateTime;

import com.delipot.pot.Pot;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 홈 목록의 카드 한 장. 화면에 실제로 그려지는 값만 담는다.
 *
 * <p>가게 링크·정산 계좌는 여기 없다. 목록은 참여 여부와 무관하게 열려 있으므로
 * 계좌 같은 값이 섞이면 안 되고, 링크는 상세 화면에서만 쓴다.
 */
@Schema(description = "팟 목록 카드")
public record PotSummaryResponse(

	@Schema(description = "팟 ID", example = "1")
	Long potId,

	@Schema(description = "글 제목", example = "저녁에 같이 치킨 시키실 분 구해요")
	String title,

	@Schema(description = "가게 이름", example = "교촌 치킨 연남점")
	String storeName,

	@Schema(description = "상세 설명")
	String description,

	@Schema(description = "만날 장소", example = "동진시장 사거리 편의점 앞")
	String meetingPlace,

	@Schema(description = "모집 마감시간")
	OffsetDateTime deadline,

	@Schema(description = "현재 참여 인원", example = "2")
	int currentMemberCount,

	@Schema(description = "모집 정원", example = "4")
	int capacity
) {

	public static PotSummaryResponse from(Pot pot) {
		return new PotSummaryResponse(
			pot.getId(),
			pot.getTitle(),
			pot.getStoreName(),
			pot.getDescription(),
			pot.getMeetingPlace(),
			pot.getDeadline(),
			pot.getCurrentMemberCount(),
			pot.getCapacity()
		);
	}
}
