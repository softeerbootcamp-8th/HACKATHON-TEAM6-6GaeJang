package com.delipot.pot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.mockito.Mockito.mock;

import com.delipot.global.error.BusinessException;
import com.delipot.global.error.ErrorCode;
import com.delipot.chat.ChatImageUploader;
import com.delipot.chat.ChatMessageRepository;
import com.delipot.chat.ChatRoomMemberRepository;
import com.delipot.chat.ChatRoomRepository;
import com.delipot.chat.ChatService;
import com.delipot.member.Member;
import com.delipot.member.MemberRepository;
import com.delipot.member.MemberService;
import com.delipot.pot.dto.PotListRequest;
import com.delipot.pot.dto.PotListResponse;
import com.delipot.pot.dto.PotSummaryResponse;

/**
 * 세 섹션 분류와 반경 필터가 실제 DB에서 함께 동작하는지 본다.
 * 서비스만 목으로 테스트하면 JPQL 조건이 틀려도 통과하므로 여기서 같이 확인한다.
 *
 * <p>{@code @DataJpaTest}에는 서비스 빈이 없어 리포지토리만 주입받아 서비스를 직접 조립한다.
 * 목으로 대체하지 않는 이유는 검증 대상이 바로 그 쿼리들이기 때문이다.
 */
