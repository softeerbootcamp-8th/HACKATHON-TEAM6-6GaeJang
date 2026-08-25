package com.delipot.auth.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Redis 없이 뜨는 h2/test 프로파일 전용. 만료는 조회 시점에 판정(지연 무효화)하고,
 * 만료된 항목은 그때 제거한다. 운영에서 쓰지 않으므로 별도 청소 스케줄러는 두지 않는다.
 */
@Component
@Profile({"h2", "test"})
public class InMemorySessionStore implements SessionStore {

	private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
	private final Clock clock;
	private final Duration ttl;

	public InMemorySessionStore(
		Clock clock,
		@Value("${app.session.ttl-seconds}") long ttlSeconds
	) {
		this.clock = clock;
		this.ttl = Duration.ofSeconds(ttlSeconds);
	}

	@Override
	public String create(Long memberId) {
		String sid = SessionStore.newSessionId();
		store.put(sid, new Entry(memberId, expiresAt()));
		return sid;
	}

	@Override
	public Optional<Long> find(String sid) {
		Entry entry = store.get(sid);
		if (entry == null) {
			return Optional.empty();
		}
		if (isExpired(entry)) {
			store.remove(sid);
			return Optional.empty();
		}
		return Optional.of(entry.memberId());
	}

	@Override
	public void refresh(String sid) {
		store.computeIfPresent(sid, (k, entry) ->
			isExpired(entry) ? null : new Entry(entry.memberId(), expiresAt()));
	}

	@Override
	public void delete(String sid) {
		store.remove(sid);
	}

	private Instant expiresAt() {
		return clock.instant().plus(ttl);
	}

	private boolean isExpired(Entry entry) {
		return clock.instant().isAfter(entry.expiresAt());
	}

	private record Entry(Long memberId, Instant expiresAt) {
	}
}
