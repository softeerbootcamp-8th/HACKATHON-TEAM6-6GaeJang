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
 * Redis 없이 뜨는 h2/test 프로파일 전용. 만료는 조회 시점에 판정(지연 무효화)하고,
 * 만료된 항목은 그때 제거한다. 운영에서 쓰지 않으므로 별도 청소 스케줄러는 두지 않는다.
 *
 * <p>{@link RedisSessionStore} 와 동일하게, memberId 별로 발급된 sid 를 역인덱스로 들고 있다가
 * {@link #deleteAllByMemberId} 에서 한 번에 지운다.
 */
@Component
@Profile({"h2", "test"})
public class InMemorySessionStore implements SessionStore {

	private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Set<String>> memberIndex = new ConcurrentHashMap<>();
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
		memberIndex.computeIfAbsent(memberId, k -> new CopyOnWriteArraySet<>()).add(sid);
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
		Entry entry = store.remove(sid);
		if (entry != null) {
			memberIndex.computeIfPresent(entry.memberId(), (k, sids) -> {
				sids.remove(sid);
				return sids.isEmpty() ? null : sids;
			});
		}
	}

	@Override
	public void deleteAllByMemberId(Long memberId) {
		Set<String> sids = memberIndex.remove(memberId);
		if (sids != null) {
			sids.forEach(store::remove);
		}
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
