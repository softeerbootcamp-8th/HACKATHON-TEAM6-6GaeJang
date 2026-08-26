package com.delipot.pot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 홈 목록 조회 조건. 좌표는 받지 않는다 — 가입 때 인증한 주소의 좌표를 서버가 직접 읽는다.
 * 클라이언트가 보내게 두면 남의 동네 팟을 조회할 수 있다.
 */
@Schema(description = "팟 목록 조회 조건")
public record PotListRequest(

	@Schema(description = "가게 이름 검색어. 비우면 전체", example = "치킨")
	@Size(max = 100, message = "검색어는 100자 이하여야 합니다.")
	String keyword
) {

	/**
	 * 쿼리에 넣을 검색어. 항상 non-null이며 빈 문자열은 "검색 없음"을 뜻한다.
	 *
	 * <p>{@code %}, {@code _}를 이스케이프하지 않으면 사용자가 친 {@code %}가 LIKE 와일드카드로
	 * 해석돼 한 글자에 전체 목록이 나온다. 역슬래시를 먼저 처리해야 {@code \%} 입력이 깨지지 않는다.
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
