package com.delipot.auth.web;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 세션 쿠키의 이름/속성을 한 곳에서 관리한다. 필터(추출)와 인증 컨트롤러(발급/만료)가 공유.
 * HttpOnly 로 JS 접근을 막고, SameSite=Lax + prod 에서 Secure 로 전송 조건을 좁힌다.
 */
@Component
public class SessionCookieManager {

	private final String cookieName;
	private final Duration ttl;
	private final boolean secure;
	private final String rememberCookieName;
	private final Duration rememberTtl;

	public SessionCookieManager(
		@Value("${app.session.cookie-name}") String cookieName,
		@Value("${app.session.ttl-seconds}") long ttlSeconds,
		@Value("${app.session.cookie-secure}") boolean secure,
		@Value("${app.session.remember-cookie-name}") String rememberCookieName,
		@Value("${app.session.remember-ttl-seconds}") long rememberTtlSeconds
	) {
		this.cookieName = cookieName;
		this.ttl = Duration.ofSeconds(ttlSeconds);
		this.secure = secure;
		this.rememberCookieName = rememberCookieName;
		this.rememberTtl = Duration.ofSeconds(rememberTtlSeconds);
	}

	/** 요청 쿠키에서 세션 키를 꺼낸다. */
	public Optional<String> resolveSessionId(HttpServletRequest request) {
		return resolve(request, cookieName);
	}

	/** 요청 쿠키에서 remember-me 토큰을 꺼낸다. */
	public Optional<String> resolveRememberMe(HttpServletRequest request) {
		return resolve(request, rememberCookieName);
	}

	/** 로그인/가입 성공 시 내려줄 세션 쿠키. */
	public ResponseCookie issue(String sessionId) {
		return build(cookieName, sessionId, ttl);
	}

	/** 로그아웃 시 즉시 만료시키는 세션 쿠키(Max-Age=0). */
	public ResponseCookie expire() {
		return build(cookieName, "", Duration.ZERO);
	}

	/** 자동 로그인용 remember-me 쿠키. */
	public ResponseCookie issueRememberMe(String token) {
		return build(rememberCookieName, token, rememberTtl);
	}

	/** 로그아웃 시 remember-me 쿠키 만료. */
	public ResponseCookie expireRememberMe() {
		return build(rememberCookieName, "", Duration.ZERO);
	}

	private Optional<String> resolve(HttpServletRequest request, String name) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		return Arrays.stream(cookies)
			.filter(cookie -> name.equals(cookie.getName()))
			.map(Cookie::getValue)
			.findFirst();
	}

	private ResponseCookie build(String name, String value, Duration maxAge) {
		return ResponseCookie.from(name, value)
			.httpOnly(true)
			.secure(secure)
			.path("/")
			.sameSite("Lax")
			.maxAge(maxAge)
			.build();
	}
}
