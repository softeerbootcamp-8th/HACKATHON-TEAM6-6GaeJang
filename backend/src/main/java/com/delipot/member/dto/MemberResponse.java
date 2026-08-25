package com.delipot.member.dto;

import java.time.LocalDateTime;

import com.delipot.member.Member;

/** 회원 정보 응답. signup/login/me 가 공통으로 사용한다. */
public record MemberResponse(
	Long id,
	String phoneNumber,
	String nickname,
	String address,
	LocalDateTime createdAt
) {

	public static MemberResponse from(Member member) {
		return new MemberResponse(
			member.getId(),
			member.getPhoneNumber(),
			member.getNickname(),
			member.getAddress(),
			member.getCreatedAt()
		);
	}
}
