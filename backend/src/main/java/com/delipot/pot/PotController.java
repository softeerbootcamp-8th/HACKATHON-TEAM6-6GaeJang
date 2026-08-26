package com.delipot.pot;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.delipot.auth.LoginMember;
import com.delipot.auth.RequireAuthenticate;
import com.delipot.global.response.ApiResponse;
import com.delipot.pot.dto.PotCreateRequest;
import com.delipot.pot.dto.PotCreateResponse;
import com.delipot.pot.dto.PotDetailResponse;
import com.delipot.pot.dto.PotJoinRequest;
import com.delipot.pot.dto.PotJoinResponse;
import com.delipot.pot.dto.PotListRequest;
import com.delipot.pot.dto.PotListResponse;
import com.delipot.pot.dto.PotUpdateRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Pot", description = "배달팟 모집")
@RestController
@RequestMapping("/api/pots")
@RequiredArgsConstructor
public class PotController {

	private final PotService potService;

	@Operation(
		summary = "팟 생성",
		description = "총대가 가게 링크·만날 장소·정원·마감시간·정산 계좌를 넣어 배달팟을 만든다. "
			+ "총대는 로그인한 회원으로 고정되며 요청 본문으로 지정할 수 없다. "
			+ "생성 직후 상태는 ACTIVE이고 총대 본인이 첫 참여자로 잡힌다. "
			+ "총대 혼자 있는 채팅방이 함께 만들어지고 그 id가 chatRoomId로 내려간다."
	)
	@RequireAuthenticate
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<PotCreateResponse> createPot(
		@LoginMember Long hostId,
		@Valid @RequestBody PotCreateRequest request
	) {
		return ApiResponse.ok(potService.create(hostId, request));
	}

	@Operation(
		summary = "팟 수정",
		description = "총대가 '내용 수정'으로 들어와 생성 폼과 같은 전체 값을 다시 보낸다. "
			+ "아직 참여자가 없는(총대 혼자인) ACTIVE 팟만 수정할 수 있다 — "
			+ "참여자가 한 명이라도 있으면 POT_NOT_EDITABLE. 이미 전달된 메뉴가 다른 가게 기준이 되고 "
			+ "참여자가 보고 들어온 계좌·장소가 조용히 바뀌기 때문이다. "
			+ "총대가 아니면 POT_ACCESS_DENIED, 나눔이 끝난 팟이면 POT_NOT_ACTIVE. "
			+ "수정 폼을 열어 둔 사이 누가 참여하면 낙관적 락에 걸려 CONFLICT로 실패한다. "
			+ "가게명·만날 장소가 바뀌면 연결된 채팅방의 이름·장소도 함께 갱신된다. "
			+ "본문 없이 성공만 응답한다 — 프론트는 저장 후 목록·상세를 새로 조회한다."
	)
	@RequireAuthenticate
	@PutMapping("/{potId}")
	public ApiResponse<Void> updatePot(
		@LoginMember Long memberId,
		@PathVariable Long potId,
		@Valid @RequestBody PotUpdateRequest request
	) {
		potService.update(memberId, potId, request);
		return ApiResponse.ok();
	}

	@Operation(
		summary = "홈 목록 조회",
		description = "홈 화면의 세 섹션을 한 번에 준다. "
			+ "hosted(내가 연 배달팟) / joined(참여중인 배달팟)은 반경도 마감시간도 보지 않는다 — "
			+ "마감 후가 주문·입금·수령 구간이라 참여자에게는 계속 보여야 하고, 사라지는 조건은 나눔 완료뿐이다. "
			+ "all(전체 배달팟)은 내 인증 주소 기준 300m 이내에서 마감 전이고 정원이 남았으며 "
			+ "내가 속하지 않은 팟만 마감 임박순으로 준다. "
			+ "나눔 완료(DONE)된 팟은 어디에도 나오지 않는다. 총대가 완료를 누르지 않아도 "
			+ "마감시간 + 5시간이 지나면 자동으로 완료 처리된다. "
			+ "keyword를 주면 세 섹션 모두 가게 이름으로 거른다. "
			+ "좌표는 가입 시 인증한 주소에서 서버가 직접 읽으므로 요청에 넣지 않는다."
	)
	@RequireAuthenticate
	@GetMapping
	public ApiResponse<PotListResponse> getPots(
		@LoginMember Long memberId,
		@Valid @ParameterObject @ModelAttribute PotListRequest request
	) {
		return ApiResponse.ok(potService.findPots(memberId, request));
	}

