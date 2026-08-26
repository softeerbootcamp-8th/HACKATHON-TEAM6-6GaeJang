package com.delipot.chat;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	Optional<ChatMessage> findFirstByChatRoomIdOrderByIdDesc(Long chatRoomId);

	/**
	 * SYSTEM_JOIN(입장 공지·나눔완료 공지 등)은 안읽음 배지에서 항상 제외한다. 실제 사람이 쓴
	 * 대화가 아니라 시스템 안내문이라서다. SYSTEM_MENU(참여자가 제출한 메뉴)는 대화 내용에
	 * 가까우니 그대로 센다.
	 *
	 * <p>SYSTEM_JOIN을 타입으로 걸러내고 나면 남는 타입(TEXT, IMAGE, SYSTEM_MENU)은 senderId가
	 * 항상 채워져 있으므로 `m.senderId <> :memberId`의 NULL 비교 문제(파생 쿼리 SenderIdNot이었을 때
	 * 겪었던 회귀)가 재발하지 않는다.
	 */
	@Query("""
		SELECT COUNT(m) FROM ChatMessage m
		WHERE m.chatRoom.id = :roomId
		  AND m.id > :afterId
		  AND m.type <> com.delipot.chat.ChatMessage.MessageType.SYSTEM_JOIN
		  AND m.senderId <> :memberId
		""")
	long countUnread(@Param("roomId") Long roomId, @Param("afterId") Long afterId, @Param("memberId") Long memberId);

	/** 커서(before) 기준 이전 메시지를 최신순으로 조회. before가 없으면 최신부터. */
	@Query("""
		SELECT m FROM ChatMessage m
		WHERE m.chatRoom.id = :roomId
		  AND (:before IS NULL OR m.id < :before)
		ORDER BY m.id DESC
		""")
	List<ChatMessage> findPage(@Param("roomId") Long roomId, @Param("before") Long before, Pageable pageable);

	/**
	 * 이 방에 메시지를 보낸 적 있는 senderId 전부(현재 나간 사람 포함). SYSTEM_JOIN은 senderId가
	 * 없어 자동으로 빠진다. 나간 멤버의 과거 메시지에도 닉네임·아바타를 계속 보여주기 위해
	 * {@link ChatService#getRoom}이 현재 멤버 목록과 이 결과를 합쳐 닉네임을 조회한다.
	 */
	@Query("""
		SELECT DISTINCT m.senderId FROM ChatMessage m
		WHERE m.chatRoom.id = :roomId AND m.senderId IS NOT NULL
		""")
	List<Long> findDistinctSenderIds(@Param("roomId") Long roomId);
}
