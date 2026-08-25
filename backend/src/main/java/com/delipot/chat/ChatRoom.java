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

	/** 배달팟 만날 장소 등. 배달팟 도메인과의 연결이 아직 없어(별도 작업 중) plain 컬럼으로 둔다. */
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
}
