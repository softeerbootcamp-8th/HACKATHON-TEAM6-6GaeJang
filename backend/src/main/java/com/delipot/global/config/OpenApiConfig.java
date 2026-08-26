package com.delipot.global.config;

import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.delipot.auth.LoginMember;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/**
 * 프론트의 Orval이 이 스펙(/v3/api-docs)을 읽어 훅을 생성한다.
 * 스펙이 곧 프론트-백 계약이므로 operation summary/description을 성실히 쓴다.
 */
@Configuration
public class OpenApiConfig {

	static {
		// @LoginMember 는 세션에서 주입되는 값이라 요청 파라미터가 아니다.
		// 등록하지 않으면 springdoc 이 memberId 를 쿼리 파라미터로 오해해 프론트 계약을 오염시킨다.
		SpringDocUtils.getConfig().addAnnotationsToIgnore(LoginMember.class);
	}

	@Bean
	public OpenAPI openAPI() {
		return new OpenAPI().info(new Info()
			.title("Delipot API")
			.description("육개장(Softeer 8기 6팀) 해커톤 프로젝트 Delipot API")
			.version("v1"));
	}
}
