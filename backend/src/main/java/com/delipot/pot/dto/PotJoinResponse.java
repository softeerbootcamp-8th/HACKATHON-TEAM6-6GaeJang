package com.delipot.pot.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 참여 결과. 참여하면 곧바로 채팅방으로 넘어가는 흐름이라 {@code chatRoomId}가 필요하다.
 * 이게 없으면 프론트가 참여 직후 목록을 다시 조회해서 방 번호를 찾아야 한다.
 *
 * <p>방은 팟 생성 시점에 이미 만들어져 있어 여기서는 그 id를 그대로 돌려준다.
 * 다만 참여자를 방 멤버로 넣는 것은 아직 붙지 않았다 — 이동은 되지만 메시지 조회는 거부된다.
 */
@Schema(description = "팟 참여 응답")
public record PotJoinResponse(

	@Schema(description = "참여한 팟 ID", example = "1")
	Long potId,

	@Schema(description = "이동할 채팅방 ID. 채팅 연동 이전에 만들어진 팟만 null", example = "3")
	Long chatRoomId,

	@Schema(description = "참여 후 인원", example = "3")
	int currentMemberCount
) {
}
