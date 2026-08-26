package com.delipot.health;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.delipot.health.dto.HealthResponse;
import com.delipot.health.dto.HealthResponse.Status;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class HealthService {

	private final JdbcTemplate jdbcTemplate;
	private final Environment environment;
	private final Clock clock;
	private final String version;

	public HealthService(
		JdbcTemplate jdbcTemplate,
		Environment environment,
		Clock clock,
		@Value("${app.version:unknown}") String version
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.environment = environment;
		this.clock = clock;
		this.version = version;
	}

	/**
	 * DB가 죽어도 이 API 자체는 200으로 응답한다.
	 * 장애 구분(서버 다운 / DB 다운)을 프론트에서 하려면 응답이 와야 하기 때문.
	 */
	public HealthResponse check() {
		Status database = pingDatabase();
		return new HealthResponse(
			database == Status.UP ? Status.UP : Status.DOWN,
			database,
			activeProfile(),
			version,
			OffsetDateTime.now(clock)
		);
	}

	private Status pingDatabase() {
		try {
			jdbcTemplate.queryForObject("SELECT 1", Integer.class);
			return Status.UP;
		} catch (Exception e) {
			log.error("DB 헬스체크 실패", e);
			return Status.DOWN;
		}
	}

	private String activeProfile() {
		String[] profiles = environment.getActiveProfiles();
		return profiles.length == 0 ? "default" : String.join(",", profiles);
	}
}
