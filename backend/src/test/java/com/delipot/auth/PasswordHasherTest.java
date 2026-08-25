package com.delipot.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.delipot.auth.crypto.PasswordHasher;

class PasswordHasherTest {

	private final PasswordHasher hasher = new PasswordHasher();

	@Test
	@DisplayName("해시는 원문과 다르고, 같은 원문으로 검증하면 통과한다")
	void encodeAndMatch() {
		String hash = hasher.encode("secret1234");

		assertThat(hash).isNotEqualTo("secret1234");
		assertThat(hasher.matches("secret1234", hash)).isTrue();
	}

	@Test
	@DisplayName("다른 비밀번호로는 검증에 실패한다")
	void mismatch() {
		String hash = hasher.encode("secret1234");

		assertThat(hasher.matches("wrongpass1", hash)).isFalse();
	}

	@Test
	@DisplayName("같은 비밀번호라도 매번 다른 해시(salt)가 나온다")
	void saltedEachTime() {
		assertThat(hasher.encode("secret1234")).isNotEqualTo(hasher.encode("secret1234"));
	}
}
