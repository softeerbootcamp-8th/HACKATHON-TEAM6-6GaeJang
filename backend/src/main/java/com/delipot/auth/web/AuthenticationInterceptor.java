package com.delipot.auth.web;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.delipot.auth.RequireAuthenticate;
import com.delipot.auth.RequireGuest;
import com.delipot.global.error.BusinessException;
import com.delipot.global.error.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 핸들러 메서드에 {@link RequireAuthenticate} 가 있으면 인증을, {@link RequireGuest} 가 있으면
 * 미인증을 강제한다. 필터가 이미 컨텍스트를 세팅해 뒀으므로 여기서는 존재 여부만 본다.
 */
public class AuthenticationInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		// 정적 리소스 등 컨트롤러 메서드가 아니면 통과
		if (!(handler instanceof HandlerMethod handlerMethod)) {
			return true;
		}
		if (handlerMethod.getMethodAnnotation(RequireAuthenticate.class) != null
			&& !AuthContext.isAuthenticated()) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		if (handlerMethod.getMethodAnnotation(RequireGuest.class) != null
			&& AuthContext.isAuthenticated()) {
			throw new BusinessException(ErrorCode.ALREADY_AUTHENTICATED);
		}
		return true;
	}
}
