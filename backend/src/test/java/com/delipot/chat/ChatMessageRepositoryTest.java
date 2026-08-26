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
 * countUnread가 SYSTEM_JOIN(시스템 공지)은 항상 제외하고, SYSTEM_MENU(참여자가 낸 메뉴)는
 * 대화처럼 그대로 세는지 실제 DB(H2, MySQL 모드)에서 확인한다.
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
	@DisplayName("SYSTEM_MENU는 안읽음 개수에 포함되지만 SYSTEM_JOIN은 제외된다")
	void countUnread_includesSystemMenuButExcludesSystemJoin() {
		ChatRoom room = chatRoomRepository.save(ChatRoom.create("방", OffsetDateTime.now()));
		chatMessageRepository.save(ChatMessage.write(room, 2L, "안녕", OffsetDateTime.now()));
		chatMessageRepository.save(ChatMessage.systemJoin(room, "누가 들어왔어요", OffsetDateTime.now()));
		chatMessageRepository.save(ChatMessage.systemMenu(room, 3L, "허니콤보 세트", 12000, OffsetDateTime.now()));

		long unread = chatMessageRepository.countUnread(room.getId(), 0L, 1L);

		assertThat(unread).isEqualTo(2);
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
