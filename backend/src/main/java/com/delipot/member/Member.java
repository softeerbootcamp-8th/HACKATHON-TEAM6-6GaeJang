package com.delipot.member;

import java.math.BigDecimal;
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
 * 온보딩으로 가입한 회원. 휴대폰번호/비밀번호/닉네임/주소(도로명, 지번, 좌표).
 * 상태 변경은 의미 있는 메서드로만 노출하고 setter 는 두지 않는다.
 */
@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

	private static final int COORDINATE_PRECISION = 10;
	private static final int COORDINATE_SCALE = 7;

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

	@Column(length = 200)
	private String roadAddress;

	@Column(length = 200)
	private String jibunAddress;

	@Column(precision = COORDINATE_PRECISION, scale = COORDINATE_SCALE)
	private BigDecimal latitude;

	@Column(precision = COORDINATE_PRECISION, scale = COORDINATE_SCALE)
	private BigDecimal longitude;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	/** null이면 정상 회원. 탈퇴는 row를 지우지 않고 이 값만 세팅하는 soft delete다. */
	@Column
	private LocalDateTime withdrawnAt;

	private Member(String phoneNumber, String password, String nickname, String address,
		String roadAddress, String jibunAddress, BigDecimal latitude, BigDecimal longitude) {
		this.phoneNumber = phoneNumber;
		this.password = password;
		this.nickname = nickname;
		this.address = address;
		this.roadAddress = roadAddress;
		this.jibunAddress = jibunAddress;
		this.latitude = latitude;
		this.longitude = longitude;
	}

	/**
	 * 온보딩 완료 시점의 가입. 형식 검증은 요청 DTO(Bean Validation)에서 끝난 값을 받고,
	 * {@code password} 는 이미 해싱된 값을 받는다(엔티티는 해싱을 모른다).
	 */
	public static Member register(String phoneNumber, String password, String nickname, String address) {
		return new Member(phoneNumber, password, nickname, address, null, null, null, null);
	}

	public static Member register(String phoneNumber, String password, String nickname, String address,
		String roadAddress, String jibunAddress, BigDecimal latitude, BigDecimal longitude) {
		return new Member(phoneNumber, password, nickname, address, roadAddress, jibunAddress, latitude, longitude);
	}

	public void changeNickname(String nickname) {
		this.nickname = nickname;
	}

	public void changeAddress(String address, String roadAddress, String jibunAddress,
		BigDecimal latitude, BigDecimal longitude) {
		this.address = address;
		this.roadAddress = roadAddress;
		this.jibunAddress = jibunAddress;
		this.latitude = latitude;
		this.longitude = longitude;
	}

	/** soft delete. phoneNumber/nickname을 id 기반 값으로 익명화해 unique 제약을 유지한 채 재가입을 허용한다. */
	public void withdraw() {
		this.withdrawnAt = LocalDateTime.now();
		this.phoneNumber = "DEL" + id;
		this.nickname = "탈퇴" + id;
	}
}
