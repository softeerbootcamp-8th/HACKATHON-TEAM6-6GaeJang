package com.delipot.pot.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 홈 목록 조회 조건.
 *
 * <p>좌표는 회원가입 때 인증한 주소의 좌표다(화면 좌측 상단 "학동로 171"). 실시간 GPS가 아니다.
 */
@Schema(description = "팟 목록 조회 조건")
public record PotListRequest(

	// TODO: 인증 도입 시 이 두 필드를 제거하고 @LoginMember가 준 회원 인증 좌표를 쓴다.
	@Schema(description = "내 인증 주소의 위도", example = "37.5172")
	@NotNull(message = "위도는 필수입니다.")
	@DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다.")
	@DecimalMax(value = "90.0", message = "위도는 90 이하여야 합니다.")
	BigDecimal latitude,

	@Schema(description = "내 인증 주소의 경도", example = "127.0286")
	@NotNull(message = "경도는 필수입니다.")
	@DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다.")
	@DecimalMax(value = "180.0", message = "경도는 180 이하여야 합니다.")
	BigDecimal longitude,

	@Schema(description = "가게 이름 검색어. 비우면 전체", example = "치킨")
	@Size(max = 100, message = "검색어는 100자 이하여야 합니다.")
	String keyword
) {

	/**
	 * 쿼리에 넣을 검색어. 항상 non-null이며 빈 문자열은 "검색 없음"을 뜻한다.
	 *
	 * <p>{@code %}와 {@code _}를 이스케이프한다. 파라미터 바인딩이라 SQL 인젝션은 아니지만,
	 * 이스케이프하지 않으면 사용자가 친 {@code %}가 LIKE 와일드카드로 해석돼
	 * 검색어 한 글자에 전체 목록이 나온다. 역슬래시 자신도 먼저 이스케이프해야
	 * {@code \%} 같은 입력이 깨지지 않는다.
	 */
	public String searchKeyword() {
		if (keyword == null) {
			return "";
		}
		return keyword.trim()
			.replace("\\", "\\\\")
			.replace("%", "\\%")
			.replace("_", "\\_");
	}
}
