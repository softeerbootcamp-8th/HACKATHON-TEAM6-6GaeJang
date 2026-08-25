package com.delipot.chat;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.delipot.chat.dto.ChatErrorMessage;
import com.delipot.chat.dto.ChatMessageSendRequest;
import com.delipot.global.config.WebSocketHandshakeInterceptor;
import com.delipot.global.error.BusinessException;
import com.delipot.global.error.ErrorCode;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST({@link ChatController})와 별개로 실시간 송수신만 담당하는 STOMP 핸들러.
 *
 * 클라이언트 SEND: /app/rooms/{roomId}/messages
 * 서버 브로드캐스트: /topic/rooms/{roomId}
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatStompController {

	private final ChatService chatService;
	private final SimpMessagingTemplate messagingTemplate;

	@MessageMapping("/rooms/{roomId}/messages")
	public void sendMessage(
		@DestinationVariable Long roomId,
		@Valid @Payload ChatMessageSendRequest request,
		SimpMessageHeaderAccessor accessor
	) {
		Long senderId = extractMemberId(accessor);
		chatService.postMessage(senderId, roomId, request.content());
	}

	@MessageExceptionHandler(BusinessException.class)
	public void handleBusinessException(BusinessException ex, SimpMessageHeaderAccessor accessor) {
		log.warn("STOMP 도메인 예외: {} - {}", ex.getErrorCode(), ex.getMessage());
		sendError(accessor.getSessionId(), ex.getErrorCode().name(), ex.getMessage());
	}

	@MessageExceptionHandler(Exception.class)
	public void handleException(Exception ex, SimpMessageHeaderAccessor accessor) {
		log.error("STOMP 메시지 처리 실패", ex);
		sendError(accessor.getSessionId(), ErrorCode.INVALID_INPUT.name(), "메시지를 전송할 수 없습니다.");
	}

	private Long extractMemberId(SimpMessageHeaderAccessor accessor) {
		Object memberId = accessor.getSessionAttributes() != null
			? accessor.getSessionAttributes().get(WebSocketHandshakeInterceptor.MEMBER_ID_ATTRIBUTE)
			: null;
		if (memberId == null) {
			throw new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
		}
		return (Long) memberId;
	}

	/** Principal 없이 세션 단위로 에러를 되돌려준다. 클라이언트는 /user/queue/errors 를 구독해야 받는다. */
	private void sendError(String sessionId, String code, String message) {
		SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
		headerAccessor.setSessionId(sessionId);
		headerAccessor.setLeaveMutable(true);

		messagingTemplate.convertAndSendToUser(
			sessionId, "/queue/errors", new ChatErrorMessage(code, message), headerAccessor.getMessageHeaders()
		);
	}
}
