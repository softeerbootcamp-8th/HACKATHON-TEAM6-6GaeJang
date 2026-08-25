package com.delipot.member.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Pattern;

/**
 * 마이페이지 프로필 수정 요청. 닉네임/주소를 각각 독립적으로 바꿀 수 있어 둘 다 optional이다.
 * 값을 보내지 않은 필드(null)는 그대로 둔다.
 */
public record ProfileUpdateRequest(

	@Pattern(regexp = "^[가-힣a-zA-Z0-9]{2,10}$", message = "닉네임은 2~10자 한글/영문/숫자입니다.")
	String nickname,

	String address,

	String roadAddress,

	String jibunAddress,

	BigDecimal latitude,

	BigDecimal longitude
) {
}
