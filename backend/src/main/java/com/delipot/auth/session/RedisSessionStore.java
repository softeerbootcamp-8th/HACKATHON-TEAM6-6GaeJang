package com.delipot.auth.session;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 세션 저장소. 키에 TTL 을 걸어 두므로 만료 세션은 Redis 가 알아서 물리 삭제한다.
 * (별도 정리 스케줄러가 필요 없는 이유)
 */
@Component
@Profile({"local", "prod"})
public class RedisSessionStore implements SessionStore {

	private static final String KEY_PREFIX = "session:";

	private final StringRedisTemplate redis;
	private final Duration ttl;

	public RedisSessionStore(
		StringRedisTemplate redis,
		@Value("${app.session.ttl-seconds}") long ttlSeconds
	) {
		this.redis = redis;
		this.ttl = Duration.ofSeconds(ttlSeconds);
	}

	@Override
	public String create(Long memberId) {
		String sid = SessionStore.newSessionId();
		redis.opsForValue().set(key(sid), memberId.toString(), ttl);
		return sid;
	}

	@Override
	public Optional<Long> find(String sid) {
		String value = redis.opsForValue().get(key(sid));
		return Optional.ofNullable(value).map(Long::valueOf);
	}

	@Override
	public void refresh(String sid) {
		redis.expire(key(sid), ttl);
	}

	@Override
	public void delete(String sid) {
		redis.delete(key(sid));
	}

	private String key(String sid) {
		return KEY_PREFIX + sid;
	}
}
