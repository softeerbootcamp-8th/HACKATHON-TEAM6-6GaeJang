package com.delipot.auth.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Redis 없이 뜨는 h2/test 전용 remember-me 저장소. 만료는 조회 시점에 판정한다. */
@Component
@Profile({"h2", "test"})
public class InMemoryRememberMeStore implements RememberMeStore {

	private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
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
		store.remove(token);
	}

	private record Entry(Long memberId, Instant expiresAt) {
	}
}
