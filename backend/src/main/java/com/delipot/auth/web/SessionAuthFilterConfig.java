package com.delipot.auth.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.delipot.auth.session.RememberMeStore;
import com.delipot.auth.session.SessionStore;

/**
 * 세션 필터 등록. 순수 @Configuration 으로 둔 이유:
 * @WebMvcTest 슬라이스는 WebMvcConfigurer/Filter 등 웹 타입만 스캔하고 이런 일반 설정은 로딩하지 않는다.
 * 필터를 WebMvcConfigurer 쪽에 두면 슬라이스가 SessionStore(@Component, 슬라이스 제외)를 못 찾아 깨진다.
 */
@Configuration
public class SessionAuthFilterConfig {

	/**
	 * 세션 필터는 DispatcherServlet 앞(서블릿 체인)에서 돌아야 인터셉터가 컨텍스트를 읽을 수 있다.
	 * FilterRegistrationBean 으로 /api/* 로만 범위를 좁힌다.
	 */
	@Bean
	public FilterRegistrationBean<SessionAuthenticationFilter> sessionAuthenticationFilter(
		SessionStore sessionStore,
		RememberMeStore rememberMeStore,
		SessionCookieManager cookieManager
	) {
		FilterRegistrationBean<SessionAuthenticationFilter> registration =
			new FilterRegistrationBean<>(
				new SessionAuthenticationFilter(sessionStore, rememberMeStore, cookieManager));
		registration.addUrlPatterns("/api/*");
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
		return registration;
	}
}
