package com.delipot.member.dto;

/** 닉네임 실시간 중복확인 결과. available=true 면 사용 가능. */
public record NicknameAvailabilityResponse(boolean available) {
}
