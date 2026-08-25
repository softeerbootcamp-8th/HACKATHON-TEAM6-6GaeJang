package com.delipot.chat;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.delipot.chat.dto.ChatMessagePageResponse;
import com.delipot.chat.dto.ChatMessageResponse;
import com.delipot.chat.dto.ChatRoomCreateRequest;
import com.delipot.chat.dto.ChatRoomResponse;
import com.delipot.chat.dto.ChatRoomSummaryResponse;
import com.delipot.global.error.BusinessException;
import com.delipot.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatService {

	private static final int MAX_PAGE_SIZE = 100;

	private final ChatRoomRepository chatRoomRepository;
	private final ChatRoomMemberRepository chatRoomMemberRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final Clock clock;

	@Transactional
	public ChatRoomResponse createRoom(Long requesterId, ChatRoomCreateRequest request) {
		if (!request.memberIds().contains(requesterId)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "요청자 본인이 참여자 목록에 포함되어야 합니다.");
		}

		OffsetDateTime now = OffsetDateTime.now(clock);
		ChatRoom room = chatRoomRepository.save(ChatRoom.create(request.name(), now));

		Set<Long> distinctMemberIds = new LinkedHashSet<>(request.memberIds());
		for (Long memberId : distinctMemberIds) {
			chatRoomMemberRepository.save(ChatRoomMember.join(room, memberId, now));
		}

		return new ChatRoomResponse(room.getId(), room.getName(), room.getCreatedAt());
	}

	@Transactional(readOnly = true)
	public List<ChatRoomSummaryResponse> getMyRooms(Long memberId) {
		List<ChatRoomMember> memberships = chatRoomMemberRepository.findByMemberId(memberId);

		List<ChatRoomSummaryResponse> result = new ArrayList<>();
		for (ChatRoomMember membership : memberships) {
			ChatRoom room = membership.getChatRoom();
			ChatMessage lastMessage = chatMessageRepository
				.findFirstByChatRoomIdOrderByIdDesc(room.getId())
				.orElse(null);

			long afterId = membership.getLastReadMessageId() != null ? membership.getLastReadMessageId() : 0L;
			long unreadCount = chatMessageRepository.countUnread(room.getId(), afterId, memberId);

			result.add(new ChatRoomSummaryResponse(
				room.getId(),
				room.getName(),
				lastMessage != null ? lastMessage.getContent() : null,
				lastMessage != null ? lastMessage.getCreatedAt() : null,
				unreadCount
			));
		}
		return result;
	}

	@Transactional(readOnly = true)
	public ChatMessagePageResponse getMessages(Long memberId, Long roomId, Long before, int size) {
		// 방 존재 여부와 무관하게 멤버가 아니면 동일하게 접근 거부로 응답 (방 존재 자체를 노출하지 않음)
		chatRoomMemberRepository.findByChatRoomIdAndMemberId(roomId, memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));

		int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		List<ChatMessage> fetched = chatMessageRepository
			.findPage(roomId, before, PageRequest.of(0, pageSize + 1));

		boolean hasNext = fetched.size() > pageSize;
		List<ChatMessage> page = hasNext ? fetched.subList(0, pageSize) : fetched;

		List<ChatMessageResponse> messages = page.stream()
			.map(this::toResponse)
			.toList();

		Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
		return new ChatMessagePageResponse(messages, nextCursor, hasNext);
	}

	/**
	 * 배달팟 멤버 가입 시 배달팟 쪽에서 직접 호출하는 내부 API (단일 모놀리식이라 HTTP 없이 서비스 메서드로 노출).
	 * 닉네임 등 회원 정보는 채팅이 몰라도 되게, 이미 완성된 문구를 호출자가 만들어 넘긴다.
	 */
	@Transactional
	public ChatMessageResponse postSystemJoinMessage(Long roomId, String content) {
		ChatRoom room = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		ChatMessage message = chatMessageRepository.save(
			ChatMessage.systemJoin(room, content, OffsetDateTime.now(clock))
		);
		return toResponse(message);
	}

	/** 참여자가 메뉴를 제출했을 때 배달팟 쪽에서 호출. menuPrice는 방 메뉴 합계 집계에 쓰인다. */
	@Transactional
	public ChatMessageResponse postSystemMenuMessage(Long roomId, String menuContent, int menuPrice) {
		ChatRoom room = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		ChatMessage message = chatMessageRepository.save(
			ChatMessage.systemMenu(room, menuContent, menuPrice, OffsetDateTime.now(clock))
		);
		return toResponse(message);
	}

	private ChatMessageResponse toResponse(ChatMessage m) {
		return new ChatMessageResponse(m.getId(), m.getType(), m.getSenderId(), m.getContent(), m.getMenuPrice(), m.getCreatedAt());
	}
}
