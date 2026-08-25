package com.delipot.auth.web;

import java.io.IOException;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import com.delipot.auth.session.RememberMeStore;
import com.delipot.auth.session.SessionStore;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 모든 요청의 인증 컨텍스트를 세팅한다. 여기서는 "확인/재발급"만 하고 요청을 막지 않는다.
 * (거부 여부는 {@link com.delipot.auth.RequireAuthenticate} 를 아는 인터셉터가 결정)
 *
 * 인증 순서: 유효 세션 → (없으면) remember-me 로 조용히 새 세션 발급.
 */
public class SessionAuthenticationFilter extends OncePerRequestFilter {

	private final SessionStore sessionStore;
	private final RememberMeStore rememberMeStore;
	private final SessionCookieManager cookieManager;

	public SessionAuthenticationFilter(
		SessionStore sessionStore,
		RememberMeStore rememberMeStore,
		SessionCookieManager cookieManager
	) {
		this.sessionStore = sessionStore;
		this.rememberMeStore = rememberMeStore;
		this.cookieManager = cookieManager;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		try {
			Long memberId = authenticate(request, response);
			if (memberId != null) {
				AuthContext.setMemberId(memberId);
			}
			filterChain.doFilter(request, response);
		} finally {
			AuthContext.clear();
		}
	}

	private Long authenticate(HttpServletRequest request, HttpServletResponse response) {
		// 1) 유효한 세션이 있으면 그대로 사용하고 TTL 만 슬라이딩 갱신.
		Optional<String> sid = cookieManager.resolveSessionId(request);
		if (sid.isPresent()) {
			Optional<Long> memberId = sessionStore.find(sid.get());
			if (memberId.isPresent()) {
				sessionStore.refresh(sid.get());
				return memberId.get();
			}
		}

		// 2) 세션이 없거나 만료됐고 remember-me 가 유효하면 조용히 새 세션을 발급한다.
		return tryRememberMe(request, response);
	}

	private Long tryRememberMe(HttpServletRequest request, HttpServletResponse response) {
		Optional<String> rid = cookieManager.resolveRememberMe(request);
		if (rid.isEmpty()) {
			return null;
		}
		Optional<Long> memberId = rememberMeStore.find(rid.get());
		if (memberId.isEmpty()) {
			return null;
		}

		Long id = memberId.get();
		// 회전: 사용한 remember-me 토큰을 폐기하고 새로 발급(탈취 재사용 창을 좁힌다).
		rememberMeStore.delete(rid.get());
		String newRememberMe = rememberMeStore.issue(id);
		String newSession = sessionStore.create(id);

		response.addHeader(HttpHeaders.SET_COOKIE, cookieManager.issue(newSession).toString());
		response.addHeader(HttpHeaders.SET_COOKIE, cookieManager.issueRememberMe(newRememberMe).toString());
		return id;
	}
}
