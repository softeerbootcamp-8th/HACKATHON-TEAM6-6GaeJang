package com.delipot.chat;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * senderId도 ChatRoomMember와 동일한 이유로 plain 컬럼. SYSTEM_JOIN(입장/완료 공지)은 실제
 * 발신자가 없어 null이다. SYSTEM_MENU는 다르다 — 화면에서 제출한 사람의 아바타·닉네임을 달고
 * 일반 메시지처럼(색만 다르게) 보여야 해서 senderId를 채운다.
 */
@Entity
@Table(name = "chat_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "chat_room_id", nullable = false)
	private ChatRoom chatRoom;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MessageType type;

	@Column(name = "sender_id")
	private Long senderId;

	@Column(nullable = false, length = 2000)
	private String content;

	/** SYSTEM_MENU일 때만 값이 있다. 방별 메뉴 금액 합계 계산에 쓴다. */
	@Column(name = "menu_price")
	private Integer menuPrice;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	private ChatMessage(
		ChatRoom chatRoom, MessageType type, Long senderId, String content, Integer menuPrice, OffsetDateTime createdAt
	) {
		this.chatRoom = chatRoom;
		this.type = type;
		this.senderId = senderId;
		this.content = content;
		this.menuPrice = menuPrice;
		this.createdAt = createdAt;
	}

	public static ChatMessage write(ChatRoom chatRoom, Long senderId, String content, OffsetDateTime createdAt) {
		return new ChatMessage(chatRoom, MessageType.TEXT, senderId, content, null, createdAt);
	}

	/** content는 S3 이미지 URL. */
	public static ChatMessage image(ChatRoom chatRoom, Long senderId, String imageUrl, OffsetDateTime createdAt) {
		return new ChatMessage(chatRoom, MessageType.IMAGE, senderId, imageUrl, null, createdAt);
	}

	public static ChatMessage systemJoin(ChatRoom chatRoom, String content, OffsetDateTime createdAt) {
		return new ChatMessage(chatRoom, MessageType.SYSTEM_JOIN, null, content, null, createdAt);
	}

	public static ChatMessage systemMenu(
		ChatRoom chatRoom, Long senderId, String menuContent, int menuPrice, OffsetDateTime createdAt
	) {
		return new ChatMessage(chatRoom, MessageType.SYSTEM_MENU, senderId, menuContent, menuPrice, createdAt);
	}

	/**
	 * content는 배달앱 가게 링크(URL) 그대로다. 제목·이미지 같은 미리보기 정보는 여기 담지 않는다
	 * — 프론트가 렌더링 시점에 {@code POST /api/pots/store-name}으로 지연 조회한다.
	 */
	public static ChatMessage link(ChatRoom chatRoom, Long senderId, String storeUrl, OffsetDateTime createdAt) {
		return new ChatMessage(chatRoom, MessageType.LINK, senderId, storeUrl, null, createdAt);
	}

	public enum MessageType {
		TEXT, IMAGE, LINK, SYSTEM_JOIN, SYSTEM_MENU
	}
}
