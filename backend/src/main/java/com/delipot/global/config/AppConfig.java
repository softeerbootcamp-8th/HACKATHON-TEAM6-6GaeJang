package com.delipot.global.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

	/** 시간은 항상 주입받아 쓴다 — 테스트에서 고정 Clock으로 갈아끼울 수 있게. */
	@Bean
	public Clock clock() {
		return Clock.systemDefaultZone();
	}
}
