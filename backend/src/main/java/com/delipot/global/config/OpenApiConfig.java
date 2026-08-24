package com.delipot.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/**
 * 프론트의 Orval이 이 스펙(/v3/api-docs)을 읽어 훅을 생성한다.
 * 스펙이 곧 프론트-백 계약이므로 operation summary/description을 성실히 쓴다.
 */
@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI openAPI() {
		return new OpenAPI().info(new Info()
			.title("Delipot API")
			.description("육개장(Softeer 8기 6팀) 해커톤 프로젝트 Delipot API")
			.version("v1"));
	}
}
