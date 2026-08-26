package com.delipot.global.config;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.delipot.auth.session.SessionStore;
import com.delipot.auth.web.SessionCookieManager;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 핸드셰이크 시점에 세션 쿠키(SID)를 검증해 memberId를 WebSocketSession attribute로 심어둔다.
 *
 * REST를 지키는 {@code SessionAuthenticationFilter}와 같은 {@link SessionStore}·
 * {@link SessionCookieManager}를 그대로 재사용한다. 다만 그 필터는 {@code /api/*}에만
 * 등록돼 있어 {@code /ws}에는 걸리지 않으므로(그 필터가 하는 슬라이딩 갱신·remember-me
 * 재발급까지 WS 커넥션 하나 여는 데 필요하지도 않다), 여기서 같은 쿠키 해석만 직접 한다.
 */
@Component
@RequiredArgsConstructor
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

	public static final String MEMBER_ID_ATTRIBUTE = "memberId";

	private final SessionStore sessionStore;
	private final SessionCookieManager cookieManager;

	@Override
	public boolean beforeHandshake(
		ServerHttpRequest request,
		ServerHttpResponse response,
		WebSocketHandler wsHandler,
		Map<String, Object> attributes
	) {
		Long memberId = resolveMemberId(request);
		if (memberId == null) {
			response.setStatusCode(HttpStatus.UNAUTHORIZED);
			return false;
		}

		attributes.put(MEMBER_ID_ATTRIBUTE, memberId);
		return true;
	}

	@Override
	public void afterHandshake(
		ServerHttpRequest request,
		ServerHttpResponse response,
		WebSocketHandler wsHandler,
		Exception exception
	) {
		// 핸드셰이크 이후 정리/로깅이 필요해지면 여기에 추가한다. 지금은 없음.
	}

	private Long resolveMemberId(ServerHttpRequest request) {
		if (!(request instanceof ServletServerHttpRequest servletRequest)) {
			return null;
		}
		HttpServletRequest httpRequest = servletRequest.getServletRequest();

		return cookieManager.resolveSessionId(httpRequest)
			.flatMap(sessionStore::find)
			.orElse(null);
	}
}
