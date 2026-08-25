package com.delipot.chat;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * memberId는 Member 엔티티가 아직 없어(인증 파트 별도 작업 중) plain 컬럼으로만 둔다.
 * Member 파트가 들어오면 필요 시 연관관계로 바꾼다.
 */
@Entity
@Table(
	name = "chat_room_member",
	uniqueConstraints = @UniqueConstraint(columnNames = {"chat_room_id", "member_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "chat_room_id", nullable = false)
	private ChatRoom chatRoom;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(name = "last_read_message_id")
	private Long lastReadMessageId;

	@Column(name = "joined_at", nullable = false)
	private OffsetDateTime joinedAt;

	private ChatRoomMember(ChatRoom chatRoom, Long memberId, OffsetDateTime joinedAt) {
		this.chatRoom = chatRoom;
		this.memberId = memberId;
		this.joinedAt = joinedAt;
	}

	public static ChatRoomMember join(ChatRoom chatRoom, Long memberId, OffsetDateTime joinedAt) {
		return new ChatRoomMember(chatRoom, memberId, joinedAt);
	}

	/** 뒤로 후퇴하지 않는다 — 동시 read 요청이 겹쳐도 더 과거 id로 되돌아가지 않게 한다. */
	public void markRead(Long messageId) {
		if (messageId == null) {
			return;
		}
		if (lastReadMessageId == null || messageId > lastReadMessageId) {
			lastReadMessageId = messageId;
		}
	}
}
