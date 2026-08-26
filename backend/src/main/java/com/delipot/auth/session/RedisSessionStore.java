package com.delipot.auth.session;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 세션 저장소. 키에 TTL 을 걸어 두므로 만료 세션은 Redis 가 알아서 물리 삭제한다.
 * (별도 정리 스케줄러가 필요 없는 이유)
 *
 * <p>{@code member-sessions:{memberId}} Set 에 발급된 sid 를 역인덱스로 같이 들고 있다가
 * 탈퇴 시 "이 회원의 모든 기기 세션"을 한 번에 지우는 데 쓴다. Set 에는 TTL 을 걸지 않는다 —
 * 슬라이딩 만료로 세션 TTL 만 계속 늘어나는데 Set 이 먼저 만료되면 역인덱스가 살아있는 세션을
 * 놓칠 수 있어서다.
 */
@Component
@Profile({"local", "prod"})
public class RedisSessionStore implements SessionStore {

	private static final String KEY_PREFIX = "session:";
	private static final String MEMBER_INDEX_PREFIX = "member-sessions:";

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
		redis.opsForSet().add(memberIndexKey(memberId), sid);
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
		String memberId = redis.opsForValue().get(key(sid));
		redis.delete(key(sid));
		if (memberId != null) {
			redis.opsForSet().remove(memberIndexKey(memberId), sid);
		}
	}

	@Override
	public void deleteAllByMemberId(Long memberId) {
		String indexKey = memberIndexKey(memberId);
		Set<String> sids = redis.opsForSet().members(indexKey);
		if (sids != null && !sids.isEmpty()) {
			sids.forEach(sid -> redis.delete(key(sid)));
		}
		redis.delete(indexKey);
	}

	private String key(String sid) {
		return KEY_PREFIX + sid;
	}

	private String memberIndexKey(Long memberId) {
		return MEMBER_INDEX_PREFIX + memberId;
	}

	private String memberIndexKey(String memberId) {
		return MEMBER_INDEX_PREFIX + memberId;
	}
}
