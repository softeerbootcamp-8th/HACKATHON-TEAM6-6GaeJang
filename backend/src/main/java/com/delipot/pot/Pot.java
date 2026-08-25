package com.delipot.pot;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 배달팟 게시물. 총대가 만들고 근거리 멤버가 참여한다.
 *
 * <p>좌표({@code latitude}/{@code longitude})는 홈의 300m 반경 조회 때문에 필수다.
 * 만날 장소 텍스트만으로는 거리 계산이 불가능하다. MySQL의 {@code POINT} + spatial index 대신
 * {@code DECIMAL} 두 컬럼으로 두는 이유는 해커톤 규모(팟 수백 건)에서는
 * 바운딩 박스 필터 + 하버사인이 충분하고 JPA 매핑이 단순해서다.
 */
@Entity
@Getter
@Table(
	name = "pots",
	indexes = {
		// 홈 목록: 좌표 바운딩 박스로 먼저 후보를 줄이는 것이 가장 큰 절감이다.
		@Index(name = "idx_pots_lat_lng", columnList = "latitude, longitude"),
		// 모집 중 + 마감 전 팟만 노출하므로 두 컬럼을 함께 탄다.
		@Index(name = "idx_pots_status_deadline", columnList = "status, deadline")
	}
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pot {

	/** 좌표 정밀도. 소수점 7자리면 약 1cm 단위로, 300m 판정에 과분하다. */
	private static final int COORDINATE_PRECISION = 10;
	private static final int COORDINATE_SCALE = 7;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/**
	 * 총대(작성자)의 회원 식별자.
	 *
	 * <p>회원 도메인이 아직 없어 FK를 걸지 않는다. 팀원의 인증이 들어오면
	 * {@code @ManyToOne User host}로 승격한다 — 컬럼 타입은 그대로 BIGINT다.
	 */
	@Column(nullable = false)
	private Long hostId;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(columnDefinition = "TEXT")
	private String description;

	/** 홈 검색 기준. 링크에서 자동 추출하거나 총대가 직접 입력한 값이 그대로 들어온다. */
	@Column(nullable = false, length = 100)
	private String storeName;

	/** 배민·쿠팡이츠 등 외부 배달앱 링크. 멤버가 메뉴를 고르러 들어간다. */
	@Column(nullable = false, length = 500)
	private String storeUrl;

	/** 배달을 받아 나눌 장소. 사람이 읽는 텍스트("○○오피스텔 1층 로비"). */
	@Column(nullable = false, length = 200)
	private String meetingPlace;

	@Column(nullable = false, precision = COORDINATE_PRECISION, scale = COORDINATE_SCALE)
	private BigDecimal latitude;

	@Column(nullable = false, precision = COORDINATE_PRECISION, scale = COORDINATE_SCALE)
	private BigDecimal longitude;

	/** 총대를 포함한 모집 정원. */
	@Column(nullable = false)
	private int capacity;

	/** 총대 포함 현재 참여 인원. 참여 도메인이 붙기 전까지는 생성 시 1로 고정된다. */
	@Column(nullable = false)
	private int currentMemberCount;

	/** 가게의 최소주문금액. 이 금액이 채워지면 송금 단계로 넘어간다. */
	@Column(nullable = false)
	private int minOrderAmount;

	@Column(nullable = false)
	private LocalDateTime deadline;

	@Column(nullable = false, length = 30)
	private String bankName;

	@Column(nullable = false, length = 30)
	private String accountNumber;

	@Column(nullable = false, length = 30)
	private String accountHolder;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PotStatus status;

	/**
	 * 여러 멤버가 동시에 참여를 눌러 정원을 넘기는 것을 막기 위한 낙관적 락.
	 * 참여 기능이 붙을 때 실제로 쓰인다.
	 */
	@Version
	private Long version;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	private LocalDateTime updatedAt;

	@Builder
	private Pot(Long hostId, String title, String description, String storeName, String storeUrl,
		String meetingPlace, BigDecimal latitude, BigDecimal longitude,
		int capacity, int minOrderAmount, LocalDateTime deadline,
		String bankName, String accountNumber, String accountHolder) {
		this.hostId = hostId;
		this.title = title;
		this.description = description;
		this.storeName = storeName;
		this.storeUrl = storeUrl;
		this.meetingPlace = meetingPlace;
		this.latitude = latitude;
		this.longitude = longitude;
		this.capacity = capacity;
		this.minOrderAmount = minOrderAmount;
		this.deadline = deadline;
		this.bankName = bankName;
		this.accountNumber = accountNumber;
		this.accountHolder = accountHolder;
		// 생성 시점의 불변 규칙은 빌더 밖으로 내보내지 않는다 — 총대는 항상 첫 참여자다.
		this.currentMemberCount = 1;
		this.status = PotStatus.RECRUITING;
	}

	/** 정원이 다 찼는지. 참여 기능에서 쓰인다. */
	public boolean isFull() {
		return currentMemberCount >= capacity;
	}

	public boolean isDeadlinePassed(LocalDateTime now) {
		return !deadline.isAfter(now);
	}
}
