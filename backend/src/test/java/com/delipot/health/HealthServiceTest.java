package com.delipot.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

import com.delipot.health.dto.HealthResponse;
import com.delipot.health.dto.HealthResponse.Status;

@ExtendWith(MockitoExtension.class)
class HealthServiceTest {

	private static final Instant FIXED = Instant.parse("2026-08-24T00:00:00Z");

	@Mock
	private JdbcTemplate jdbcTemplate;

	private HealthService healthService(String... activeProfiles) {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles(activeProfiles);
		return new HealthService(
			jdbcTemplate,
			environment,
			Clock.fixed(FIXED, ZoneOffset.UTC),
			"0.0.1-TEST"
		);
	}

	@Test
	@DisplayName("DB 핑이 성공하면 전체 상태가 UP이다")
	void databaseUp() {
		given(jdbcTemplate.queryForObject(eq("SELECT 1"), eq(Integer.class))).willReturn(1);

		HealthResponse response = healthService("local").check();

		assertThat(response.status()).isEqualTo(Status.UP);
		assertThat(response.database()).isEqualTo(Status.UP);
		assertThat(response.profile()).isEqualTo("local");
		assertThat(response.version()).isEqualTo("0.0.1-TEST");
		assertThat(response.serverTime().toInstant()).isEqualTo(FIXED);
	}

	@Test
	@DisplayName("DB 연결이 끊기면 예외를 던지지 않고 DOWN으로 응답한다")
	void databaseDown() {
		willThrow(new DataAccessResourceFailureException("connection refused"))
			.given(jdbcTemplate).queryForObject(any(String.class), eq(Integer.class));

		HealthResponse response = healthService("local").check();

		assertThat(response.status()).isEqualTo(Status.DOWN);
		assertThat(response.database()).isEqualTo(Status.DOWN);
	}

	@Test
	@DisplayName("활성 프로파일이 없으면 default로 표시한다")
	void noActiveProfile() {
		given(jdbcTemplate.queryForObject(eq("SELECT 1"), eq(Integer.class))).willReturn(1);

		assertThat(healthService().check().profile()).isEqualTo("default");
	}
}
