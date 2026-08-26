package com.delipot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;

import com.delipot.auth.web.AuthContext;
import com.delipot.auth.web.LoginMemberArgumentResolver;
import com.delipot.global.error.BusinessException;
import com.delipot.global.error.ErrorCode;

class LoginMemberArgumentResolverTest {

	private final LoginMemberArgumentResolver resolver = new LoginMemberArgumentResolver();

	@AfterEach
	void clearContext() {
		AuthContext.clear();
	}

	@SuppressWarnings("unused")
	static class DummyController {
		public void handle(@LoginMember Long memberId, String plain) {
		}
	}

	private MethodParameter parameter(int index) throws NoSuchMethodException {
		return new MethodParameter(
			DummyController.class.getMethod("handle", Long.class, String.class), index);
	}

	@Test
	@DisplayName("@LoginMember Long 파라미터만 지원한다")
	void supportsOnlyAnnotatedLong() throws Exception {
		assertThat(resolver.supportsParameter(parameter(0))).isTrue();
		assertThat(resolver.supportsParameter(parameter(1))).isFalse();
	}

	@Test
	@DisplayName("인증 컨텍스트의 memberId를 주입한다")
	void resolvesMemberId() throws Exception {
		AuthContext.setMemberId(42L);

		Object resolved = resolver.resolveArgument(parameter(0), null, null, null);

		assertThat(resolved).isEqualTo(42L);
	}

	@Test
	@DisplayName("컨텍스트가 비어 있으면 UNAUTHORIZED")
	void throwsWhenUnauthenticated() throws Exception {
		MethodParameter param = parameter(0);

		assertThatThrownBy(() -> resolver.resolveArgument(param, null, null, null))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.UNAUTHORIZED);
	}
}
