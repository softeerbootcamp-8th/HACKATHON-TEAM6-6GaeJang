package com.delipot.chat;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.delipot.chat.dto.ChatMessagePageResponse;
import com.delipot.chat.dto.ChatMessageResponse;
import com.delipot.chat.dto.ChatRoomCreateRequest;
import com.delipot.chat.dto.ChatRoomDetailResponse;
import com.delipot.chat.dto.ChatRoomMemberSummary;
import com.delipot.chat.dto.ChatRoomResponse;
import com.delipot.chat.dto.ChatRoomSummaryResponse;
import com.delipot.global.error.BusinessException;
import com.delipot.global.error.ErrorCode;
import com.delipot.member.Member;
import com.delipot.member.MemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatService {

	private static final int MAX_PAGE_SIZE = 100;

	private final ChatRoomRepository chatRoomRepository;
	private final ChatRoomMemberRepository chatRoomMemberRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final MemberRepository memberRepository;
	private final ChatImageUploader chatImageUploader;
	private final SimpMessagingTemplate messagingTemplate;
	private final Clock clock;

	@Transactional
	public ChatRoomResponse createRoom(Long requesterId, ChatRoomCreateRequest request) {
		if (!request.memberIds().contains(requesterId)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "요청자 본인이 참여자 목록에 포함되어야 합니다.");
		}

		OffsetDateTime now = OffsetDateTime.now(clock);
		ChatRoom room = chatRoomRepository.save(ChatRoom.create(request.name(), request.location(), now));

		Set<Long> distinctMemberIds = new LinkedHashSet<>(request.memberIds());
		for (Long memberId : distinctMemberIds) {
			chatRoomMemberRepository.save(ChatRoomMember.join(room, memberId, now));
		}

		return new ChatRoomResponse(room.getId(), room.getName(), room.getLocation(), room.getCreatedAt());
	}

	/**
	 * 채팅방 헤더용 상세 — 이름/장소/인원수/참여자 닉네임. 방 멤버가 아니면 거부.
	 *
	 * <p>{@code memberCount}는 지금 방에 남아 있는 멤버 수다. {@code members}(닉네임 조회용)는
	 * 그보다 넓게, 이 방에 메시지를 보낸 적 있는 사람까지 합친다 — 나간 멤버가 남긴 과거 메시지도
	 * 닉네임·아바타를 계속 보여줘야 해서다({@link ChatMessageRepository#findDistinctSenderIds}).
	 */
	@Transactional(readOnly = true)
	public ChatRoomDetailResponse getRoom(Long memberId, Long roomId) {
		ChatRoomMember membership = chatRoomMemberRepository.findByChatRoomIdAndMemberId(roomId, memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));
		ChatRoom room = membership.getChatRoom();

		List<ChatRoomMember> memberships = chatRoomMemberRepository.findByChatRoomId(roomId);
		List<Long> activeMemberIds = memberships.stream().map(ChatRoomMember::getMemberId).toList();

		List<Long> nicknameTargetIds = Stream.concat(
			activeMemberIds.stream(), chatMessageRepository.findDistinctSenderIds(roomId).stream()
		).distinct().toList();

		Map<Long, String> nicknameById = memberRepository.findAllById(nicknameTargetIds).stream()
			.collect(Collectors.toMap(Member::getId, Member::getNickname));

		List<ChatRoomMemberSummary> members = nicknameTargetIds.stream()
			.map(id -> new ChatRoomMemberSummary(id, nicknameById.get(id)))
			.toList();

		return new ChatRoomDetailResponse(
			room.getId(), room.getName(), room.getLocation(), activeMemberIds.size(), members, room.getCreatedAt()
		);
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

	/** 방을 열람했을 때 최신 메시지까지 읽음 처리한다. 메시지가 없으면 no-op. */
	@Transactional
	public void markRoomRead(Long memberId, Long roomId) {
		ChatRoomMember membership = chatRoomMemberRepository.findByChatRoomIdAndMemberId(roomId, memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));

		chatMessageRepository.findFirstByChatRoomIdOrderByIdDesc(roomId)
			.ifPresent(lastMessage -> membership.markRead(lastMessage.getId()));
	}

	/** WebSocket STOMP SEND로 들어온 일반 텍스트 메시지를 저장한다. 방 멤버가 아니면 거부. */
	@Transactional
	public ChatMessageResponse postMessage(Long senderId, Long roomId, String content) {
		ChatRoomMember membership = chatRoomMemberRepository.findByChatRoomIdAndMemberId(roomId, senderId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));

		ChatMessage message = chatMessageRepository.save(
			ChatMessage.write(membership.getChatRoom(), senderId, content, OffsetDateTime.now(clock))
		);
		return saveAndBroadcast(roomId, message);
	}

	/** REST(멀티파트)로 들어온 이미지를 S3에 올리고 IMAGE 메시지로 저장한다. 방 멤버가 아니면 거부. */
	@Transactional
	public ChatMessageResponse postImageMessage(Long senderId, Long roomId, MultipartFile file) {
		ChatRoomMember membership = chatRoomMemberRepository.findByChatRoomIdAndMemberId(roomId, senderId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));

		String imageUrl = chatImageUploader.upload(roomId, file);
		ChatMessage message = chatMessageRepository.save(
			ChatMessage.image(membership.getChatRoom(), senderId, imageUrl, OffsetDateTime.now(clock))
		);
		return saveAndBroadcast(roomId, message);
	}

	/**
	 * 배달팟 멤버 가입/나가기/나눔 완료 등 배달팟 쪽에서 직접 호출하는 내부 API
	 * (단일 모놀리식이라 HTTP 없이 서비스 메서드로 노출). 닉네임 등 회원 정보는 채팅이
	 * 몰라도 되게, 이미 완성된 문구를 호출자가 만들어 넘긴다.
	 */
	@Transactional
	public ChatMessageResponse postSystemNoticeMessage(Long roomId, String content) {
		ChatRoom room = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		ChatMessage message = chatMessageRepository.save(
			ChatMessage.systemJoin(room, content, OffsetDateTime.now(clock))
		);
		return saveAndBroadcast(roomId, message);
	}

	/**
	 * 참여자가 메뉴를 제출했을 때 배달팟 쪽에서 호출. 화면에는 제출한 사람의 아바타·닉네임을 단
	 * 일반 메시지처럼(색만 다르게) 보여야 해서 senderId를 받는다. menuPrice는 방 메뉴 합계
	 * 집계에도 쓰인다.
	 */
	@Transactional
	public ChatMessageResponse postSystemMenuMessage(Long roomId, Long senderId, String menuContent, int menuPrice) {
		ChatRoom room = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		ChatMessage message = chatMessageRepository.save(
			ChatMessage.systemMenu(room, senderId, menuContent, menuPrice, OffsetDateTime.now(clock))
		);
		return saveAndBroadcast(roomId, message);
	}

	/**
	 * 배달팟 생성 시 배달팟 쪽에서 호출 — 총대가 입력한 가게 링크를 총대 명의 말풍선으로 올린다.
	 *
	 * <p>미리보기(제목·이미지·설명)는 여기서 가져오지 않는다. 방 생성 트랜잭션에 외부 HTTP 호출을
	 * 얹으면 응답이 느려지고, 그 호출이 실패하면 팟 생성 자체가 막힌다. 그래서 content에는 URL만
	 * 저장하고, 미리보기는 프론트가 방을 열람할 때 {@code POST /api/pots/store-name}으로 지연 조회한다.
	 */
	@Transactional
	public ChatMessageResponse postStoreLinkMessage(Long roomId, Long senderId, String storeUrl) {
		ChatRoom room = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		ChatMessage message = chatMessageRepository.save(
			ChatMessage.link(room, senderId, storeUrl, OffsetDateTime.now(clock))
		);
		return saveAndBroadcast(roomId, message);
	}

	/**
	 * 배달팟 참여 시 배달팟 쪽에서 호출 — 기존 방에 멤버 하나를 추가한다.
	 * 이미 멤버면 조용히 무시한다(재시도 안전).
	 */
	@Transactional
	public void addMember(Long roomId, Long memberId) {
		if (chatRoomMemberRepository.findByChatRoomIdAndMemberId(roomId, memberId).isPresent()) {
			return;
		}
		ChatRoom room = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		chatRoomMemberRepository.save(ChatRoomMember.join(room, memberId, OffsetDateTime.now(clock)));
	}

	/**
	 * 배달팟 내용 수정 시 배달팟 쪽에서 호출 — 방 이름(가게명)과 장소(만날 장소)를 맞춘다.
	 *
	 * <p>이걸 안 하면 팟은 바뀌었는데 채팅 목록에는 옛 가게명이, 헤더에는 옛 장소가 남는다.
	 * 방이 없는(연동 전) 팟도 있으므로 호출 쪽에서 {@code chatRoomId} null 여부를 먼저 본다.
	 */
	@Transactional
	public void updateRoomInfo(Long roomId, String name, String location) {
		chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND))
			.updateInfo(name, location);
	}

	/**
	 * 배달팟 나가기 시 배달팟 쪽에서 호출 — 방 멤버십을 제거한다.
	 * 이미 멤버가 아니면 조용히 무시한다(재시도 안전).
	 */
	@Transactional
	public void removeMember(Long roomId, Long memberId) {
		chatRoomMemberRepository.findByChatRoomIdAndMemberId(roomId, memberId)
			.ifPresent(chatRoomMemberRepository::delete);
	}

	/** 저장된 메시지를 방 구독자 전원에게 실시간으로 알린다. SEND/이미지/시스템 메시지가 공유하는 마지막 단계다. */
	private ChatMessageResponse saveAndBroadcast(Long roomId, ChatMessage message) {
		ChatMessageResponse response = toResponse(message);
		messagingTemplate.convertAndSend("/topic/rooms/" + roomId, response);
		return response;
	}

	private ChatMessageResponse toResponse(ChatMessage m) {
		return new ChatMessageResponse(m.getId(), m.getType(), m.getSenderId(), m.getContent(), m.getMenuPrice(), m.getCreatedAt());
	}
}
