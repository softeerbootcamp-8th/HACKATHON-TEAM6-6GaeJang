package com.delipot.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 온보딩 완료 시 서버가 받는 값. 형식 검증은 여기서 끝낸다. */
public record SignupRequest(

	@NotBlank
	@Pattern(regexp = "^\\d{1,11}$", message = "휴대폰 번호는 최대 11자리 숫자입니다.")
	String phoneNumber,

	@NotBlank
	@Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하입니다.")
	String password,

	@NotBlank
	@Pattern(regexp = "^[가-힣a-zA-Z]{1,10}$", message = "닉네임은 한글/영문 최대 10자입니다.")
	String nickname,

	@NotBlank
	String address,

	/** 자동 로그인 체크 여부. true 일 때만 remember-me 토큰을 발급한다. */
	boolean rememberMe
) {
}
