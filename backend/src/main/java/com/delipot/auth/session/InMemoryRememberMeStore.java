package com.delipot.auth.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Redis 없이 뜨는 h2/test 전용 remember-me 저장소. 만료는 조회 시점에 판정한다.
 *
 * <p>{@link InMemorySessionStore} 와 동일하게 memberId 별 발급 토큰을 역인덱스로 들고 있다가
 * {@link #deleteAllByMemberId} 에서 한 번에 지운다.
 */
@Component
@Profile({"h2", "test"})
public class InMemoryRememberMeStore implements RememberMeStore {

	private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Set<String>> memberIndex = new ConcurrentHashMap<>();
	private final Clock clock;
	private final Duration ttl;

	public InMemoryRememberMeStore(
		Clock clock,
		@Value("${app.session.remember-ttl-seconds}") long ttlSeconds
	) {
		this.clock = clock;
		this.ttl = Duration.ofSeconds(ttlSeconds);
	}

	@Override
	public String issue(Long memberId) {
		String token = SessionStore.newSessionId();
		store.put(token, new Entry(memberId, clock.instant().plus(ttl)));
		memberIndex.computeIfAbsent(memberId, k -> new CopyOnWriteArraySet<>()).add(token);
		return token;
	}

	@Override
	public Optional<Long> find(String token) {
		Entry entry = store.get(token);
		if (entry == null) {
			return Optional.empty();
		}
		if (clock.instant().isAfter(entry.expiresAt())) {
			store.remove(token);
			return Optional.empty();
		}
		return Optional.of(entry.memberId());
	}

	@Override
	public void delete(String token) {
		Entry entry = store.remove(token);
		if (entry != null) {
			memberIndex.computeIfPresent(entry.memberId(), (k, tokens) -> {
				tokens.remove(token);
				return tokens.isEmpty() ? null : tokens;
			});
		}
	}

	@Override
	public void deleteAllByMemberId(Long memberId) {
		Set<String> tokens = memberIndex.remove(memberId);
		if (tokens != null) {
			tokens.forEach(store::remove);
		}
	}

	private record Entry(Long memberId, Instant expiresAt) {
	}
}
