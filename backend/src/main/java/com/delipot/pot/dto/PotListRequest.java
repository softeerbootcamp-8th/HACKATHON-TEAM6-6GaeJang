package com.delipot.pot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 홈 목록 조회 조건.
 *
 * <p>좌표는 요청에서 받지 않는다. 회원가입 때 카카오맵으로 인증한 주소의 좌표를 서버가 직접 읽는다
 * (화면 좌측 상단 "학동로 171"). 클라이언트가 좌표를 보내게 두면 남의 동네 팟을 조회할 수 있고,
 * 프론트가 좌표를 따로 들고 다녀야 해서 화면마다 어긋난다.
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
