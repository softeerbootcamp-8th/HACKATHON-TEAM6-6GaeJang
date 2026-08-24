package com.delipot.health;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.delipot.global.response.ApiResponse;
import com.delipot.health.dto.HealthResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Health", description = "서버/DB 상태 확인")
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

	private final HealthService healthService;

	@Operation(summary = "헬스체크", description = "서버와 DB 연결 상태를 반환한다. DB가 죽어도 200으로 응답한다.")
	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public ApiResponse<HealthResponse> health() {
		return ApiResponse.ok(healthService.check());
	}
}
