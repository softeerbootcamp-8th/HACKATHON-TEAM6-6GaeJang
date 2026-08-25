package com.delipot.auth.web;

import java.util.Optional;

/**
 * 한 요청 동안 "지금 인증된 회원이 누구인가"를 담는 ThreadLocal 홀더.
 * 필터가 심고, 인터셉터/리졸버가 읽고, 필터가 finally 에서 반드시 비운다.
 * (스레드 풀 재사용 시 이전 요청의 인증정보가 남는 것을 막기 위함)
 */
public final class AuthContext {

	private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

	private AuthContext() {
	}

	public static void setMemberId(Long memberId) {
		HOLDER.set(memberId);
	}

	public static Optional<Long> getMemberId() {
		return Optional.ofNullable(HOLDER.get());
	}

	public static boolean isAuthenticated() {
		return HOLDER.get() != null;
	}

	public static void clear() {
		HOLDER.remove();
	}
}
