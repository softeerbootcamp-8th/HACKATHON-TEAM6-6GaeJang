package com.delipot.pot.dto;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 모집 조건 확장 요청. 정원과 마감시간만 담는다.
 *
 * <p>{@link PotUpdateRequest}와 나누는 이유는 두 경로의 규칙이 다르기 때문이다. 전체 수정은
 * 참여자가 없을 때만 열리고 값을 통째로 갈아끼우지만, 이쪽은 참여자가 있어도 열리는 대신
 * 늘리는 방향만 허용한다. 한 요청 타입으로 합치면 서버가 나머지 필드의 "안 바뀜"을 값 비교로
 * 확인해야 하고(좌표 scale·null/빈문자열 차이로 오탐이 난다), 규칙 두 벌이 한 메서드에 섞인다.
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
