package com.delipot.pot.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.delipot.pot.Pot;
import com.delipot.pot.PotStatus;

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
	int capacity,

	@Schema(description = "팟 상태. 목록에는 ACTIVE만 나온다(DONE은 어느 섹션에도 없다)", example = "ACTIVE")
	PotStatus status,

	@Schema(description = "내가 이 팟의 총대인지. true면 내용 수정·나눔 완료 버튼을 노출한다", example = "false")
	boolean isHost,

	@Schema(description = "이 팟의 채팅방 ID. 채팅방 연동 전까지 null", example = "3")
	Long chatRoomId,

	@Schema(description = "참여자 목록. 카드 우측 아바타로 그린다")
	List<PotMemberResponse> members
) {

	public static PotSummaryResponse of(Pot pot, boolean isHost, List<PotMemberResponse> members) {
		return new PotSummaryResponse(
			pot.getId(),
			pot.getTitle(),
			pot.getStoreName(),
			pot.getDescription(),
			pot.getMeetingPlace(),
			pot.getDeadline(),
			pot.getCurrentMemberCount(),
			pot.getCapacity(),
			pot.getStatus(),
			isHost,
			pot.getChatRoomId(),
			members
		);
	}
}
