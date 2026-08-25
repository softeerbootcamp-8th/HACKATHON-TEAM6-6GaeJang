package com.delipot.global.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 에러 코드 = 클라이언트와의 계약. 새 케이스는 여기에만 추가한다.
 * 이름(enum name)이 그대로 응답 body의 {@code error.code}로 나간다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// 400대 — 클라이언트 입력/상태 문제
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
	ALREADY_AUTHENTICATED(HttpStatus.CONFLICT, "이미 로그인된 상태입니다."),
	// 번호/비번 중 무엇이 틀렸는지 구분하지 않는다 — 사용자 열거(enumeration) 방지.
	LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "전화번호 또는 비밀번호가 올바르지 않습니다."),
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
	CONFLICT(HttpStatus.CONFLICT, "이미 처리된 요청입니다."),
	CHAT_ROOM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "채팅방에 접근할 권한이 없습니다."),
	DUPLICATE_PHONE(HttpStatus.CONFLICT, "이미 가입된 휴대폰 번호입니다."),
	DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
	// 팟 — 주소는 목록 조회의 검색 중심점이라 없으면 조회 자체가 불가능하다.
	ADDRESS_NOT_SET(HttpStatus.BAD_REQUEST, "주소를 먼저 설정해주세요."),
	POT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "총대만 할 수 있는 작업입니다."),
	POT_NOT_ACTIVE(HttpStatus.CONFLICT, "참여할 수 없는 팟입니다."),
	POT_FULL(HttpStatus.CONFLICT, "모집 정원이 이미 찼습니다."),
	POT_ALREADY_JOINED(HttpStatus.CONFLICT, "이미 참여한 팟입니다."),
	POT_NOT_JOINED(HttpStatus.CONFLICT, "참여하지 않은 팟입니다."),
	POT_HOST_CANNOT_LEAVE(HttpStatus.BAD_REQUEST, "총대는 팟을 나갈 수 없습니다. 나눔 완료를 사용해주세요."),
	// 회원 — 총대인 진행 중인 팟이 있으면 탈퇴를 막는다. 참여자로만 속한 팟은 자동으로 나가기 처리된다.
	MEMBER_HAS_ACTIVE_POT(HttpStatus.CONFLICT, "진행 중인 배달팟이 있어 탈퇴할 수 없어요."),

	// 500대 — 서버 문제
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

	private final HttpStatus status;
	private final String message;
}