	@Operation(
		summary = "팟 상세 조회",
		description = "참여 전 첫 진입 화면과 채팅방 상단 헤더가 함께 쓴다. "
			+ "총대 닉네임과 '총대 N회' 배지, 가게 링크, 상세 설명, 참여 멤버 목록을 준다. "
			+ "정산 계좌(account)는 참여자에게만 채워진다 — 참여 전 화면에도 열려 있는 API라 "
			+ "항상 실으면 참여하지 않은 사람에게 계좌번호가 노출된다. "
			+ "나눔 완료된 팟도 조회된다(채팅방은 완료 후에도 남고 그 화면 상단이 이 API를 쓴다)."
	)
	@RequireAuthenticate
	@GetMapping("/{potId}")
	public ApiResponse<PotDetailResponse> getPot(@LoginMember Long memberId, @PathVariable Long potId) {
		return ApiResponse.ok(potService.findDetail(memberId, potId));
	}

	@Operation(
		summary = "채팅방 기준 팟 상세 조회",
		description = "채팅방 헤더/배너가 potId 없이 roomId만 가진 경우를 위한 역조회다. "
			+ "필드·계좌 노출 정책은 GET /api/pots/{potId}와 동일하다. "
			+ "배달팟에서 만들지 않은 채팅방이거나 아직 연동 전인 방이면 404."
	)
	@RequireAuthenticate
	@GetMapping("/by-chat-room/{chatRoomId}")
	public ApiResponse<PotDetailResponse> getPotByChatRoom(
		@LoginMember Long memberId,
		@PathVariable Long chatRoomId
	) {
		return ApiResponse.ok(potService.findDetailByChatRoomId(memberId, chatRoomId));
	}

	@Operation(
		summary = "팟 참여 (메뉴 전달)",
		description = "'총대에게 메뉴 전달하기' 버튼. 참여와 메뉴 전달을 한 번에 처리한다 — "
			+ "참여 기록(메뉴·금액 포함) + 인원 증가가 한 트랜잭션이다. "
			+ "프론트는 응답의 chatRoomId로 채팅 화면으로 이동한다. "
			+ "중복 참여는 POT_ALREADY_JOINED, 정원이 찼으면 POT_FULL, "
			+ "마감시간이 지났거나 나눔이 완료됐으면 POT_NOT_ACTIVE."
	)
	@RequireAuthenticate
	@PostMapping("/{potId}/members")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<PotJoinResponse> joinPot(
		@LoginMember Long memberId,
		@PathVariable Long potId,
		@Valid @RequestBody PotJoinRequest request
	) {
		return ApiResponse.ok(potService.join(memberId, potId, request));
	}

	@Operation(
		summary = "팟 나가기",
		description = "채팅방의 '팟 나가기' 버튼. 참여 기록을 지우고 인원을 1 줄이며, 채팅방에 "
			+ "'~님이 채팅방을 나갔어요' 안내를 남긴다. "
			+ "총대는 나눔 완료 전엔 나갈 수 없다(POT_HOST_CANNOT_LEAVE) — 완료 후에는 참여자와 동일하게 나갈 수 있다."
	)
	@RequireAuthenticate
	@DeleteMapping("/{potId}/members/me")
	public ApiResponse<Void> leavePot(@LoginMember Long memberId, @PathVariable Long potId) {
		potService.leave(memberId, potId);
		return ApiResponse.ok();
	}

	@Operation(
		summary = "나눔 완료",
		description = "배달을 받아 나누는 것까지 끝났을 때 총대가 호출한다('나눔 완료' 버튼). "
			+ "상태가 DONE이 되어 참여자를 포함한 모두의 목록에서 사라진다. "
			+ "마감시간 전이라도 누를 수 있다 — "
			+ "정원이 차서 일찍 받아 나눈 경우가 정상 흐름이다. "
			+ "총대가 누르지 않아도 마감시간 + 5시간이 지나면 목록 조회 시 자동으로 완료된다."
	)
	@RequireAuthenticate
	@PostMapping("/{potId}/complete")
	public ApiResponse<Void> completePot(@LoginMember Long memberId, @PathVariable Long potId) {
		potService.complete(memberId, potId);
		return ApiResponse.ok();
	}
}
