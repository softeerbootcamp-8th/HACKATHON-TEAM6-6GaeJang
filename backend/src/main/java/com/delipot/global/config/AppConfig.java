package com.delipot.global.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

	/** 서비스 기준 타임존. 서버 OS 설정에 의존하지 않도록 코드에 고정한다. */
	public static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	/**
	 * 시간은 항상 주입받아 쓴다 — 테스트에서 고정 Clock으로 갈아끼울 수 있게.
	 *
	 * <p>{@code systemDefaultZone()}을 쓰지 않는 이유는 EC2의 JVM 기본 타임존이 UTC라서다.
	 * 그 상태로는 KST 벽시계 시각과 서버가 보는 "지금"이 9시간 어긋나 마감시간 검증이 무력화된다.
	 */
	@Bean
	public Clock clock() {
		return Clock.system(SERVICE_ZONE);
	}
}
