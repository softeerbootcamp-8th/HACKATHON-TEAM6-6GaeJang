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
		// 전체 목록은 살아 있는 팟 중 마감 전만 노출하므로 두 컬럼을 함께 탄다.
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
	 * <p>{@code @ManyToOne Member}로 걸지 않는 이유는 목록 조회가 팟 수십 건을 한 번에 읽는데
	 * 총대 정보가 카드에 안 나가서다. 연관관계를 걸면 lazy proxy가 팟마다 붙어 실수로 N+1이 나기 쉽다.
	 * 참여자 닉네임은 서비스에서 한 번에 벌크 조회해 붙인다.
	 */
	@Column(nullable = false)
	private Long hostId;

	/**
	 * 이 팟의 채팅방. 프론트가 "총대에게 메뉴 전달하기" 이후 어디로 이동할지 판단하는 값이다.
	 *
	 * <p>팟 생성 트랜잭션에서 {@link #linkChatRoom(Long)}으로 채워진다. nullable로 두는 이유는
	 * 두 가지다 — 채팅방 id는 팟이 저장돼야 알 수 있어서 INSERT 시점에는 없고,
	 * 채팅 연동 전에 만들어진 기존 팟이 null로 남아 있다.
	 *
	 * <p>단방향으로만 둔다({@code ChatRoom}은 팟을 모른다). 팟이 {@link PotStatus#DONE}이 되어 목록에서
	 * 사라진 뒤에도 방은 살아 있어야 하므로, 팟 상태 변화가 채팅 조회로 새는 통로를 만들지 않는다.
	 * 채팅방에서 팟 정보가 필요하면 이 컬럼을 거꾸로 타면 된다({@code findByChatRoomId}).
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

	/**
	 * 배달을 받아 나눌 장소의 표시용 주소. 카카오맵에서 고른 도로명 주소가 그대로 들어온다.
	 *
	 * <p>{@code Member}의 {@code address}와 같은 자리다 — 총대가 회원가입 때와 똑같은
	 * 지도 화면에서 핀을 찍어 만든다. 그래서 도로명/지번을 아래에 나란히 두고,
	 * 화면에 한 줄만 보여줄 때는 이 값을 쓴다.
	 */
	@Column(nullable = false, length = 200)
	private String meetingPlace;

	/**
	 * 만날 장소의 도로명 주소. 카카오 역지오코딩 결과를 그대로 저장한다.
	 *
	 * <p>{@code meetingPlace}와 값이 겹칠 수 있는데도 따로 두는 이유는, 좌표만 있는 산·공터처럼
	 * 도로명이 없는 지점이 있어 {@code meetingPlace}가 지번으로 채워지는 경우가 있어서다.
	 * nullable인 이유는 이 필드가 붙기 전에 만들어진 팟이 null로 남기 때문이다.
	 */
	@Column(length = 200)
	private String meetingRoadAddress;

	/** 만날 장소의 지번 주소. 도로명을 모르는 사람에게 보조로 보여준다. nullable 이유는 위와 같다. */
	@Column(length = 200)
	private String meetingJibunAddress;

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
	 * 팟이 {@code ACTIVE}인 동안 누군가 나간 적이 있는지. 총대는 완료 전엔 나갈 수 없으므로(
	 * {@link PotService#leave}) 이 시점의 "누군가"는 항상 참여자다.
	 * "총대 경험" 조건 — 완료 전 이탈이 있었으면 그 팟은 경험치로 치지 않는다 — 판정에만 쓴다.
	 */
	@Column(nullable = false)
	private boolean hasMemberLeft;

	/**
	 * "총대 N회" 배지에 이 팟을 셀지 여부. {@link #complete()}(수동/자동 모두) 시점에 딱 한 번
	 * 계산해서 고정한다 — 완료 후 참여자·총대가 나가서 {@code currentMemberCount}가 줄어도
	 * 이미 확정된 경험치가 흔들리면 안 되기 때문이다.
	 */
	@Column(nullable = false)
	private boolean countsAsHostExperience;

	/**
	 * 여러 멤버가 동시에 참여를 눌러 정원을 넘기는 것을 막기 위한 낙관적 락.
	 * 참여 기능이 붙을 때 실제로 쓰인다.
	 */
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
	 * <p>빌더로 받지 않는 이유는 채팅방 id를 알려면 팟이 먼저 저장돼 있어야 해서다
	 * (방 이름에 팟 정보가 들어간다). 통로를 빌더와 이 메서드 둘로 두면 어느 쪽이 정본인지 흐려진다.
	 *
	 * <p>재할당을 막는 이유는 방을 갈아치우면 이전 방에 남은 참여자와 메시지가 고아가 되기 때문이다.
	 * 정상 흐름에서는 절대 두 번 호출되지 않으므로 도메인 예외가 아니라 프로그래밍 오류로 던진다.
	 */
	public void linkChatRoom(Long chatRoomId) {
		if (this.chatRoomId != null) {
			throw new IllegalStateException("이미 채팅방이 연결된 팟입니다. potId=" + id);
		}
		this.chatRoomId = chatRoomId;
	}

	/** 정원이 다 찼는지. 참여 기능에서 쓰인다. */
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
	 * 검증을 여기 두면 마감 후 5시간 경과로 일괄 전이할 때(벌크 UPDATE) 규칙이 두 곳으로 갈라진다.
	 * 벌크 UPDATE 쪽은 이 메서드를 타지 않으므로 {@code countsAsHostExperience} 계산 로직을
	 * {@link PotRepository#completeAbandoned}의 {@code case when}에도 동일하게 맞춰뒀다.
	 */
	public void complete() {
		this.countsAsHostExperience = currentMemberCount > 1 && !hasMemberLeft;
		this.status = PotStatus.DONE;
	}

	/** 팟이 아직 살아 있을 때 누군가 나갔음을 기록한다. 완료 후 나가기는 경험치 판정과 무관해 무시한다. */
	public void recordMemberLeft() {
		if (isActive()) {
			this.hasMemberLeft = true;
		}
	}

	/**
	 * 참여자가 아직 총대 혼자인지. 총대만 있을 때에 한해 팟 내용 수정이 열린다.
	 *
	 * <p>참여자가 한 명이라도 들어오면 막는 이유는, 이미 전달된 메뉴가 다른 가게 기준이 되고,
	 * 송금할 계좌와 받으러 갈 장소가 참여자 모르게 바뀌기 때문이다. 잘못된 정보로 들어온 사람이
	 * 있으면 총대가 채팅으로 정리하는 쪽이 낫다 — 값을 조용히 갈아치우는 것보다 낫다.
	 */
	public boolean hasOnlyHost() {
		return currentMemberCount <= 1;
	}

	/**
	 * 팟 내용 수정. 총대 혼자일 때만 호출된다는 전제로 필드를 통째로 갈아끼운다.
	 *
	 * <p>부분 수정(null이면 유지)을 지원하지 않는 이유는 화면이 생성 폼과 같은 전체 폼이어서
	 * 항상 모든 값을 다시 보내오기 때문이다. 부분 수정을 섞으면 "지웠다"와 "안 보냈다"를
	 * 구분할 수 없어진다.
	 *
	 * <p>{@code status}/{@code currentMemberCount}/{@code chatRoomId}는 여기서 건드리지 않는다.
	 * 수정은 모집 조건을 다시 쓰는 것이지 진행 상태를 되돌리는 것이 아니다.
	 * 권한·상태 검증은 서비스가 한다({@link #complete()}와 같은 결).
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
	 * 모집 조건 확장. 정원과 마감시간을 늘리는 것만 허용한다.
	 *
	 * <p>참여자가 이미 있어도 열려 있는 유일한 변경 경로다. 늘리는 방향만 여는 이유는 이 방향이
	 * 참여자에게 손해가 없어서다 — 자리가 더 생기고 시간이 더 생긴다. 반대로 정원을 줄이면 이미
	 * 들어온 사람이 정원 밖으로 밀리고, 마감을 당기면 참여자가 기대한 시간표가 짧아진다.
	 *
	 * <p>같은 값으로 다시 호출해도 문제없다(멱등). 화면이 두 값을 함께 보내오므로 한쪽만 바꾸는
	 * 경우 다른 쪽은 현재 값 그대로 들어온다. 검증·거부는 서비스가 한다.
	 */
	public void expandRecruitment(int capacity, OffsetDateTime deadline) {
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
	 * <p>{@code currentMemberCount}를 {@code PotMember} 행 수로 매번 세지 않고 컬럼으로 들고 있는 이유는
	 * 목록 카드가 "2/4"를 그리는데, 팟마다 count 쿼리를 날리면 N+1이 되기 때문이다.
	 * 대신 이 컬럼과 실제 행 수가 어긋나지 않게 참여/나가기를 한 트랜잭션에서 같이 움직인다.
	 * 동시 참여로 정원을 넘기는 것은 {@link #version} 낙관적 락이 막는다.
	 */
	public void increaseMemberCount() {
		this.currentMemberCount++;
	}

	/** 참여자 1명 감소. 총대는 완료 전엔 나갈 수 없어 그 전까지는 0으로 떨어지지 않는다. */
	public void decreaseMemberCount() {
		this.currentMemberCount--;
	}
}
