package com.delipot.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

/**
 * countUnread의 null-safe 비교(IS NULL OR <>)가 실제 DB(H2, MySQL 모드)에서도
 * 의도대로 동작하는지 확인한다 — 파생 쿼리(SenderIdNot)였다면 SQL의 NULL 비교 규칙 때문에
 * senderId가 null인 시스템 메시지가 조용히 안읽음 집계에서 빠지는 회귀가 있었다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("h2")
class ChatMessageRepositoryTest {

	@Autowired
	private ChatRoomRepository chatRoomRepository;

	@Autowired
	private ChatMessageRepository chatMessageRepository;

	@Test
	@DisplayName("senderId가 null인 시스템 메시지도 안읽음 개수에 포함된다")
	void countUnread_includesSystemMessages() {
		ChatRoom room = chatRoomRepository.save(ChatRoom.create("방", OffsetDateTime.now()));
		chatMessageRepository.save(ChatMessage.write(room, 2L, "안녕", OffsetDateTime.now()));
		chatMessageRepository.save(ChatMessage.systemJoin(room, "누가 들어왔어요", OffsetDateTime.now()));
		chatMessageRepository.save(ChatMessage.systemMenu(room, "허니콤보 세트", 12000, OffsetDateTime.now()));

		long unread = chatMessageRepository.countUnread(room.getId(), 0L, 1L);

		assertThat(unread).isEqualTo(3);
	}

	@Test
	@DisplayName("내가 보낸 메시지는 안읽음 개수에서 제외된다")
	void countUnread_excludesOwnMessages() {
		ChatRoom room = chatRoomRepository.save(ChatRoom.create("방", OffsetDateTime.now()));
		chatMessageRepository.save(ChatMessage.write(room, 1L, "내가 보냄", OffsetDateTime.now()));
		chatMessageRepository.save(ChatMessage.write(room, 2L, "상대가 보냄", OffsetDateTime.now()));

		long unread = chatMessageRepository.countUnread(room.getId(), 0L, 1L);

		assertThat(unread).isEqualTo(1);
	}
}
