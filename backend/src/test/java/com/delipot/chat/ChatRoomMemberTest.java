package com.delipot.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatRoomMemberTest {

	@Test
	@DisplayName("더 최신 메시지 id로만 lastReadMessageId가 전진한다")
	void markRead_advancesForward() {
		ChatRoom room = ChatRoom.create("방", OffsetDateTime.now());
		ChatRoomMember membership = ChatRoomMember.join(room, 1L, OffsetDateTime.now());

		membership.markRead(5L);

		assertThat(membership.getLastReadMessageId()).isEqualTo(5L);
	}

	@Test
	@DisplayName("더 과거 메시지 id로는 되돌아가지 않는다 (동시 read 요청이 겹쳐도 안전)")
	void markRead_neverRegresses() {
		ChatRoom room = ChatRoom.create("방", OffsetDateTime.now());
		ChatRoomMember membership = ChatRoomMember.join(room, 1L, OffsetDateTime.now());
		membership.markRead(5L);

		membership.markRead(3L);

		assertThat(membership.getLastReadMessageId()).isEqualTo(5L);
	}

	@Test
	@DisplayName("null id는 무시한다")
	void markRead_ignoresNull() {
		ChatRoom room = ChatRoom.create("방", OffsetDateTime.now());
		ChatRoomMember membership = ChatRoomMember.join(room, 1L, OffsetDateTime.now());

		membership.markRead(null);

		assertThat(membership.getLastReadMessageId()).isNull();
	}
}
