package com.delipot.health.dto;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "서버 상태 응답")
public record HealthResponse(
	@Schema(description = "서버 전체 상태", example = "UP") Status status,
	@Schema(description = "DB 연결 상태", example = "UP") Status database,
	@Schema(description = "활성 프로파일", example = "local") String profile,
	@Schema(description = "애플리케이션 버전", example = "0.0.1-SNAPSHOT") String version,
	@Schema(description = "서버 시각") OffsetDateTime serverTime
) {

	public enum Status {
		UP, DOWN
	}
}
