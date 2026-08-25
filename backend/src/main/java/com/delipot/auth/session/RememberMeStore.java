package com.delipot.auth.session;

import java.util.Optional;

/**
 * remember-me(자동 로그인) 토큰 저장소. 세션과 별개로 장기 보관한다.
 * 세션이 만료됐을 때 이 토큰으로 새 세션을 발급하고, 사용 시마다 회전(삭제 후 재발급)한다.
 */
public interface RememberMeStore {

	/** 토큰을 발급하고 저장한다(장기 TTL). */
	String issue(Long memberId);

	/** 유효한 토큰이면 memberId, 없거나 만료면 empty. */
	Optional<Long> find(String token);

	/** 회전/로그아웃 시 토큰을 즉시 제거한다. */
	void delete(String token);
}
