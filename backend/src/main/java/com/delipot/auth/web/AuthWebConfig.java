package com.delipot.auth.web;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 인증 인터셉터/리졸버를 MVC 파이프라인에 등록한다.
 * 세션 필터 등록은 {@link SessionAuthFilterConfig} 에 별도로 둔다 — 이유는 그쪽 주석 참고.
 * 동작 순서: 필터(컨텍스트 세팅) → 인터셉터(강제) → 리졸버(주입).
 */
@Configuration
public class AuthWebConfig implements WebMvcConfigurer {

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new AuthenticationInterceptor())
			.addPathPatterns("/api/**");
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(new LoginMemberArgumentResolver());
	}
}