@DataJpaTest
@ActiveProfiles("h2")
class PotListIntegrationTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	/** 고정 현재 시각: 2026-08-25 18:00 KST */
	private static final Instant NOW = Instant.parse("2026-08-25T09:00:00Z");
	private static final OffsetDateTime CURRENT = NOW.atZone(SEOUL).toOffsetDateTime();

	/** 목업의 "학동로 171" 기준점. */
	private static final BigDecimal MY_LAT = new BigDecimal("37.5172000");
	private static final BigDecimal MY_LNG = new BigDecimal("127.0286000");

	@Autowired
	private PotRepository potRepository;

	@Autowired
	private PotMemberRepository potMemberRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private ChatRoomRepository chatRoomRepository;

	@Autowired
	private ChatRoomMemberRepository chatRoomMemberRepository;

	@Autowired
	private ChatMessageRepository chatMessageRepository;

	private PotService potService;
	private ChatService chatService;
	private Long meId;
	private Long strangerId;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(NOW, SEOUL);
		// 채팅도 목으로 대체하지 않는다 — 팟 생성이 실제로 방과 방 멤버 행을 남기는지가 확인 대상이다.
		chatService = new ChatService(
			chatRoomRepository, chatRoomMemberRepository, chatMessageRepository,
			memberRepository, mock(ChatImageUploader.class), mock(SimpMessagingTemplate.class), clock);
		potService = new PotService(
			potRepository, potMemberRepository, new MemberService(memberRepository), chatService, clock);

		meId = memberRepository.save(Member.register(
			"01011112222", "hash", "나", "서울시 강남구 학동로 171", null, null, MY_LAT, MY_LNG)).getId();
		strangerId = memberRepository.save(Member.register(
			"01033334444", "hash", "남", "서울시 강남구 학동로 171", null, null, MY_LAT, MY_LNG)).getId();
	}

	/** 정북으로 지정한 미터만큼 떨어진 좌표. 위도 1도 ≈ 111,320m. */
	private static BigDecimal latitudeOffsetBy(int meters) {
		return MY_LAT.add(BigDecimal.valueOf(meters / 111_320.0).setScale(7, RoundingMode.HALF_UP));
	}

	/**
	 * 팟 하나를 저장한다. 총대 참여 기록과 채팅방 연결까지 직접 심어 서비스의 create 경로와
	 * 같은 상태로 맞춘다 — join/leave/complete가 실제로 chatRoomId를 쓰기 때문에 null로
	 * 두면 안 된다.
	 */
	private Pot savePot(Long hostId, String storeName, BigDecimal latitude, OffsetDateTime deadline) {
		Pot pot = potRepository.save(Pot.builder()
			.hostId(hostId)
			.title(storeName + " 같이 시켜요")
			.description("저녁에 같이 시키실 분 구해요")
			.storeName(storeName)
			.storeUrl("https://web.coupangeats.com/share?storeId=1")
			.meetingPlace("동진시장 사거리 편의점 앞")
			.latitude(latitude)
			.longitude(MY_LNG)
			.capacity(4)
			.minOrderAmount(20000)
			.deadline(deadline)
			.bankName("카카오뱅크")
			.accountNumber("3333-01-1234567")
			.accountHolder("김하나")
			.build());

		potMemberRepository.save(PotMember.host(pot.getId(), hostId, CURRENT));

		var room = chatService.createRoom(hostId,
			new com.delipot.chat.dto.ChatRoomCreateRequest(storeName, java.util.List.of(hostId), pot.getMeetingPlace()));
		pot.linkChatRoom(room.id());

		return pot;
	}

	private com.delipot.pot.dto.PotJoinRequest menuRequest() {
		return new com.delipot.pot.dto.PotJoinRequest("허니콤보 세트 (순살로 변경) + 콜라 제로 500ml", 12000);
	}

	private PotListResponse search(String keyword) {
		return potService.findPots(meId, new PotListRequest(keyword));
	}

	private static java.util.List<String> storeNames(java.util.List<PotSummaryResponse> cards) {
		return cards.stream().map(PotSummaryResponse::storeName).toList();
	}

	// ---------- 전체 배달팟 (반경) ----------

	@Test
	@DisplayName("300m 이내 팟만 전체 목록에 나오고 밖은 걸러진다")
	void filtersByRadius() {
		savePot(strangerId, "교촌 치킨 연남점", latitudeOffsetBy(100), CURRENT.plusHours(1));
		savePot(strangerId, "호백반점", latitudeOffsetBy(280), CURRENT.plusHours(2));
		savePot(strangerId, "먼집", latitudeOffsetBy(500), CURRENT.plusHours(3));

		assertThat(storeNames(search(null).all()))
			.containsExactly("교촌 치킨 연남점", "호백반점");
	}

	@Test
	@DisplayName("전체 목록은 마감 임박순이다")
	void sortsByDeadline() {
		savePot(strangerId, "늦게마감", latitudeOffsetBy(50), CURRENT.plusHours(3));
		savePot(strangerId, "빨리마감", latitudeOffsetBy(50), CURRENT.plusHours(1));

		assertThat(storeNames(search(null).all())).containsExactly("빨리마감", "늦게마감");
	}

	@Test
	@DisplayName("정원이 찬 팟은 전체 목록에서 빠진다 — 참여할 수 없는 카드다")
	void excludesFullPots() {
		Pot full = savePot(strangerId, "정원찬집", latitudeOffsetBy(50), CURRENT.plusHours(1));
		full.join();
		full.join();
		full.join();
		potRepository.saveAndFlush(full);

		savePot(strangerId, "여유있는집", latitudeOffsetBy(50), CURRENT.plusHours(2));

		assertThat(storeNames(search(null).all())).containsExactly("여유있는집");
	}

	@Test
	@DisplayName("가게 이름으로 검색된다")
	void filtersByKeyword() {
		savePot(strangerId, "교촌 치킨 연남점", latitudeOffsetBy(50), CURRENT.plusHours(1));
		savePot(strangerId, "호백반점", latitudeOffsetBy(50), CURRENT.plusHours(2));

		assertThat(storeNames(search("치킨").all())).containsExactly("교촌 치킨 연남점");
	}

	/** 이스케이프하지 않으면 LIKE 와일드카드로 해석돼 한 글자에 전체가 나온다. */
	@Test
	@DisplayName("검색어의 %는 와일드카드가 아니라 문자로 취급된다")
	void escapesLikeWildcard() {
		savePot(strangerId, "교촌 치킨 연남점", latitudeOffsetBy(50), CURRENT.plusHours(1));

		assertThat(search("%").all()).isEmpty();
	}

	// ---------- 세 섹션 분류 ----------

	@Test
	@DisplayName("내가 총대인 팟은 hosted로, 참여한 팟은 joined로, 나머지는 all로 간다")
	void splitsIntoThreeSections() {
		savePot(meId, "내가연집", latitudeOffsetBy(50), CURRENT.plusHours(1));

		Pot joinedPot = savePot(strangerId, "참여한집", latitudeOffsetBy(50), CURRENT.plusHours(2));
		potMemberRepository.save(PotMember.join(joinedPot.getId(), meId, "허니콤보", 12000, CURRENT));
		joinedPot.join();
		potRepository.saveAndFlush(joinedPot);

		savePot(strangerId, "남의집", latitudeOffsetBy(50), CURRENT.plusHours(3));

		PotListResponse response = search(null);

		assertThat(storeNames(response.hosted())).containsExactly("내가연집");
		assertThat(storeNames(response.joined())).containsExactly("참여한집");
		assertThat(storeNames(response.all())).containsExactly("남의집");
	}

	@Test
	@DisplayName("내 팟은 전체 목록에 중복으로 뜨지 않는다")
	void myPotsAreNotDuplicatedInAll() {
		savePot(meId, "내가연집", latitudeOffsetBy(50), CURRENT.plusHours(1));

		PotListResponse response = search(null);

		assertThat(storeNames(response.hosted())).containsExactly("내가연집");
		assertThat(response.all()).isEmpty();
	}

	@Test
	@DisplayName("hosted 카드에는 isHost=true와 참여자 닉네임이 실린다")
	void hostedCardCarriesHostFlagAndMembers() {
		savePot(meId, "내가연집", latitudeOffsetBy(50), CURRENT.plusHours(1));

		PotSummaryResponse card = search(null).hosted().getFirst();

		assertThat(card.isHost()).isTrue();
		assertThat(card.members()).extracting("nickname").containsExactly("나");
		// savePot()도 create()와 마찬가지로 채팅방을 만들어 연결한다(join/leave/complete가 실제로 쓰기 때문).
		assertThat(card.chatRoomId()).isNotNull();
	}

	/**
	 * 팟의 수령 위치는 총대의 등록 주소와 다를 수 있다(회사 근처 팟을 집 주소로 가입한 사람이 만든다).
	 * 내 섹션에 반경을 걸면 내가 만든 팟이 내 목록에서 사라진다.
	 */
	@Test
	@DisplayName("내가 연 팟은 300m 밖이어도 hosted에 남는다")
	void hostedIgnoresRadius() {
		savePot(meId, "먼내팟", latitudeOffsetBy(3000), CURRENT.plusHours(1));

		assertThat(storeNames(search(null).hosted())).containsExactly("먼내팟");
	}

	// ---------- 상태 전이 ----------

	/**
	 * 마감 후가 주문·입금·수령 구간이다. 참여자에게 이 시점에 팟이 사라지면 총대 계좌를 찾을 길이 끊긴다.
	 * 반대로 참여하지 않은 사람에게는 참여할 수 없는 카드라 보일 이유가 없다.
	 */
	@Test
	@DisplayName("마감시간이 지난 팟은 전체 목록에서만 사라지고 내 섹션에는 남는다")
	void expiredPotStaysOnlyInMySections() {
		savePot(meId, "지난내팟", latitudeOffsetBy(50), CURRENT.minusMinutes(1));
		savePot(strangerId, "지난남의팟", latitudeOffsetBy(50), CURRENT.minusMinutes(1));

		PotListResponse response = search(null);

		assertThat(storeNames(response.hosted())).containsExactly("지난내팟");
		assertThat(response.all()).isEmpty();
		assertThat(response.hosted().getFirst().status()).isEqualTo(PotStatus.ACTIVE);
	}

	@Test
	@DisplayName("나눔 완료한 팟은 내 섹션에서도 사라진다")
	void donePotDisappearsEverywhere() {
		Pot mine = savePot(meId, "내가연집", latitudeOffsetBy(50), CURRENT.plusHours(1));

		potService.complete(meId, mine.getId());

		PotListResponse response = search(null);

		assertThat(response.hosted()).isEmpty();
		assertThat(response.joined()).isEmpty();
		assertThat(response.all()).isEmpty();
		assertThat(potRepository.findById(mine.getId()).orElseThrow().getStatus())
			.isEqualTo(PotStatus.DONE);
	}

	/**
	 * 총대가 버튼을 안 누르면 참여자 섹션에서 사라질 방법이 없다 — 그 섹션은 마감시간을 보지 않는다.
	 * 그래서 유예 시간이 지난 팟은 조회 시 서버가 대신 완료 처리한다.
	 */
	@Test
	@DisplayName("마감 후 5시간이 지난 팟은 조회 시 자동으로 나눔 완료되고 채팅방에 완료 공지가 남는다")
	void abandonedPotIsAutoCompleted() {
		Pot mine = savePot(meId, "방치된팟", latitudeOffsetBy(50), CURRENT.minusHours(6));

		PotListResponse response = search(null);

		assertThat(response.hosted()).isEmpty();
		assertThat(potRepository.findById(mine.getId()).orElseThrow().getStatus())
			.isEqualTo(PotStatus.DONE);

		var lastMessage = chatMessageRepository.findFirstByChatRoomIdOrderByIdDesc(mine.getChatRoomId())
			.orElseThrow();
		assertThat(lastMessage.getType()).isEqualTo(com.delipot.chat.ChatMessage.MessageType.SYSTEM_JOIN);
		assertThat(lastMessage.getContent()).isEqualTo("배달팟의 나눔이 완료되었어요");
	}

	@Test
	@DisplayName("마감 후 5시간이 안 지난 팟은 아직 내 섹션에 남는다 — 주문·입금이 진행 중인 구간이다")
	void recentlyExpiredPotSurvives() {
		savePot(meId, "진행중팟", latitudeOffsetBy(50), CURRENT.minusHours(4));

		assertThat(storeNames(search(null).hosted())).containsExactly("진행중팟");
	}

	// ---------- 참여 / 나가기 ----------

	@Test
	@DisplayName("참여하면 joined로 옮겨가고 입력한 메뉴가 참여 기록에 남는다")
	void joinMovesPotIntoJoinedSection() {
		Pot pot = savePot(strangerId, "남의집", latitudeOffsetBy(50), CURRENT.plusHours(1));

		potService.join(meId, pot.getId(), menuRequest());

		PotListResponse response = search(null);
		assertThat(storeNames(response.joined())).containsExactly("남의집");
		assertThat(response.all()).isEmpty();
		assertThat(response.joined().getFirst().currentMemberCount()).isEqualTo(2);
		assertThat(potMemberRepository.findByPotIdIn(java.util.List.of(pot.getId())))
			.filteredOn(pm -> pm.getMemberId().equals(meId))
			.singleElement()
			.satisfies(pm -> {
				assertThat(pm.getMenuContent()).isEqualTo("허니콤보 세트 (순살로 변경) + 콜라 제로 500ml");
				assertThat(pm.getMenuPrice()).isEqualTo(12000);
			});
	}

	@Test
	@DisplayName("나가면 다시 전체 목록으로 돌아가고 인원도 줄어든다")
	void leaveMovesPotBackToAll() {
		Pot pot = savePot(strangerId, "남의집", latitudeOffsetBy(50), CURRENT.plusHours(1));
		potService.join(meId, pot.getId(), menuRequest());

		potService.leave(meId, pot.getId());

		PotListResponse response = search(null);
		assertThat(response.joined()).isEmpty();
		assertThat(storeNames(response.all())).containsExactly("남의집");
		assertThat(response.all().getFirst().currentMemberCount()).isEqualTo(1);
		assertThat(potMemberRepository.existsByPotIdAndMemberId(pot.getId(), meId)).isFalse();
	}

	/** 중복 참여를 서비스가 통과시켰을 때의 최종 방어선. 인원 컬럼과 실제 행 수가 어긋나는 걸 막는다. */
	@Test
	@DisplayName("같은 사람이 같은 팟에 두 번 참여할 수 없다")
	void cannotJoinTwice() {
		Pot pot = savePot(strangerId, "남의집", latitudeOffsetBy(50), CURRENT.plusHours(1));
		potService.join(meId, pot.getId(), menuRequest());

		assertThatThrownBy(() -> potService.join(meId, pot.getId(), menuRequest()))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(ErrorCode.POT_ALREADY_JOINED);
	}

	// ---------- 생성 ----------

	@Test
	@DisplayName("팟을 만들면 총대가 참여자로 기록되고 곧바로 내가 연 배달팟에 뜬다")
	void createRegistersHostAndAppearsInHosted() {
		var created = potService.create(meId, new com.delipot.pot.dto.PotCreateRequest(
			"교촌 치킨 연남점 같이 시켜요", "교촌 치킨 연남점",
			"https://web.coupangeats.com/share?storeId=1", "동진시장 사거리 편의점 앞",
			"서울 마포구 동교로 123", "서울 마포구 연남동 227-15",
			MY_LAT, MY_LNG, 4, 20000, CURRENT.plusHours(1), "저녁에 같이 시키실 분",
			"카카오뱅크", "3333-01-1234567", "김하나"));

		assertThat(potMemberRepository.existsByPotIdAndMemberId(created.potId(), meId)).isTrue();
		assertThat(storeNames(search(null).hosted())).containsExactly("교촌 치킨 연남점");
		// 총대 혼자 있는 채팅방이 같은 트랜잭션에서 만들어진다.
		assertThat(created.chatRoomId()).isNotNull();
	}

	/**
	 * 총대가 방 멤버로 들어가 있어야 메시지 조회·전송이 통과한다.
	 * chatRoomId만 채워두고 멤버십을 빠뜨리면 총대 본인이 자기 방에서 CHAT_ROOM_ACCESS_DENIED를 맞는다.
	 */
	@Test
	@DisplayName("팟을 만들면 가게명으로 된 방이 생기고 총대가 그 방 멤버가 된다")
	void createOpensChatRoomWithHostAsMember() {
		var created = potService.create(meId, new com.delipot.pot.dto.PotCreateRequest(
			"교촌 치킨 연남점 같이 시켜요", "교촌 치킨 연남점",
			"https://web.coupangeats.com/share?storeId=1", "동진시장 사거리 편의점 앞",
			"서울 마포구 동교로 123", "서울 마포구 연남동 227-15",
			MY_LAT, MY_LNG, 4, 20000, CURRENT.plusHours(1), "저녁에 같이 시키실 분",
			"카카오뱅크", "3333-01-1234567", "김하나"));

		Long roomId = created.chatRoomId();
		assertThat(chatRoomRepository.findById(roomId)).get()
			.extracting("name").isEqualTo("교촌 치킨 연남점");
		assertThat(chatRoomMemberRepository.findByChatRoomIdAndMemberId(roomId, meId)).isPresent();
		// 참여자 입장은 아직 붙지 않았다 — 방 멤버는 총대 한 명뿐이다.
		assertThat(chatRoomMemberRepository.findByChatRoomIdAndMemberId(roomId, strangerId)).isEmpty();
	}

	// ---------- 상세 ----------

	/**
	 * "총대 N회" 배지는 참여자가 한 번이라도 있었고 완료된 팟만 센다. 그래서 참여자로
	 * {@code meId}를 하나씩 넣고 총대가 완료 처리까지 해야 배지에 잡힌다.
	 */
	@Test
	@DisplayName("상세는 총대 닉네임과 '총대 N회' 배지 값을 준다")
	void detailCarriesHostBadge() {
		Pot first = savePot(strangerId, "첫팟", latitudeOffsetBy(50), CURRENT.plusHours(1));
		Pot second = savePot(strangerId, "둘째팟", latitudeOffsetBy(50), CURRENT.plusHours(2));
		Pot third = savePot(strangerId, "셋째팟", latitudeOffsetBy(50), CURRENT.plusHours(3));
		for (Pot pot : java.util.List.of(first, second, third)) {
			potService.join(meId, pot.getId(), menuRequest());
			potService.complete(strangerId, pot.getId());
		}

		var detail = potService.findDetail(meId, third.getId());

		assertThat(detail.hostNickname()).isEqualTo("남");
		assertThat(detail.hostPotCount()).isEqualTo(3);
		assertThat(detail.isHost()).isFalse();
		assertThat(detail.isJoined()).isTrue();
	}

	/** 상세 화면은 참여 전에도 열려 있다. 계좌를 항상 실으면 참여도 안 한 사람에게 계좌번호가 나간다. */
	@Test
	@DisplayName("계좌는 참여자에게만 채워지고 비참여자에게는 null이다")
	void accountIsOnlyForMembers() {
		Pot pot = savePot(strangerId, "남의집", latitudeOffsetBy(50), CURRENT.plusHours(1));

		assertThat(potService.findDetail(meId, pot.getId()).account()).isNull();

		potService.join(meId, pot.getId(), menuRequest());

		var joined = potService.findDetail(meId, pot.getId());
		assertThat(joined.isJoined()).isTrue();
		assertThat(joined.account().accountNumber()).isEqualTo("3333-01-1234567");
		assertThat(joined.account().bankName()).isEqualTo("카카오뱅크");
	}

	@Test
	@DisplayName("나눔 완료된 팟도 상세는 조회된다 — 채팅방 헤더가 이 API를 쓴다")
	void detailWorksAfterCompletion() {
		Pot pot = savePot(meId, "끝난팟", latitudeOffsetBy(50), CURRENT.plusHours(1));
		potService.complete(meId, pot.getId());

		var detail = potService.findDetail(meId, pot.getId());

		assertThat(detail.status()).isEqualTo(PotStatus.DONE);
	}

	@Test
	@DisplayName("마감시간이 지나면 상세에 isDeadlinePassed=true — 참여하기 버튼을 막는 근거")
	void detailFlagsPassedDeadline() {
		Pot pot = savePot(strangerId, "지난팟", latitudeOffsetBy(50), CURRENT.minusMinutes(1));

		assertThat(potService.findDetail(meId, pot.getId()).isDeadlinePassed()).isTrue();
	}

	@Test
	@DisplayName("마감시간이 지난 팟에는 참여할 수 없다")
	void cannotJoinAfterDeadline() {
		Pot pot = savePot(strangerId, "지난팟", latitudeOffsetBy(50), CURRENT.minusMinutes(1));

		assertThatThrownBy(() -> potService.join(meId, pot.getId(), menuRequest()))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(ErrorCode.POT_NOT_ACTIVE);
	}

	// ---------- 주소 ----------

	@Test
	@DisplayName("주소 좌표가 없는 회원은 ADDRESS_NOT_SET — 검색 기준점이 없으면 조회 자체가 불가능하다")
	void memberWithoutCoordinatesCannotSearch() {
		Long noAddressId = memberRepository.save(
			Member.register("01055556666", "hash", "주소없음", "미입력")).getId();

		assertThatThrownBy(() -> potService.findPots(noAddressId, new PotListRequest(null)))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(ErrorCode.ADDRESS_NOT_SET);
	}

	@Test
	@DisplayName("참여 이력이 전혀 없는 신규 회원도 조회가 된다 — 빈 in절로 죽지 않는다")
	void brandNewMemberCanSearch() {
		savePot(strangerId, "남의집", latitudeOffsetBy(50), CURRENT.plusHours(1));

		PotListResponse response = search(null);

		assertThat(response.hosted()).isEmpty();
		assertThat(response.joined()).isEmpty();
		assertThat(storeNames(response.all())).containsExactly("남의집");
	}
}
