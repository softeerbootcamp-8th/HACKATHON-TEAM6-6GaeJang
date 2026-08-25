package com.delipot.pot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.delipot.chat.ChatService;
import com.delipot.chat.dto.ChatRoomCreateRequest;
import com.delipot.chat.dto.ChatRoomResponse;
import com.delipot.global.error.BusinessException;
import com.delipot.global.error.ErrorCode;
import com.delipot.member.Member;
import com.delipot.member.MemberService;
import com.delipot.pot.dto.PotCreateRequest;
import com.delipot.pot.dto.PotCreateResponse;

@ExtendWith(MockitoExtension.class)
class PotServiceTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	/** 고정 현재 시각: 2026-08-25 18:00 KST */
	private static final Instant NOW = Instant.parse("2026-08-25T09:00:00Z");
	private static final OffsetDateTime CURRENT = NOW.atZone(SEOUL).toOffsetDateTime();
	private static final Long HOST_ID = 1L;
	private static final Long OTHER_ID = 2L;
	private static final Long POT_ID = 10L;

	private static final Long CHAT_ROOM_ID = 3L;

	@Mock
	private PotRepository potRepository;

	@Mock
	private PotMemberRepository potMemberRepository;

	@Mock
	private MemberService memberService;

	@Mock
	private ChatService chatService;

	private PotService potService() {
		return potService(Clock.fixed(NOW, SEOUL));
	}

	private PotService potService(Clock clock) {
		return new PotService(potRepository, potMemberRepository, memberService, chatService, clock);
	}

	/** 팟 하나를 총대 {@code HOST_ID}, 인원 {@code memberCount}/4, 상태 {@code status}로 만든다. */
	private Pot pot(PotStatus status, int memberCount) {
		Pot pot = Pot.builder()
			.hostId(HOST_ID)
			.title("역삼역 호백반점 같이 시켜요")
			.storeName("호백반점")
			.storeUrl("https://web.coupangeats.com/share?storeId=781313")
			.meetingPlace("역삼 스타빌 1층 로비")
			.latitude(new BigDecimal("37.5006000"))
			.longitude(new BigDecimal("127.0366000"))
			.capacity(4)
			.minOrderAmount(20000)
			.deadline(CURRENT.plusHours(1))
			.bankName("카카오뱅크")
			.accountNumber("3333-01-1234567")
			.accountHolder("김하나")
			.build();

		pot.linkChatRoom(CHAT_ROOM_ID);

		for (int i = 1; i < memberCount; i++) {
			pot.increaseMemberCount();
		}
		if (status == PotStatus.DONE) {
			pot.complete();
		}
		return pot;
	}

	/**
	 * 생성 경로 공통 스텁. 저장은 그대로 돌려주고, 채팅방은 항상 {@code CHAT_ROOM_ID}로 만들어진 것처럼 둔다.
	 * 채팅방 생성이 create()의 정상 경로에 들어와 있어서 스텁이 없으면 NPE로 죽는다.
	 */
	private void givenSaveEchoes() {
		given(potRepository.save(any(Pot.class))).willAnswer(invocation -> invocation.getArgument(0));
		given(chatService.createRoom(any(), any(ChatRoomCreateRequest.class)))
			.willReturn(new ChatRoomResponse(CHAT_ROOM_ID, "호백반점", null, CURRENT));
	}

	private PotMember capturedPotMember() {
		ArgumentCaptor<PotMember> captor = ArgumentCaptor.forClass(PotMember.class);
		verify(potMemberRepository).save(captor.capture());
		return captor.getValue();
	}

	private void givenPotExists(Pot pot) {
		given(potRepository.findById(POT_ID)).willReturn(java.util.Optional.of(pot));
	}

	private Pot capturedPot() {
		ArgumentCaptor<Pot> captor = ArgumentCaptor.forClass(Pot.class);
		verify(potRepository).save(captor.capture());
		return captor.getValue();
	}

	private PotCreateRequest request(OffsetDateTime deadline) {
		return request(deadline, 4);
	}

	private PotCreateRequest request(OffsetDateTime deadline, int capacity) {
		return new PotCreateRequest(
			"역삼역 호백반점 같이 시켜요",
			"호백반점",
			"https://web.coupangeats.com/share?storeId=781313",
			"역삼 스타빌 1층 로비",
			"서울 강남구 테헤란로 132",
			"서울 강남구 역삼동 823",
			new BigDecimal("37.5006000"),
			new BigDecimal("127.0366000"),
			capacity,
			20000,
			deadline,
			"짜장면 먹고 싶은데 최소주문금액이 안 채워져요",
			"카카오뱅크",
			"3333-01-1234567",
			"김하나"
		);
	}

	@Test
	@DisplayName("팟을 생성하면 ACTIVE 상태로 총대 본인이 첫 참여자가 된다")
	void createSetsInitialState() {
		givenSaveEchoes();

		PotCreateResponse response = potService().create(HOST_ID, request(CURRENT.plusHours(1)));

		Pot saved = capturedPot();
		assertThat(saved.getStatus()).isEqualTo(PotStatus.ACTIVE);
		assertThat(saved.getCurrentMemberCount()).isEqualTo(1);
		assertThat(saved.getHostId()).isEqualTo(1L);
		assertThat(saved.getStoreName()).isEqualTo("호백반점");
		assertThat(saved.getCapacity()).isEqualTo(4);
		assertThat(response.status()).isEqualTo(PotStatus.ACTIVE);
		assertThat(response.currentMemberCount()).isEqualTo(1);
	}

	/**
	 * 좌표는 반드시 만날 장소의 것이어야 한다. 예전 프론트는 회원 집 좌표를 실어 보냈는데,
	 * 그러면 홈의 300m 반경 판정이 실제 수령 장소와 어긋나 근처 사람에게 팟이 안 보인다.
	 */
	@Test
	@DisplayName("만날 장소는 표시 주소·도로명·지번·좌표가 요청값 그대로 저장된다")
	void createStoresMeetingAddressAsSent() {
		givenSaveEchoes();

		potService().create(HOST_ID, request(CURRENT.plusHours(1)));

		Pot saved = capturedPot();
		assertThat(saved.getMeetingPlace()).isEqualTo("역삼 스타빌 1층 로비");
		assertThat(saved.getMeetingRoadAddress()).isEqualTo("서울 강남구 테헤란로 132");
		assertThat(saved.getMeetingJibunAddress()).isEqualTo("서울 강남구 역삼동 823");
		assertThat(saved.getLatitude()).isEqualByComparingTo("37.5006000");
		assertThat(saved.getLongitude()).isEqualByComparingTo("127.0366000");
	}

	/** 지도 선택이 붙기 전에 만들어진 팟은 도로명/지번이 없다. 조회가 이걸로 깨지면 안 된다. */
	@Test
	@DisplayName("도로명·지번 없이 만들어도 저장된다 — 기존 팟 호환")
	void createAllowsMissingRoadAndJibunAddress() {
		givenSaveEchoes();

		PotCreateRequest legacy = new PotCreateRequest(
			"역삼역 호백반점 같이 시켜요", "호백반점",
			"https://web.coupangeats.com/share?storeId=781313",
			"역삼 스타빌 1층 로비", null, null,
			new BigDecimal("37.5006000"), new BigDecimal("127.0366000"),
			4, 20000, CURRENT.plusHours(1), null,
			"카카오뱅크", "3333-01-1234567", "김하나");

		potService().create(HOST_ID, legacy);

		Pot saved = capturedPot();
		assertThat(saved.getMeetingRoadAddress()).isNull();
		assertThat(saved.getMeetingJibunAddress()).isNull();
		assertThat(saved.getMeetingPlace()).isEqualTo("역삼 스타빌 1층 로비");
	}

	@Test
	@DisplayName("마감시간이 30분 미만으로 촉박하면 INVALID_INPUT으로 거부한다")
	void rejectsTooSoonDeadline() {
		assertThatThrownBy(() -> potService().create(HOST_ID, request(CURRENT.plusMinutes(29))))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(ErrorCode.INVALID_INPUT);

		verify(potRepository, never()).save(any());
	}

	@Test
	@DisplayName("마감시간이 정확히 30분 뒤면 경계값으로 허용한다")
	void allowsExactlyMinimumDeadline() {
		givenSaveEchoes();

		potService().create(HOST_ID, request(CURRENT.plusMinutes(30)));

		verify(potRepository).save(any(Pot.class));
	}

	@Test
	@DisplayName("과거 마감시간도 서비스 계층에서 거부한다 — @Valid를 우회한 호출 대비")
	void rejectsPastDeadline() {
		assertThatThrownBy(() -> potService().create(HOST_ID, request(CURRENT.minusHours(1))))
			.isInstanceOf(BusinessException.class);

		verify(potRepository, never()).save(any());
	}

	/**
	 * 회귀 방지: 이전에는 deadline이 {@code LocalDateTime}이라 서버 JVM 타임존이 UTC면
	 * KST 기준 이미 지난 마감이 통과했다. 절대 시각 비교로 바꿔서 막았다.
	 */
	@Test
	@DisplayName("서버 타임존이 UTC여도 KST 기준 지난 마감은 거부한다")
	void rejectsPastDeadlineRegardlessOfServerZone() {
		PotService utcServer = potService(Clock.fixed(NOW, ZoneOffset.UTC));

		// KST 벽시계로는 17:30 — 고정 현재 시각(18:00 KST)보다 30분 전이다.
		OffsetDateTime pastInKst = NOW.atZone(SEOUL).toOffsetDateTime().minusMinutes(30);

		assertThatThrownBy(() -> utcServer.create(HOST_ID, request(pastInKst)))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(ErrorCode.INVALID_INPUT);

		verify(potRepository, never()).save(any());
	}

	@Test
	@DisplayName("같은 절대 시각을 다른 오프셋으로 보내도 판정이 같다")
	void offsetDoesNotChangeVerdict() {
		givenSaveEchoes();

		// 둘 다 같은 순간: KST 19:00 == UTC 10:00
		OffsetDateTime asKst = CURRENT.plusHours(1);
		OffsetDateTime asUtc = asKst.withOffsetSameInstant(ZoneOffset.UTC);

		potService().create(HOST_ID, request(asKst));
		potService().create(HOST_ID, request(asUtc));

		verify(potRepository, org.mockito.Mockito.times(2)).save(any(Pot.class));
	}

	@Test
	@DisplayName("생성 직후 팟은 정원이 남아 있고 마감 전이다")
	void newPotIsOpen() {
		givenSaveEchoes();

		potService().create(HOST_ID, request(CURRENT.plusHours(1)));

		Pot saved = capturedPot();
		assertThat(saved.isFull()).isFalse();
		assertThat(saved.isDeadlinePassed(CURRENT)).isFalse();
		assertThat(saved.isDeadlinePassed(CURRENT.plusHours(2))).isTrue();
	}

	@Test
	@DisplayName("정원 2명인 팟은 총대 1명만으로는 아직 정원이 차지 않는다")
	void capacityTwoIsNotFullWithHostOnly() {
		givenSaveEchoes();

		potService().create(HOST_ID, request(CURRENT.plusHours(1), 2));

		assertThat(capturedPot().isFull()).isFalse();
	}

	// ---------- 생성 시 참여 기록 ----------

	@Test
	@DisplayName("팟을 생성하면 총대가 PotMember로도 기록된다 — 내가 연 팟 조회가 이 테이블을 쓴다")
	void createRecordsHostAsPotMember() {
		givenSaveEchoes();

		potService().create(HOST_ID, request(CURRENT.plusHours(1)));

		PotMember hostMember = capturedPotMember();
		assertThat(hostMember.getMemberId()).isEqualTo(HOST_ID);
		// 총대는 팟을 만들 때 메뉴를 입력하지 않는다.
		assertThat(hostMember.getMenuContent()).isNull();
		assertThat(hostMember.getMenuPrice()).isNull();
	}

	/** 총대 혼자 있는 방이 만들어지고 그 id가 팟과 응답에 함께 실린다. 프론트는 이 값으로 채팅 화면에 들어간다. */
	@Test
	@DisplayName("팟을 생성하면 총대 혼자 있는 채팅방이 만들어지고 chatRoomId가 채워진다")
	void createOpensChatRoomForHostOnly() {
		givenSaveEchoes();

		PotCreateResponse response = potService().create(HOST_ID, request(CURRENT.plusHours(1)));

		ArgumentCaptor<ChatRoomCreateRequest> captor = ArgumentCaptor.forClass(ChatRoomCreateRequest.class);
		verify(chatService).createRoom(eq(HOST_ID), captor.capture());
		// 방 이름은 가게명. 채팅 목록에서 어느 팟의 방인지 알아볼 단서가 이것뿐이다.
		assertThat(captor.getValue().name()).isEqualTo("호백반점");
		assertThat(captor.getValue().memberIds()).containsExactly(HOST_ID);

		assertThat(capturedPot().getChatRoomId()).isEqualTo(CHAT_ROOM_ID);
		assertThat(response.chatRoomId()).isEqualTo(CHAT_ROOM_ID);
	}

	@Test
	@DisplayName("연결된 채팅방을 다시 붙이려 하면 거부한다 — 이전 방의 참여자·메시지가 고아가 된다")
	void chatRoomCannotBeRelinked() {
		Pot pot = pot(PotStatus.ACTIVE, 1);

		assertThatThrownBy(() -> pot.linkChatRoom(99L))
			.isInstanceOf(IllegalStateException.class);

		assertThat(pot.getChatRoomId()).isEqualTo(CHAT_ROOM_ID);
	}

	@Test
	@DisplayName("마감시간이 촉박해 거부되면 참여 기록도 채팅방도 남지 않는다")
	void rejectedCreateLeavesNothing() {
		assertThatThrownBy(() -> potService().create(HOST_ID, request(CURRENT.plusMinutes(29))))
			.isInstanceOf(BusinessException.class);

		verify(potMemberRepository, never()).save(any());
		verify(chatService, never()).createRoom(any(), any());
	}

	// ---------- 참여 ----------

	@Test
	@DisplayName("참여하면 인원이 늘고 입력한 메뉴가 참여 기록에 저장된다")
	void joinIncreasesCountAndStoresMenu() {
		Pot pot = pot(PotStatus.ACTIVE, 2);
		givenPotExists(pot);
		given(potMemberRepository.existsByPotIdAndMemberId(POT_ID, OTHER_ID)).willReturn(false);
		given(memberService.getById(OTHER_ID)).willReturn(
			Member.register("01022223333", "hash", "참여자", "서울시 강남구"));

		var response = potService().join(OTHER_ID, POT_ID, menuRequest());

		assertThat(pot.getCurrentMemberCount()).isEqualTo(3);
		assertThat(response.currentMemberCount()).isEqualTo(3);

		PotMember saved = capturedPotMember();
		assertThat(saved.getMemberId()).isEqualTo(OTHER_ID);
		assertThat(saved.getMenuContent()).isEqualTo("허니콤보 세트 (순살로 변경) + 콜라 제로 500ml");
		assertThat(saved.getMenuPrice()).isEqualTo(12000);

		verify(chatService).addMember(CHAT_ROOM_ID, OTHER_ID);
		verify(chatService).postSystemJoinMessage(CHAT_ROOM_ID, "참여자님이 들어왔어요");
		verify(chatService).postSystemMenuMessage(
			CHAT_ROOM_ID, OTHER_ID, "허니콤보 세트 (순살로 변경) + 콜라 제로 500ml", 12000);
	}

	@Test
	@DisplayName("정원이 찬 팟은 POT_FULL로 거부하고 인원을 건드리지 않는다")
	void joinFullPotIsRejected() {
		Pot pot = pot(PotStatus.ACTIVE, 4);
		givenPotExists(pot);

		assertThatThrownBy(() -> potService().join(OTHER_ID, POT_ID, menuRequest()))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(ErrorCode.POT_FULL);

		assertThat(pot.getCurrentMemberCount()).isEqualTo(4);
		verify(potMemberRepository, never()).save(any());
	}

	@Test
	@DisplayName("이미 참여한 사람은 POT_ALREADY_JOINED — 두 번 눌러도 인원이 두 번 늘지 않는다")
	void joinTwiceIsRejected() {
		Pot pot = pot(PotStatus.ACTIVE, 2);
		givenPotExists(pot);
		given(potMemberRepository.existsByPotIdAndMemberId(POT_ID, OTHER_ID)).willReturn(true);

		assertThatThrownBy(() -> potService().join(OTHER_ID, POT_ID, menuRequest()))
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(ErrorCode.POT_ALREADY_JOINED);

		assertThat(pot.getCurrentMemberCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("나눔 완료된 팟에는 참여할 수 없다 — 목록에서 사라져도 URL 직접 호출은 막아야 한다")
	void joinDonePotIsRejected() {
		givenPotExists(pot(PotStatus.DONE, 2));

		assertThatThrownBy(() -> potService().join(OTHER_ID, POT_ID, menuRequest()))
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(ErrorCode.POT_NOT_ACTIVE);
	}

	/**
	 * 마감시간이 지났는데 아직 {@code ACTIVE}인 창(벌크 전이 전)에 참여 요청이 들어올 수 있다.
	 * 상태만 보면 통과하므로 시각도 함께 본다.
	 */
	@Test
	@DisplayName("상태가 아직 ACTIVE이어도 마감시간이 지났으면 참여를 거부한다")
	void joinAfterDeadlineIsRejected() {
		givenPotExists(pot(PotStatus.ACTIVE, 2));

		PotService afterDeadline = potService(Clock.fixed(NOW.plusSeconds(7200), SEOUL));

		assertThatThrownBy(() -> afterDeadline.join(OTHER_ID, POT_ID, menuRequest()))
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(ErrorCode.POT_NOT_ACTIVE);
	}

	// ---------- 나가기 ----------

	@Test
	@DisplayName("팟을 나가면 인원이 줄고 참여 기록이 지워진다")
	void leaveDecreasesCount() {
		Pot pot = pot(PotStatus.ACTIVE, 3);
		givenPotExists(pot);
		given(potMemberRepository.deleteByPotIdAndMemberId(POT_ID, OTHER_ID)).willReturn(1L);

		potService().leave(OTHER_ID, POT_ID);

		assertThat(pot.getCurrentMemberCount()).isEqualTo(2);
		verify(chatService).removeMember(CHAT_ROOM_ID, OTHER_ID);
	}

	@Test
	@DisplayName("총대는 팟을 나갈 수 없다 — 정산 계좌 주인이 사라지면 남은 사람이 주문을 이어받을 수 없다")
	void hostCannotLeave() {
		Pot pot = pot(PotStatus.ACTIVE, 3);
		givenPotExists(pot);

		assertThatThrownBy(() -> potService().leave(HOST_ID, POT_ID))
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(ErrorCode.POT_HOST_CANNOT_LEAVE);

		assertThat(pot.getCurrentMemberCount()).isEqualTo(3);
		verify(potMemberRepository, never()).deleteByPotIdAndMemberId(any(), any());
		verify(chatService, never()).removeMember(any(), any());
	}

	@Test
	@DisplayName("참여하지 않은 팟을 나가려 하면 POT_NOT_JOINED — 인원이 음수로 내려가지 않는다")
	void leaveWithoutJoiningIsRejected() {
		Pot pot = pot(PotStatus.ACTIVE, 1);
		givenPotExists(pot);
		given(potMemberRepository.deleteByPotIdAndMemberId(POT_ID, OTHER_ID)).willReturn(0L);

		assertThatThrownBy(() -> potService().leave(OTHER_ID, POT_ID))
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(ErrorCode.POT_NOT_JOINED);

		assertThat(pot.getCurrentMemberCount()).isEqualTo(1);
	}

	// ---------- 회원 탈퇴 연동 ----------

	@Test
	@DisplayName("총대로 있는 ACTIVE 팟이 있으면 hasActiveHostedPot이 true")
	void hasActiveHostedPotTrue() {
		given(potRepository.existsByHostIdAndStatus(HOST_ID, PotStatus.ACTIVE)).willReturn(true);

		assertThat(potService().hasActiveHostedPot(HOST_ID)).isTrue();
	}

	@Test
	@DisplayName("참여 중인 ACTIVE 팟에서 전부 나간다 — 나간 만큼 인원이 줄어든다")
	void leaveAllActivePotsLeavesEachOne() {
		Pot potA = pot(PotStatus.ACTIVE, 3);
		Pot potB = pot(PotStatus.ACTIVE, 2);
		given(potMemberRepository.findActivePotIdsByMemberId(OTHER_ID)).willReturn(java.util.List.of(10L, 20L));
		given(potRepository.findById(10L)).willReturn(java.util.Optional.of(potA));
		given(potRepository.findById(20L)).willReturn(java.util.Optional.of(potB));
		given(potMemberRepository.deleteByPotIdAndMemberId(10L, OTHER_ID)).willReturn(1L);
		given(potMemberRepository.deleteByPotIdAndMemberId(20L, OTHER_ID)).willReturn(1L);

		potService().leaveAllActivePots(OTHER_ID);

		assertThat(potA.getCurrentMemberCount()).isEqualTo(2);
		assertThat(potB.getCurrentMemberCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("참여 중인 팟이 없으면 아무 것도 하지 않는다")
	void leaveAllActivePotsNoop() {
		given(potMemberRepository.findActivePotIdsByMemberId(OTHER_ID)).willReturn(java.util.List.of());

		potService().leaveAllActivePots(OTHER_ID);

		verify(potMemberRepository, never()).deleteByPotIdAndMemberId(any(), any());
	}

	// ---------- 모집 마감 / 완료 ----------

	@Test
	@DisplayName("총대가 나눔 완료하면 DONE이 된다")
	void completeMovesToDone() {
		Pot pot = pot(PotStatus.ACTIVE, 3);
		givenPotExists(pot);

		potService().complete(HOST_ID, POT_ID);

		assertThat(pot.getStatus()).isEqualTo(PotStatus.DONE);
		verify(chatService).postSystemJoinMessage(CHAT_ROOM_ID, "배달팟의 나눔이 완료되었어요");
	}

	/** 정원이 차서 마감 전에 주문·수령을 끝낸 경우가 정상 흐름이다. 기다리게 하면 끝난 팟이 목록에 남는다. */
	@Test
	@DisplayName("마감시간 전에도 나눔 완료할 수 있다")
	void completeBeforeDeadlineIsAllowed() {
		Pot pot = pot(PotStatus.ACTIVE, 4);
		givenPotExists(pot);

		potService().complete(HOST_ID, POT_ID);

		assertThat(pot.getStatus()).isEqualTo(PotStatus.DONE);
	}

	@Test
	@DisplayName("총대가 아니면 나눔 완료를 할 수 없다")
	void completeByNonHostIsRejected() {
		Pot pot = pot(PotStatus.ACTIVE, 2);
		givenPotExists(pot);

		assertThatThrownBy(() -> potService().complete(OTHER_ID, POT_ID))
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(ErrorCode.POT_ACCESS_DENIED);

		assertThat(pot.getStatus()).isEqualTo(PotStatus.ACTIVE);
	}

	@Test
	@DisplayName("이미 나눔 완료된 팟을 다시 완료하면 POT_NOT_ACTIVE")
	void completeTwiceIsRejected() {
		givenPotExists(pot(PotStatus.DONE, 2));

		assertThatThrownBy(() -> potService().complete(HOST_ID, POT_ID))
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(ErrorCode.POT_NOT_ACTIVE);
	}

	@Test
	@DisplayName("존재하지 않는 팟이면 RESOURCE_NOT_FOUND")
	void unknownPotIsNotFound() {
		given(potRepository.findById(POT_ID)).willReturn(java.util.Optional.empty());

		assertThatThrownBy(() -> potService().complete(HOST_ID, POT_ID))
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
	}

	private com.delipot.pot.dto.PotJoinRequest menuRequest() {
		return new com.delipot.pot.dto.PotJoinRequest("허니콤보 세트 (순살로 변경) + 콜라 제로 500ml", 12000);
	}

	private Member member(String nickname) {
		return Member.register("01012345678", "hash", nickname, "서울시 강남구 학동로 171");
	}
}
