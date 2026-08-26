package com.delipot.auth.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.delipot.support.MutableTestClock;

class InMemoryRememberMeStoreTest {

	private static final long TTL_SECONDS = 1209600; // 14일

	private MutableTestClock clock;
	private InMemoryRememberMeStore store;

	@BeforeEach
	void setUp() {
		clock = new MutableTestClock(Instant.parse("2026-01-01T00:00:00Z"));
		store = new InMemoryRememberMeStore(clock, TTL_SECONDS);
	}

	@Test
	@DisplayName("발급한 토큰은 memberId로 조회된다")
	void issueThenFind() {
		String token = store.issue(9L);

		assertThat(store.find(token)).contains(9L);
	}

	@Test
	@DisplayName("TTL(14일)이 지나면 만료된다")
	void expiresAfterTtl() {
		String token = store.issue(9L);

		clock.advance(Duration.ofSeconds(TTL_SECONDS + 1));

		assertThat(store.find(token)).isEmpty();
	}

	@Test
	@DisplayName("delete하면 즉시 무효화된다(회전 시 옛 토큰 폐기)")
	void delete() {
		String token = store.issue(9L);

		store.delete(token);

		assertThat(store.find(token)).isEmpty();
	}

	@Test
	@DisplayName("deleteAllByMemberId는 다른 기기에서 발급한 토큰까지 전부 지운다")
	void deleteAllByMemberIdRemovesEveryDevice() {
		String tokenA = store.issue(9L);
		String tokenB = store.issue(9L);
		String otherMemberToken = store.issue(3L);

		store.deleteAllByMemberId(9L);

		assertThat(store.find(tokenA)).isEmpty();
		assertThat(store.find(tokenB)).isEmpty();
		assertThat(store.find(otherMemberToken)).contains(3L);
	}
}
