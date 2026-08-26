package com.delipot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import com.delipot.auth.web.AuthContext;
import com.delipot.auth.web.AuthenticationInterceptor;
import com.delipot.global.error.BusinessException;
import com.delipot.global.error.ErrorCode;

class AuthenticationInterceptorTest {

	private final AuthenticationInterceptor interceptor = new AuthenticationInterceptor();
	private final MockHttpServletRequest request = new MockHttpServletRequest();
	private final MockHttpServletResponse response = new MockHttpServletResponse();

	@AfterEach
	void clearContext() {
		AuthContext.clear();
	}

	/** @RequireAuthenticate / @RequireGuest 유무를 확인하기 위한 더미 핸들러. */
	static class DummyController {
		@RequireAuthenticate
		public void secured() {
		}

		@RequireGuest
		public void guestOnly() {
		}

		public void open() {
		}
	}

	private HandlerMethod handler(String method) throws NoSuchMethodException {
		return new HandlerMethod(new DummyController(), DummyController.class.getMethod(method));
	}

	@Test
	@DisplayName("@RequireAuthenticate + 인증됨 → 통과")
	void securedAuthenticated() throws Exception {
		AuthContext.setMemberId(1L);

		assertThat(interceptor.preHandle(request, response, handler("secured"))).isTrue();
	}

	@Test
	@DisplayName("@RequireAuthenticate + 미인증 → UNAUTHORIZED")
	void securedUnauthenticated() throws Exception {
		assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("secured")))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.UNAUTHORIZED);
	}

	@Test
	@DisplayName("애노테이션 없는 메서드는 미인증이어도 통과")
	void openMethodPasses() throws Exception {
		assertThat(interceptor.preHandle(request, response, handler("open"))).isTrue();
	}

	@Test
	@DisplayName("@RequireGuest + 미인증 → 통과")
	void guestOnlyUnauthenticated() throws Exception {
		assertThat(interceptor.preHandle(request, response, handler("guestOnly"))).isTrue();
	}

	@Test
	@DisplayName("@RequireGuest + 인증됨 → ALREADY_AUTHENTICATED")
	void guestOnlyAuthenticated() throws Exception {
		AuthContext.setMemberId(1L);

		assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("guestOnly")))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.ALREADY_AUTHENTICATED);
	}

	@Test
	@DisplayName("핸들러 메서드가 아니면(정적 리소스 등) 통과")
	void nonHandlerMethodPasses() {
		assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
	}
}
