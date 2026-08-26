package com.delipot.pot.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.delipot.pot.Pot;
import com.delipot.pot.PotStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 팟 상세. 참여 전 첫 진입 화면과 채팅방 상단 헤더가 같이 쓴다.
 *
 * <p>정산 계좌는 참여자에게만 채워 보낸다. 상세 화면은 아직 참여하지 않은 사람에게도 열려 있어서
 * 계좌를 항상 실으면 참여도 안 한 사람에게 계좌번호가 노출된다. 화면상으로도 계좌 배너는
 * 채팅방(=참여 후)에만 있다.
 */
@Schema(description = "팟 상세")
public record PotDetailResponse(

	@Schema(description = "팟 ID", example = "1")
	Long potId,

	@Schema(description = "글 제목", example = "저녁에 같이 치킨 시키실 분 구해요")
	String title,

	@Schema(description = "가게 이름", example = "교촌 치킨 연남점")
	String storeName,

	@Schema(description = "배달앱 가게 링크. 메뉴를 고르러 들어간다")
	String storeUrl,

	@Schema(description = "상세 설명")
	String description,

	@Schema(description = "만날 장소", example = "동진시장 사거리 편의점 앞")
	String meetingPlace,

	@Schema(description = "만날 장소 도로명 주소. 이 필드가 붙기 전 팟은 null", example = "서울 강남구 학동로 171")
	String meetingRoadAddress,

	@Schema(description = "만날 장소 지번 주소. 이 필드가 붙기 전 팟은 null", example = "서울 강남구 논현동 58-3")
	String meetingJibunAddress,

	/*
	 * 좌표를 함께 내리는 이유는 총대의 수정 화면이 만날 장소를 다시 찍지 않고도 그대로 유지할 수
	 * 있어야 해서다(PUT은 전체 값을 다시 보낸다). 주소 문자열이 이미 내려가고 있으므로
	 * 위치 노출 수준은 달라지지 않는다.
	 */
	@Schema(description = "만날 장소 위도", example = "37.5006")
	BigDecimal latitude,

	@Schema(description = "만날 장소 경도", example = "127.0366")
	BigDecimal longitude,

	@Schema(description = "모집 마감시간")
	OffsetDateTime deadline,

	@Schema(description = "최소주문금액(원)", example = "20000")
	int minOrderAmount,

	@Schema(description = "현재 참여 인원", example = "2")
	int currentMemberCount,

	@Schema(description = "모집 정원", example = "4")
	int capacity,

	@Schema(description = "팟 상태", example = "ACTIVE")
	PotStatus status,

	@Schema(description = "채팅방 ID. 채팅방 연동 전까지 null", example = "3")
	Long chatRoomId,

	@Schema(description = "총대 닉네임", example = "연남동자취러")
	String hostNickname,

	@Schema(description = "총대가 지금까지 팟을 연 횟수. 화면의 '총대 N회' 배지", example = "3")
	long hostPotCount,

	@Schema(description = "내가 이 팟의 총대인지", example = "false")
	boolean isHost,

	@Schema(description = "내가 이미 참여했는지. true면 참여하기 버튼 대신 채팅방으로 보낸다", example = "false")
	boolean isJoined,

	@Schema(description = "마감시간이 지났는지. 지나면 새로 참여할 수 없다", example = "false")
	boolean isDeadlinePassed,

	@Schema(description = "참여 멤버 목록")
	List<PotMemberResponse> members,

	@Schema(description = "총대 정산 계좌. 참여자에게만 채워진다")
	AccountResponse account
) {

	@Schema(description = "정산 계좌")
	public record AccountResponse(
		@Schema(description = "은행", example = "카카오뱅크") String bankName,
		@Schema(description = "계좌번호", example = "3333-01-1234567") String accountNumber,
		@Schema(description = "예금주", example = "김하나") String accountHolder
	) {
	}

	public static PotDetailResponse of(
		Pot pot, String hostNickname, long hostPotCount,
		boolean isHost, boolean isJoined, boolean isDeadlinePassed,
		List<PotMemberResponse> members
	) {
		AccountResponse account = isJoined
			? new AccountResponse(pot.getBankName(), pot.getAccountNumber(), pot.getAccountHolder())
			: null;

		return new PotDetailResponse(
			pot.getId(), pot.getTitle(), pot.getStoreName(), pot.getStoreUrl(), pot.getDescription(),
			pot.getMeetingPlace(), pot.getMeetingRoadAddress(), pot.getMeetingJibunAddress(),
			pot.getLatitude(), pot.getLongitude(),
			pot.getDeadline(), pot.getMinOrderAmount(),
			pot.getCurrentMemberCount(), pot.getCapacity(), pot.getStatus(), pot.getChatRoomId(),
			hostNickname, hostPotCount, isHost, isJoined, isDeadlinePassed, members, account
		);
	}
}
