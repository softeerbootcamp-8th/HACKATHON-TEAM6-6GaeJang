package com.delipot.auth.session;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * 세션 저장소 추상화. 필터/인터셉터/리졸버는 이 인터페이스에만 의존한다.
 * 구현은 프로파일로 갈린다 — local/prod 는 Redis, h2/test 는 InMemory.
 */
public interface SessionStore {

	/** 세션을 발급하고 세션 키(sid)를 돌려준다. TTL 은 구현이 설정값대로 건다. */
	String create(Long memberId);

	/** 유효한 세션이면 memberId, 없거나 만료면 empty. */
	Optional<Long> find(String sid);

	/** 슬라이딩 만료: 남은 TTL 을 설정값 기준으로 다시 채운다. */
	void refresh(String sid);

	/** 로그아웃 등에서 세션을 즉시 제거한다. */
	void delete(String sid);

	// --- 공통 유틸 ---

	SecureRandom RANDOM = new SecureRandom();
	Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

	/** 추측 불가능한 256비트 세션 키. base64url 이라 쿠키/헤더에 그대로 실린다. */
	static String newSessionId() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return ENCODER.encodeToString(bytes);
	}
}
