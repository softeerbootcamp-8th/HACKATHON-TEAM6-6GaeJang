package com.delipot.pot;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.delipot.auth.LoginMember;
import com.delipot.auth.RequireAuthenticate;
import com.delipot.global.response.ApiResponse;
import com.delipot.pot.dto.PotCreateRequest;
import com.delipot.pot.dto.PotCreateResponse;
import com.delipot.pot.dto.PotListRequest;
import com.delipot.pot.dto.PotListResponse;

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
			+ "생성 직후 상태는 RECRUITING이고 총대 본인이 첫 참여자로 잡힌다."
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
		summary = "팟 목록 조회",
		description = "내 인증 주소 기준 300m 이내에서 참여 가능한 팟을 마감 임박순으로 준다. "
			+ "모집 중이면서 마감 전이고 정원이 남은 팟만 나온다. keyword를 주면 가게 이름으로 거른다."
	)
	@RequireAuthenticate
	@GetMapping
	public ApiResponse<PotListResponse> getPots(@Valid @ModelAttribute PotListRequest request) {
		return ApiResponse.ok(potService.findNearby(request));
	}
}
