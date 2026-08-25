package com.delipot.chat;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import com.delipot.global.config.WebSocketHandshakeInterceptor;
import com.delipot.global.error.BusinessException;
import com.delipot.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * SUBSCRIBE 프레임이 SimpleBroker로 넘어가기 전에 방 멤버십을 검증한다.
 * REST {@link ChatService#getMessages}가 하는 것과 동일한 검사를, 구독이라는 별도
 * 진입점에서 한 번 더 강제한다 — SimpleBroker 자체는 "방 멤버십" 개념을 모르기 때문에
 * 여기서 막지 않으면 아무 memberId나 아무 roomId를 구독해버릴 수 있다.
 *
 * 여기서 던진 예외는 {@code @MessageExceptionHandler}로 안 잡힌다(그건 {@code @MessageMapping}
 * 메서드 호출에만 적용됨). Spring이 이 예외를 STOMP ERROR 프레임으로 변환해 클라이언트로 보내면서
 * 커넥션 자체를 끊는다 — SEND 실패(ChatStompController)보다 훨씬 단호한 처리다.
 * 무단 구독 시도는 그 정도로 다뤄도 된다고 판단해 별도 완충 로직을 넣지 않았다.
 */
@Component
@RequiredArgsConstructor
public class ChatSubscriptionInterceptor implements ChannelInterceptor {

	private static final String ROOM_TOPIC_PREFIX = "/topic/rooms/";

	private final ChatRoomMemberRepository chatRoomMemberRepository;

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

		if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
			verifyRoomMembership(accessor);
		}

		return message;
	}

	private void verifyRoomMembership(StompHeaderAccessor accessor) {
		Long roomId = extractRoomId(accessor.getDestination());
		if (roomId == null) {
			return; // 방 topic이 아닌 구독(/user/queue/errors 등)은 검사 대상이 아니다.
		}

		Long memberId = extractMemberId(accessor);
		chatRoomMemberRepository.findByChatRoomIdAndMemberId(roomId, memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));
	}

	private Long extractRoomId(String destination) {
		if (destination == null || !destination.startsWith(ROOM_TOPIC_PREFIX)) {
			return null;
		}
		try {
			return Long.parseLong(destination.substring(ROOM_TOPIC_PREFIX.length()));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private Long extractMemberId(StompHeaderAccessor accessor) {
		Object memberId = accessor.getSessionAttributes() != null
			? accessor.getSessionAttributes().get(WebSocketHandshakeInterceptor.MEMBER_ID_ATTRIBUTE)
			: null;
		if (memberId == null) {
			throw new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
		}
		return (Long) memberId;
	}
}
