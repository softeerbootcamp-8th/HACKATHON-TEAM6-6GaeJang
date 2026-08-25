package com.delipot.auth.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.delipot.support.MutableTestClock;

class InMemorySessionStoreTest {

	private static final long TTL_SECONDS = 60;

	private MutableTestClock clock;
	private InMemorySessionStore store;

	@BeforeEach
	void setUp() {
		clock = new MutableTestClock(Instant.parse("2026-01-01T00:00:00Z"));
		store = new InMemorySessionStore(clock, TTL_SECONDS);
	}

	@Test
	@DisplayName("발급한 세션은 memberId로 조회된다")
	void createThenFind() {
		String sid = store.create(7L);

		assertThat(store.find(sid)).contains(7L);
	}

	@Test
	@DisplayName("TTL이 지나면 만료되어 조회되지 않는다")
	void expiresAfterTtl() {
		String sid = store.create(7L);

		clock.advance(Duration.ofSeconds(TTL_SECONDS + 1));

		assertThat(store.find(sid)).isEmpty();
	}

	@Test
	@DisplayName("refresh는 만료 시각을 현재부터 다시 채운다(슬라이딩)")
	void refreshSlidesExpiry() {
		String sid = store.create(7L);

		clock.advance(Duration.ofSeconds(40)); // 만료 20초 전
		store.refresh(sid);                    // 이제 만료는 +60초 뒤로 밀린다
		clock.advance(Duration.ofSeconds(40)); // refresh 안 했다면 만료됐을 시점

		assertThat(store.find(sid)).contains(7L);

		clock.advance(Duration.ofSeconds(61)); // refresh 기준으로도 만료
		assertThat(store.find(sid)).isEmpty();
	}

	@Test
	@DisplayName("delete하면 즉시 조회되지 않는다")
	void delete() {
		String sid = store.create(7L);

		store.delete(sid);

		assertThat(store.find(sid)).isEmpty();
	}
}
