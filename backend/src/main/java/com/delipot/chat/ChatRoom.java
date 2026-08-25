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

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	private ChatRoom(String name, OffsetDateTime createdAt) {
		this.name = name;
		this.createdAt = createdAt;
	}

	public static ChatRoom create(String name, OffsetDateTime createdAt) {
		return new ChatRoom(name, createdAt);
	}
}
