package com.delipot.auth.web;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.delipot.auth.LoginMember;
import com.delipot.global.error.BusinessException;
import com.delipot.global.error.ErrorCode;

/**
 * {@code @LoginMember Long memberId} 파라미터에 인증 컨텍스트의 회원 id 를 주입한다.
 * 보통 {@link RequireAuthenticate} 와 함께 쓰여 항상 값이 있지만, 방어적으로 미인증이면 401.
 */
public class LoginMemberArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(LoginMember.class)
			&& parameter.getParameterType().equals(Long.class);
	}

	@Override
	public Object resolveArgument(
		MethodParameter parameter,
		ModelAndViewContainer mavContainer,
		NativeWebRequest webRequest,
		WebDataBinderFactory binderFactory
	) {
		return AuthContext.getMemberId()
			.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
	}
}
