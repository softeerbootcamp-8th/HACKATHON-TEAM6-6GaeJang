package com.delipot.pot;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

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
 * <p>좌표는 홈의 300m 반경 조회 때문에 필수다. spatial 타입 대신 DECIMAL 두 컬럼을 쓴 이유,
 * 회원을 식별자로만 참조하는 이유 등 설계 배경은 {@code .claude/태은_주석_총정리.md} 참고.
 */
@Entity
@Getter
@Table(
	name = "pots",
	indexes = {
		// 홈 목록: 좌표 바운딩 박스로 먼저 후보를 줄이는 것이 가장 큰 절감이다.
		@Index(name = "idx_pots_lat_lng", columnList = "latitude, longitude"),
		// 전체 목록은 살아 있는 팟 중 마감 전만 노출하므로 두 컬럼을 함께 탄다.
		@Index(name = "idx_pots_status_deadline", columnList = "status, deadline")
	}
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pot {

	private static final int COORDINATE_PRECISION = 10;
	private static final int COORDINATE_SCALE = 7;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 총대(작성자)의 회원 식별자. 회원은 별도 애그리거트라 연관관계 없이 id만 보관한다. */
	@Column(nullable = false)
	private Long hostId;

	/**
	 * 이 팟의 채팅방. 프론트가 "총대에게 메뉴 전달하기" 이후 어디로 이동할지 판단하는 값이다.
	 *
	 * <p>단방향이다 — {@code ChatRoom}은 팟을 모른다. 채팅 쪽에서 팟 정보가 필요하면
	 * 이 컬럼을 거꾸로 탄다({@code findByChatRoomId}). nullable인 이유는 팟이 저장돼야
	 * 방 id를 알 수 있어서(INSERT 시점에는 없다), 그리고 채팅 연동 전 팟이 null로 남아서다.
	 */
	@Column
	private Long chatRoomId;

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

	/** 배달을 받아 나눌 장소의 표시용 주소. 화면에 한 줄만 보여줄 때 이 값을 쓴다. */
	@Column(nullable = false, length = 200)
	private String meetingPlace;

	/** 만날 장소의 도로명 주소. 도로명이 없는 지점이 있어 {@code meetingPlace}와 별도로 둔다. */
	@Column(length = 200)
	private String meetingRoadAddress;

	/** 만날 장소의 지번 주소. 도로명을 모르는 사람에게 보조로 보여준다. */
	@Column(length = 200)
	private String meetingJibunAddress;

	@Column(nullable = false, precision = COORDINATE_PRECISION, scale = COORDINATE_SCALE)
	private BigDecimal latitude;

	@Column(nullable = false, precision = COORDINATE_PRECISION, scale = COORDINATE_SCALE)
	private BigDecimal longitude;

	/** 총대를 포함한 모집 정원. */
	@Column(nullable = false)
	private int capacity;

	/**
	 * 총대 포함 현재 참여 인원. 목록 카드의 "2/4"를 위해 행 수를 세지 않고 컬럼으로 든다.
	 * 실제 {@code pot_members} 행 수와 어긋나지 않게 참여/나가기를 한 트랜잭션에서 같이 움직인다.
	 */
	@Column(nullable = false)
	private int currentMemberCount;

	/** 가게의 최소주문금액. 이 금액이 채워지면 송금 단계로 넘어간다. */
	@Column(nullable = false)
	private int minOrderAmount;

	@Column(nullable = false)
	private OffsetDateTime deadline;

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
	 * 팟이 ACTIVE인 동안 참여자가 나간 적이 있는지. 총대는 완료 전엔 나갈 수 없으므로
	 * 이 값이 true면 과거에 참여자가 있었다는 뜻이고, 총대 경험 판정에 쓰인다.
	 */
	@Column(nullable = false)
	private boolean hasMemberLeft;

	/**
	 * "총대 N회" 배지에 이 팟을 셀지 여부. 완료 시점에 한 번 계산해 고정한다 —
	 * 완료 후 사람들이 나가 인원이 줄어도 확정된 경험치가 흔들리면 안 된다.
	 *
	 * <p>계산식이 {@link PotRepository#completeAbandoned}의 {@code case when}에도 복제돼 있다.
	 * 한쪽만 고치면 자동완료된 팟의 배지가 조용히 틀어진다.
	 */
	@Column(nullable = false)
	private boolean countsAsHostExperience;

	/** 동시 참여로 정원을 넘기는 것을 막는 낙관적 락. {@link #join()}의 인원 변경에서 충돌을 잡는다. */
	@Version
	private Long version;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@UpdateTimestamp
	private OffsetDateTime updatedAt;

	@Builder
	private Pot(Long hostId, String title, String description, String storeName, String storeUrl,
		String meetingPlace, String meetingRoadAddress, String meetingJibunAddress,
		BigDecimal latitude, BigDecimal longitude,
		int capacity, int minOrderAmount, OffsetDateTime deadline,
		String bankName, String accountNumber, String accountHolder) {
		this.hostId = hostId;
		this.title = title;
		this.description = description;
		this.storeName = storeName;
		this.storeUrl = storeUrl;
		this.meetingPlace = meetingPlace;
		this.meetingRoadAddress = meetingRoadAddress;
		this.meetingJibunAddress = meetingJibunAddress;
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
		this.status = PotStatus.ACTIVE;
	}

	/**
	 * 채팅방 연결. 팟을 저장한 직후 한 번만 붙는다.
	 *
	 * <p>재할당을 막는 이유는 방을 갈아치우면 이전 방의 참여자와 메시지가 고아가 되기 때문이다.
	 * 정상 흐름에서는 두 번 호출되지 않으므로 도메인 예외가 아니라 프로그래밍 오류로 던진다.
	 */
	public void linkChatRoom(Long chatRoomId) {
		if (this.chatRoomId != null) {
			throw new IllegalStateException("이미 채팅방이 연결된 팟입니다. potId=" + id);
		}
		this.chatRoomId = chatRoomId;
	}

	public boolean isFull() {
		return currentMemberCount >= capacity;
	}

	public boolean isDeadlinePassed(OffsetDateTime now) {
		return !deadline.isAfter(now);
	}

	/**
	 * 아직 살아 있는 팟인지. 나눔 완료되면 false.
	 *
	 * <p>"참여할 수 있는지"와 다르다 — 마감시간이 지난 팟도 살아 있다.
	 * 참여 가능 여부는 이 값과 {@link #isDeadlinePassed(OffsetDateTime)}를 함께 봐야 한다.
	 */
	public boolean isActive() {
		return status == PotStatus.ACTIVE;
	}

	public boolean isHost(Long memberId) {
		return hostId.equals(memberId);
	}

	/**
	 * 나눔 완료. 참여자를 포함한 모두의 목록에서 사라진다. 채팅방은 남는다.
	 *
	 * <p>총대 권한·현재 상태 검증은 서비스가 한다 — 엔티티는 전이만 수행한다.
	 */
	public void complete() {
		this.countsAsHostExperience = currentMemberCount > 1 || hasMemberLeft;
		this.status = PotStatus.DONE;
	}

	/** 참여자가 아직 총대 혼자인지. 총대만 있을 때에 한해 팟 내용 수정이 열린다. */
	public boolean hasOnlyHost() {
		return currentMemberCount <= 1;
	}

	/**
	 * 팟 내용 수정. 총대 혼자일 때만 호출된다는 전제로 필드를 통째로 갈아끼운다(부분 수정 없음).
	 *
	 * <p>{@code status}/{@code currentMemberCount}/{@code chatRoomId}는 건드리지 않는다.
	 * 수정은 모집 조건을 다시 쓰는 것이지 진행 상태를 되돌리는 것이 아니다.
	 */
	public void update(String title, String description, String storeName, String storeUrl,
		String meetingPlace, String meetingRoadAddress, String meetingJibunAddress,
		BigDecimal latitude, BigDecimal longitude,
		int capacity, int minOrderAmount, OffsetDateTime deadline,
		String bankName, String accountNumber, String accountHolder) {
		this.title = title;
		this.description = description;
		this.storeName = storeName;
		this.storeUrl = storeUrl;
		this.meetingPlace = meetingPlace;
		this.meetingRoadAddress = meetingRoadAddress;
		this.meetingJibunAddress = meetingJibunAddress;
		this.latitude = latitude;
		this.longitude = longitude;
		this.capacity = capacity;
		this.minOrderAmount = minOrderAmount;
		this.deadline = deadline;
		this.bankName = bankName;
		this.accountNumber = accountNumber;
		this.accountHolder = accountHolder;
	}

	/**
	 * 모집 조건 확장. 참여자가 이미 있어도 열려 있는 유일한 변경 경로이므로 늘리는 방향만 허용한다.
	 * 같은 값으로 다시 호출해도 문제없다(멱등). 사용자용 검증·거부는 서비스가 한다.
	 */
	public void expandRecruitment(int capacity, OffsetDateTime deadline) {
		if (!isExpansionOf(capacity, deadline)) {
			throw new IllegalStateException(
				"모집 조건은 축소할 수 없다: capacity %d -> %d, deadline %s -> %s"
					.formatted(this.capacity, capacity, this.deadline, deadline));
		}
		this.capacity = capacity;
		this.deadline = deadline;
	}

	/** 확장 방향인지. 정원·마감 둘 다 현재 값 이상이어야 한다. */
	public boolean isExpansionOf(int newCapacity, OffsetDateTime newDeadline) {
		return newCapacity >= this.capacity && !newDeadline.isBefore(this.deadline);
	}

	/**
	 * 참여자 1명 증가.
	 *
	 * <p>정원 검사는 마지막 방어선이다 — 사용자에게 보여줄 {@code POT_FULL} 판정은 서비스가 하고,
	 * 여기 걸리는 것은 그 검사를 빠뜨린 버그다. 그래서 400이 아니라 500으로 드러나야 한다.
	 */
	public void join() {
		if (isFull()) {
			throw new IllegalStateException(
				"정원을 넘겨 참여시킬 수 없다: potId=%d, %d/%d".formatted(id, currentMemberCount, capacity));
		}
		this.currentMemberCount++;
	}

	/**
	 * 참여자 1명 감소. 팟이 살아 있는 동안의 이탈이면 경험 판정용 이력({@code hasMemberLeft})도 함께 남긴다.
	 * 두 변경을 묶은 이유는 이력 기록을 빠뜨렸을 때 총대 경험 판정이 조용히 틀어지기 때문이다.
	 */
	public void leave() {
		if (currentMemberCount <= 0) {
			throw new IllegalStateException("참여자가 없는 팟에서 나갈 수 없다: potId=%d".formatted(id));
		}
		if (isActive()) {
			this.hasMemberLeft = true;
		}
		this.currentMemberCount--;
	}
}
