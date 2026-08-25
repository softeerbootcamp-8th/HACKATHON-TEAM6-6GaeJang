package com.delipot.member;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 온보딩으로 가입한 회원. 저장하는 값은 3가지 — 휴대폰번호/닉네임/주소.
 * 비밀번호는 없다(전화번호 인증 기반). 상태 변경은 의미 있는 메서드로만 노출하고 setter 는 두지 않는다.
 */
@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 숫자 최대 11자리. 로그인 식별자이자 가입 유일성 기준. */
	@Column(nullable = false, unique = true, length = 11)
	private String phoneNumber;

	/** BCrypt 해시. 원문 비밀번호는 저장하지 않는다. */
	@Column(nullable = false)
	private String password;

	/** 한/영 최대 10자. 온보딩 중 실시간 중복확인 대상이라 유니크. */
	@Column(nullable = false, unique = true, length = 10)
	private String nickname;

	@Column(nullable = false)
	private String address;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	private Member(String phoneNumber, String password, String nickname, String address) {
		this.phoneNumber = phoneNumber;
		this.password = password;
		this.nickname = nickname;
		this.address = address;
	}

	/**
	 * 온보딩 완료 시점의 가입. 형식 검증은 요청 DTO(Bean Validation)에서 끝난 값을 받고,
	 * {@code password} 는 이미 해싱된 값을 받는다(엔티티는 해싱을 모른다).
	 */
	public static Member register(String phoneNumber, String password, String nickname, String address) {
		return new Member(phoneNumber, password, nickname, address);
	}
}
