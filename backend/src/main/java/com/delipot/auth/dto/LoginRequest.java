package com.delipot.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 재방문 로그인 — 전화번호 + 비밀번호. */
public record LoginRequest(

	@NotBlank
	@Pattern(regexp = "^\\d{1,11}$", message = "휴대폰 번호는 최대 11자리 숫자입니다.")
	String phoneNumber,

	@NotBlank
	String password,

	/** 자동 로그인 체크 여부. true 일 때만 remember-me 토큰을 발급한다. */
	boolean rememberMe
) {
}
