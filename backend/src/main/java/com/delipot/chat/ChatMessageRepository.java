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
	 * senderId가 null인 시스템 메시지(SYSTEM_JOIN, SYSTEM_MENU)도 항상 안읽음으로 잡아야 해서,
	 * 파생 쿼리의 `sender_id <> :memberId`(NULL 비교 시 UNKNOWN이 되어 제외됨) 대신 JPQL로 명시한다.
	 */
	@Query("""
		SELECT COUNT(m) FROM ChatMessage m
		WHERE m.chatRoom.id = :roomId
		  AND m.id > :afterId
		  AND (m.senderId IS NULL OR m.senderId <> :memberId)
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
}
