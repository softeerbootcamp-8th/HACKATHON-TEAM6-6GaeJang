package com.delipot.member.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.delipot.member.Member;

/** 회원 정보 응답. signup/login/me 가 공통으로 사용한다. */
public record MemberResponse(
	Long id,
	String phoneNumber,
	String nickname,
	String address,
	String roadAddress,
	String jibunAddress,
	BigDecimal latitude,
	BigDecimal longitude,
	LocalDateTime createdAt,
	long hostedPotCount
) {

	/** signup/login 처럼 총대 횟수가 의미 없는(가입 직후) 응답용 — 0으로 고정해 카운트 조회를 건너뛴다. */
	public static MemberResponse from(Member member) {
		return from(member, 0L);
	}

	/** 마이페이지 "총대 N회" 배지처럼 실제 카운트가 필요한 응답용. */
	public static MemberResponse from(Member member, long hostedPotCount) {
		return new MemberResponse(
			member.getId(),
			member.getPhoneNumber(),
			member.getNickname(),
			member.getAddress(),
			member.getRoadAddress(),
			member.getJibunAddress(),
			member.getLatitude(),
			member.getLongitude(),
			member.getCreatedAt(),
			hostedPotCount
		);
	}
}
