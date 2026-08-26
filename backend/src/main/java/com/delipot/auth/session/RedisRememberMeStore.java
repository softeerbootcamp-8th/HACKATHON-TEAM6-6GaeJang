package com.delipot.auth.session;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 remember-me 저장소. 키 TTL 로 장기 만료를 맡긴다.
 *
 * <p>{@link RedisSessionStore} 와 동일한 이유로 {@code member-remember:{memberId}} Set 에
 * 발급된 토큰을 역인덱스로 들고 있다가 탈퇴 시 전부 지운다.
 */
@Component
@Profile({"local", "prod"})
public class RedisRememberMeStore implements RememberMeStore {

	private static final String KEY_PREFIX = "remember:";
	private static final String MEMBER_INDEX_PREFIX = "member-remember:";

	private final StringRedisTemplate redis;
	private final Duration ttl;

	public RedisRememberMeStore(
		StringRedisTemplate redis,
		@Value("${app.session.remember-ttl-seconds}") long ttlSeconds
	) {
		this.redis = redis;
		this.ttl = Duration.ofSeconds(ttlSeconds);
	}

	@Override
	public String issue(Long memberId) {
		// 세션 키와 동일한 방식의 추측 불가능한 랜덤 토큰을 재사용한다.
		String token = SessionStore.newSessionId();
		redis.opsForValue().set(key(token), memberId.toString(), ttl);
		redis.opsForSet().add(memberIndexKey(memberId), token);
		return token;
	}

	@Override
	public Optional<Long> find(String token) {
		String value = redis.opsForValue().get(key(token));
		return Optional.ofNullable(value).map(Long::valueOf);
	}

	@Override
	public void delete(String token) {
		String memberId = redis.opsForValue().get(key(token));
		redis.delete(key(token));
		if (memberId != null) {
			redis.opsForSet().remove(memberIndexKey(memberId), token);
		}
	}

	@Override
	public void deleteAllByMemberId(Long memberId) {
		String indexKey = memberIndexKey(memberId);
		Set<String> tokens = redis.opsForSet().members(indexKey);
		if (tokens != null && !tokens.isEmpty()) {
			tokens.forEach(token -> redis.delete(key(token)));
		}
		redis.delete(indexKey);
	}

	private String key(String token) {
		return KEY_PREFIX + token;
	}

	private String memberIndexKey(Long memberId) {
		return MEMBER_INDEX_PREFIX + memberId;
	}

	private String memberIndexKey(String memberId) {
		return MEMBER_INDEX_PREFIX + memberId;
	}
}
