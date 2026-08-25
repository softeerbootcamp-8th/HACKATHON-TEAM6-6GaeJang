package com.delipot.pot;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.delipot.global.response.ApiResponse;
import com.delipot.pot.dto.PotCreateRequest;
import com.delipot.pot.dto.PotCreateResponse;

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
			+ "생성 직후 상태는 RECRUITING이고 총대 본인이 첫 참여자로 잡힌다."
	)
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<PotCreateResponse> createPot(@Valid @RequestBody PotCreateRequest request) {
		return ApiResponse.ok(potService.create(request));
	}
}
