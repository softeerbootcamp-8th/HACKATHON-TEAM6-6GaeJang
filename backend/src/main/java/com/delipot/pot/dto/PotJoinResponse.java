package com.delipot.pot.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 참여 결과. 참여하면 곧바로 채팅방으로 넘어가는 흐름이라 {@code chatRoomId}가 필요하다.
 * 이게 없으면 프론트가 참여 직후 목록을 다시 조회해서 방 번호를 찾아야 한다.
 *
 * <p>채팅방 생성·입장은 채팅 담당자 작업이라 아직 이 값을 채우는 코드가 없다(null).
 * 필드를 미리 열어두는 이유는 채팅 쪽이 붙을 때 응답 계약을 다시 깨지 않기 위해서다.
 */
@Schema(description = "팟 참여 응답")
public record PotJoinResponse(

	@Schema(description = "참여한 팟 ID", example = "1")
	Long potId,

	@Schema(description = "이동할 채팅방 ID. 채팅방 연동 전까지 null", example = "3")
	Long chatRoomId,

	@Schema(description = "참여 후 인원", example = "3")
	int currentMemberCount
) {
}
