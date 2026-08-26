package com.delipot.chat;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_room")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	/** 배달팟의 만날 장소. 채팅은 팟을 모르는 단방향 구조라 팟 쪽에서 값만 밀어주는 plain 컬럼이다. */
	@Column(length = 200)
	private String location;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	private ChatRoom(String name, String location, OffsetDateTime createdAt) {
		this.name = name;
		this.location = location;
		this.createdAt = createdAt;
	}

	public static ChatRoom create(String name, OffsetDateTime createdAt) {
		return new ChatRoom(name, null, createdAt);
	}

	public static ChatRoom create(String name, String location, OffsetDateTime createdAt) {
		return new ChatRoom(name, location, createdAt);
	}

	/**
	 * 방 이름·장소 갱신. 배달팟 내용이 수정될 때 팟 쪽에서 호출한다.
	 *
	 * <p>방 이름은 가게명, 장소는 만날 장소라 팟에서 바뀌면 여기도 같이 바뀌어야 한다.
	 * 이 값들이 갱신되지 않으면 채팅 목록에는 옛 가게명이, 헤더에는 옛 장소가 남는다.
	 */
	public void updateInfo(String name, String location) {
		this.name = name;
		this.location = location;
	}
}
