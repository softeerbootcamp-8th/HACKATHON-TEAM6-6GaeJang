package com.delipot.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.delipot.global.config.WebSocketHandshakeInterceptor;

import lombok.RequiredArgsConstructor;

/**
 * STOMP endpoint·destination prefix 설정. 채팅 도메인 전용이라 global/config가 아니라
 * chat 패키지에 둔다(도메인 인터셉터를 여기서 연결해야 해서, 반대로 global이 도메인을
 * 의존하게 되는 걸 피함).
 *
 * - 클라이언트 SEND 주소: /app/** → @MessageMapping 컨트롤러로 라우팅
 * - 서버 브로드캐스트 주소: /topic/** → 내장 SimpleBroker가 구독자에게 그대로 전달
 * - 채팅방별 destination: /topic/rooms/{roomId}
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class ChatWebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final WebSocketHandshakeInterceptor webSocketHandshakeInterceptor;
	private final ChatSubscriptionInterceptor chatSubscriptionInterceptor;

	/** REST CORS와 동일한 오리진 목록을 재사용한다. */
	@Value("${app.cors.allowed-origins}")
	private String[] allowedOrigins;

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// SockJS 폴백 없음(확정 사항) — stompjs가 순수 WebSocket으로 이 경로에 직접 붙는다.
		registry.addEndpoint("/ws")
			.setAllowedOrigins(allowedOrigins)
			.addInterceptors(webSocketHandshakeInterceptor);
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		// /queue는 /topic처럼 방송용이 아니라 convertAndSendToUser(에러 프레임)가 내부적으로
		// 세션별 목적지(/queue/errors-user<sessionId>)로 리라이트한 메시지를 릴레이하는 데 쓰인다.
		// 여기 없으면 SimpleBroker가 그 목적지를 프리픽스 불일치로 조용히 버린다(에러 발생 없음).
		registry.enableSimpleBroker("/topic", "/queue");
		registry.setApplicationDestinationPrefixes("/app");
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(chatSubscriptionInterceptor);
	}
}
