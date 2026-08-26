package com.delipot.pot;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 팟 참여 기록. 총대도 여기에 한 행으로 들어간다 — 이 테이블은 "이 팟에 속한 사람 전부"를 뜻하고,
 * 총대 여부는 {@link Pot#isHost(Long)}로 구분한다. 총대만 따로 찾게 하면 홈 목록 조회가 두 갈래가 된다.
 *
 * <p>{@code unique(pot_id, member_id)}는 중복 참여의 최종 방어선이다. 서비스가 먼저 확인하지만
 * 같은 사람이 참여 버튼을 두 번 빠르게 눌렀을 때는 DB 제약만 막을 수 있다.
 */
@Entity
@Getter
@Table(
	name = "pot_members",
	uniqueConstraints = @UniqueConstraint(name = PotMember.UK_POT_MEMBER, columnNames = {"pot_id", "member_id"}),
	// "내가 속한 팟 전부"가 홈 진입마다 실행되므로 member_id 단독 조회를 인덱스로 받친다.
	indexes = @Index(name = "idx_pot_members_member", columnList = "member_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PotMember {

	/**
	 * 중복 참여를 막는 unique 제약 이름. {@code @UniqueConstraint}와 {@link PotService}의 예외 번역이
	 * 같은 값을 쓰게 상수로 뽑았다 — 따로 적으면 이름을 바꿨을 때 번역이 조용히 안 걸린다.
	 */
	static final String UK_POT_MEMBER = "uk_pot_members_pot_member";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "pot_id", nullable = false)
	private Long potId;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	/**
	 * 참여할 때 입력한 메뉴·옵션 자유 텍스트. 총대가 배달앱에 그대로 옮겨 적는 값이라 구조화하지 않는다.
	 * 총대 자신의 행에는 없다(null) — 팟을 만들 때는 메뉴를 입력하지 않는다.
	 */
	@Column(length = 500)
	private String menuContent;

	/** 참여자가 낼 금액(원). 최소주문금액 충족 판단에 쓰인다. 총대 본인은 null. */
	@Column
	private Integer menuPrice;

	@Column(name = "joined_at", nullable = false)
	private OffsetDateTime joinedAt;

	private PotMember(Long potId, Long memberId, String menuContent, Integer menuPrice, OffsetDateTime joinedAt) {
		this.potId = potId;
		this.memberId = memberId;
		this.menuContent = menuContent;
		this.menuPrice = menuPrice;
		this.joinedAt = joinedAt;
	}

	/** 총대의 참여 기록. 팟을 만드는 시점에는 메뉴를 입력하지 않는다. */
	public static PotMember host(Long potId, Long memberId, OffsetDateTime joinedAt) {
		return new PotMember(potId, memberId, null, null, joinedAt);
	}

	/** 참여자의 참여 기록. 메뉴 입력이 곧 참여라서 메뉴 없이는 만들 수 없다. */
	public static PotMember join(
		Long potId, Long memberId, String menuContent, int menuPrice, OffsetDateTime joinedAt
	) {
		return new PotMember(potId, memberId, menuContent, menuPrice, joinedAt);
	}
}
