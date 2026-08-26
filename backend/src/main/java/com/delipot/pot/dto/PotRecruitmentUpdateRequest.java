package com.delipot.pot.dto;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 모집 조건 확장 요청. 정원과 마감시간만 담는다.
 * {@link PotUpdateRequest}와 나눈 이유는 두 경로의 허용 규칙이 반대여서다.
 */
@Schema(description = "모집 조건 확장 요청. 정원·마감시간을 늘릴 때만 쓴다")
public record PotRecruitmentUpdateRequest(

	@Schema(description = "총대를 포함한 모집 정원. 현재 정원보다 작을 수 없다", example = "4")
	@NotNull(message = "배달팟 인원은 필수입니다.")
	@Min(value = 2, message = "배달팟 인원은 2명 이상이어야 합니다.")
	@Max(value = 4, message = "배달팟 인원은 4명 이하여야 합니다.")
	Integer capacity,

	@Schema(description = "모집 마감시간. 현재 마감시간보다 앞당길 수 없다", example = "2026-08-25T21:00:00+09:00")
	@NotNull(message = "마감시간은 필수입니다.")
	@Future(message = "마감시간은 현재 시각 이후여야 합니다.")
	OffsetDateTime deadline
) {
}
