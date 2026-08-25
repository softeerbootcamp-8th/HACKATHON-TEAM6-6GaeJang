package com.delipot.pot;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.delipot.chat.ChatService;
import com.delipot.chat.dto.ChatRoomCreateRequest;
import com.delipot.chat.dto.ChatRoomResponse;
import com.delipot.global.error.BusinessException;
import com.delipot.global.error.ErrorCode;
import com.delipot.member.Member;
import com.delipot.member.MemberService;
import com.delipot.pot.dto.PotCreateRequest;
import com.delipot.pot.dto.PotCreateResponse;
import com.delipot.pot.dto.PotDetailResponse;
import com.delipot.pot.dto.PotJoinRequest;
import com.delipot.pot.dto.PotJoinResponse;
import com.delipot.pot.dto.PotListRequest;
import com.delipot.pot.dto.PotListResponse;
import com.delipot.pot.dto.PotMemberResponse;
import com.delipot.pot.dto.PotSummaryResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PotService {

	/** 마감시간이 너무 촉박하면 아무도 참여하지 못한다. 최소 여유를 도메인 규칙으로 둔다. */
	private static final int MIN_DEADLINE_MINUTES = 10;

	/** 홈 목록 조회 반경. 걸어가서 받아올 수 있는 거리로 기획에서 정한 값이다. */
	private static final int SEARCH_RADIUS_METERS = 300;

	/**
	 * 총대가 나눔 완료를 누르지 않았을 때 자동 완료로 넘기는 유예 시간.
	 *
	 * <p>마감시간부터 재는 이유는 그 이후가 주문·배달·수령 구간이어서다. 배달이 늦거나 총대가
	 * 버튼 누르기를 잊어도 이 시간이면 끝나 있다. 너무 짧으면 아직 진행 중인 팟이 참여자 목록에서
	 * 사라져 총대 계좌를 찾을 길이 끊긴다.
	 */
	private static final int AUTO_COMPLETE_HOURS = 5;

	/**
	 * 한 번에 내려줄 팟 수의 상한. 300m 반경이면 보통 수십 건이지만, 밀집 지역에서 폭증했을 때
	 * TEXT 설명까지 전부 직렬화되는 것을 막는다. 페이징이 붙으면 페이지 크기로 대체된다.
	 */
	private static final Limit MAX_RESULTS = Limit.of(100);

	/**
	 * JPQL {@code in ()}은 빈 컬렉션에서 문법 오류가 난다. 존재할 수 없는 id를 넣어
	 * "제외할 게 없음"을 표현한다. 이걸 안 하면 참여 이력이 없는 신규 회원의 홈이 500으로 죽는다.
	 */
	private static final Long NO_SUCH_POT_ID = -1L;

	private final PotRepository potRepository;
	private final PotMemberRepository potMemberRepository;
	private final MemberService memberService;
	private final ChatService chatService;
	private final Clock clock;

	/**
	 * 팟 생성. 총대를 첫 참여자로 기록하고, 총대 혼자 있는 채팅방을 함께 만든다.
	 *
	 * <p>방 생성이 같은 트랜잭션인 이유는 방 없는 팟이 남으면 복구 경로가 없어서다 — 참여자가
	 * "메뉴 전달하기"를 눌렀을 때 들어갈 방이 없고, 총대에게 방을 다시 만들 화면도 없다.
	 * 방 생성이 실패하면 팟도 만들어지지 않는 것이 낫다(총대는 다시 누르면 된다).
	 *
	 * <p>참여자 입장·메뉴 게시는 아직 붙지 않았다. {@code ChatService}에 기존 방으로
	 * 멤버 하나를 넣는 메서드가 없어서다(방 생성 시 전원을 받는 형태만 있다).
	 *
	 * <p>의존 방향은 팟 → 채팅 단방향으로 유지한다. 채팅이 팟을 부르면 순환 참조가 되어
	 * 빈 생성 단계에서 실패한다. 채팅방에서 팟 정보가 필요하면 {@code Pot.chatRoomId}를
	 * 거꾸로 타는 조회를 팟 쪽에 열어준다.
	 */
	@Transactional
	public PotCreateResponse create(Long hostId, PotCreateRequest request) {
		validateDeadline(request.deadline());

		Pot pot = potRepository.save(Pot.builder()
			.hostId(hostId)
			.title(request.title())
			.description(request.description())
			.storeName(request.storeName())
			.storeUrl(request.storeUrl())
			.meetingPlace(request.meetingPlace())
			.latitude(request.latitude())
			.longitude(request.longitude())
			.capacity(request.capacity())
			.minOrderAmount(request.minOrderAmount())
			.deadline(request.deadline())
			.bankName(request.bankName())
			.accountNumber(request.accountNumber())
			.accountHolder(request.accountHolder())
			.build());

		potMemberRepository.save(PotMember.host(pot.getId(), hostId, OffsetDateTime.now(clock)));

		// 방 이름은 가게명. 채팅 목록에서 어느 팟의 방인지 알아볼 수 있는 유일한 단서다.
		ChatRoomResponse room = chatService.createRoom(
			hostId, new ChatRoomCreateRequest(pot.getStoreName(), List.of(hostId)));
		pot.linkChatRoom(room.id());

		return PotCreateResponse.from(pot);
	}

	/**
	 * 홈 목록. 화면의 세 섹션을 각각 다른 조건으로 뽑아 한 번에 내려준다.
	 *
	 * <ul>
	 *   <li>{@code hosted}/{@code joined} — 내가 속한 팟. 반경도 마감시간도 보지 않는다. 마감 후가
	 *       오히려 중요한 구간(주문·입금·수령)이고, 여기서 사라지는 조건은 나눔 완료뿐이다.</li>
	 *   <li>{@code all} — 300m 이내, 마감 전, 정원 여유 있고, 내가 속하지 않은 팟.
	 *       마감시간이 지나면 참여할 수 없으니 참여하지 않은 사람에게는 보일 이유가 없다.</li>
	 * </ul>
	 *
	 * <p>{@code readOnly}가 아닌 이유는 맨 앞에서 방치된 팟(마감 + 5시간)을 일괄 {@code DONE}으로
	 * 전이시키기 때문이다. 이걸 안 하면 참여자 목록에 끝난 팟이 영구히 쌓인다.
	 */
	@Transactional
	public PotListResponse findPots(Long memberId, PotListRequest request) {
		Member me = memberService.getById(memberId);
		if (me.getLatitude() == null || me.getLongitude() == null) {
			throw new BusinessException(ErrorCode.ADDRESS_NOT_SET);
		}

		OffsetDateTime now = OffsetDateTime.now(clock);
		potRepository.completeAbandoned(now.minusHours(AUTO_COMPLETE_HOURS));

		String keyword = request.searchKeyword();

		Set<Long> myPotIds = potMemberRepository.findByMemberId(memberId).stream()
			.map(PotMember::getPotId)
			.collect(Collectors.toSet());

		List<Pot> myPots = myPotIds.isEmpty()
			? List.of()
			: potRepository.findMyLivePots(myPotIds, keyword);

		List<Pot> hosted = myPots.stream().filter(pot -> pot.isHost(memberId)).toList();
		List<Pot> joined = myPots.stream().filter(pot -> !pot.isHost(memberId)).toList();
		List<Pot> others = findOthersNearby(me, myPotIds, keyword, now);

		// 세 섹션의 참여자를 한 번에 긁는다. 섹션별로 나눠 부르면 같은 쿼리가 세 번 나간다.
		Map<Long, List<PotMemberResponse>> membersByPotId =
			loadMembers(concatIds(hosted, joined, others));

		return new PotListResponse(
			toSummaries(hosted, memberId, membersByPotId),
			toSummaries(joined, memberId, membersByPotId),
			toSummaries(others, memberId, membersByPotId)
		);
	}

	/**
	 * 팟 참여. 참여 기록 + 인원 증가 + 채팅방 입장 + 입장 공지가 한 트랜잭션이다.
	 *
	 * <p>참여는 곧 메뉴 전달이다. 입력한 메뉴는 {@link PotMember}에 함께 저장된다. 메뉴를 따로
	 * 보내는 API를 두지 않는 이유는 화면이 한 버튼("총대에게 메뉴 전달하기")으로 둘을 동시에 하기
	 * 때문이다 — 나누면 참여는 됐는데 메뉴는 없는 중간 상태가 생긴다.
	 *
	 * <p>아직 채팅방 입장과 메뉴 게시는 하지 않는다. {@code ChatService}에 기존 방에 멤버 하나를
	 * 넣는 메서드가 없어서 참여자가 방 멤버가 되지 못한다(붙으면 이 두 줄 다음이 그 자리다).
	 * 응답의 {@code chatRoomId}는 팟 생성 때 채워지므로 이미 유효한 방 id다.
	 *
	 * <p>정원 초과를 막는 건 두 겹이다. 여기서 {@link Pot#isFull()}로 먼저 걸러내고,
	 * 두 사람이 같은 순간에 마지막 자리를 노렸을 때는 {@code Pot.version} 낙관적 락이 막는다
	 * (한쪽 커밋이 실패하고 {@code CONFLICT}로 응답된다). 앞의 검사만 두면 둘 다 통과해 5/4가 된다.
	 */
	@Transactional
	public PotJoinResponse join(Long memberId, Long potId, PotJoinRequest request) {
		Pot pot = findPot(potId);
		OffsetDateTime now = OffsetDateTime.now(clock);

		if (!pot.isActive()) {
			throw new BusinessException(ErrorCode.POT_NOT_ACTIVE, "나눔이 완료된 팟입니다.");
		}
		if (pot.isDeadlinePassed(now)) {
			throw new BusinessException(ErrorCode.POT_NOT_ACTIVE, "모집 시간이 지난 팟입니다.");
		}
		if (potMemberRepository.existsByPotIdAndMemberId(potId, memberId)) {
			throw new BusinessException(ErrorCode.POT_ALREADY_JOINED);
		}
		if (pot.isFull()) {
			throw new BusinessException(ErrorCode.POT_FULL);
		}

		potMemberRepository.save(
			PotMember.join(potId, memberId, request.menuContent(), request.menuPrice(), now));
		pot.increaseMemberCount();

		return new PotJoinResponse(pot.getId(), pot.getChatRoomId(), pot.getCurrentMemberCount());
	}

	/**
	 * 팟 상세. 참여 전 첫 진입 화면과 채팅방 상단 헤더가 함께 쓴다.
	 *
	 * <p>나눔 완료된 팟도 조회는 된다. 채팅방은 완료 후에도 남아 있고, 그 화면 상단이 이 API를
	 * 쓰기 때문에 여기서 막으면 완료된 팟의 채팅방 헤더가 비어버린다.
	 */
	@Transactional(readOnly = true)
	public PotDetailResponse findDetail(Long memberId, Long potId) {
		Pot pot = findPot(potId);

		boolean isJoined = potMemberRepository.existsByPotIdAndMemberId(potId, memberId);
		Map<Long, List<PotMemberResponse>> members = loadMembers(List.of(potId));

		return PotDetailResponse.of(
			pot,
			memberService.getById(pot.getHostId()).getNickname(),
			potRepository.countByHostId(pot.getHostId()),
			pot.isHost(memberId),
			isJoined,
			pot.isDeadlinePassed(OffsetDateTime.now(clock)),
			members.getOrDefault(potId, List.of())
		);
	}

	/**
	 * 팟 나가기. 채팅방의 "팟 나가기" 버튼이 이걸 부른다.
	 *
	 * <p>총대는 나갈 수 없다. 총대가 사라지면 정산 계좌 주인이 없어지고 남은 사람들이
	 * 주문을 이어받을 방법이 없다. 총대에게는 대신 나눔 완료가 있다.
	 *
	 * <p>채팅방 멤버십은 건드리지 않는다 — 채팅 담당자 작업이다.
	 */
	@Transactional
	public void leave(Long memberId, Long potId) {
		Pot pot = findPot(potId);

		if (pot.isHost(memberId)) {
			throw new BusinessException(ErrorCode.POT_HOST_CANNOT_LEAVE);
		}
		if (potMemberRepository.deleteByPotIdAndMemberId(potId, memberId) == 0) {
			throw new BusinessException(ErrorCode.POT_NOT_JOINED);
		}

		pot.decreaseMemberCount();
	}

	/**
	 * 나눔 완료. 총대가 배달을 받아 나누는 것까지 끝냈다는 뜻이고, 참여자를 포함한 모두의 목록에서
	 * 사라진다. 채팅방은 남으므로 하단 채팅 탭에서 계속 볼 수 있다.
	 *
	 * <p>마감시간 전이라도 누를 수 있다. 정원이 다 차서 일찍 주문하고 받아 나눈 경우가 정상 흐름이고,
	 * 그때 마감시간까지 기다리게 하면 끝난 팟이 전체 목록에 계속 떠 있게 된다.
	 *
	 * <p>채팅방 공지는 보내지 않는다 — 채팅 담당자 작업이다.
	 */
	@Transactional
	public void complete(Long memberId, Long potId) {
		Pot pot = findPot(potId);
		requireHost(pot, memberId);

		if (!pot.isActive()) {
			throw new BusinessException(ErrorCode.POT_NOT_ACTIVE, "이미 나눔이 완료된 팟입니다.");
		}

		pot.complete();
	}

	/** 전체 배달팟 섹션. 사각형으로 후보를 줄인 뒤 구면 거리로 모서리에 걸친 팟을 걸러낸다. */
	private List<Pot> findOthersNearby(Member me, Set<Long> myPotIds, String keyword, OffsetDateTime now) {
		Geo.Box box = Geo.boxAround(me.getLatitude(), me.getLongitude(), SEARCH_RADIUS_METERS);

		Collection<Long> excluded = myPotIds.isEmpty() ? List.of(NO_SUCH_POT_ID) : myPotIds;

		List<Pot> candidates = potRepository.findOpenPotsInBox(
			now, excluded,
			box.minLatitude(), box.maxLatitude(),
			box.minLongitude(), box.maxLongitude(),
			keyword, MAX_RESULTS
		);

		// 쿼리가 이미 마감 임박순으로 정렬해 두었고 stream 은 순서를 보존하므로 여기서 다시 정렬하지 않는다.
		return candidates.stream()
			.filter(pot -> isWithinRadius(me, pot))
			.toList();
	}

	/**
	 * 카드 아바타용 참여자를 팟 id별로 묶는다. 쿼리는 딱 두 번 — 참여 기록 전체, 닉네임 전체.
	 * 팟마다 참여자를 조회하면 목록 크기만큼(N+1), 참여자마다 회원을 조회하면 그 곱만큼 쿼리가 나간다.
	 */
	private Map<Long, List<PotMemberResponse>> loadMembers(List<Long> potIds) {
		if (potIds.isEmpty()) {
			return Map.of();
		}

		List<PotMember> potMembers = potMemberRepository.findByPotIdIn(potIds);

		Map<Long, String> nicknameById = memberService
			.findAllByIds(potMembers.stream().map(PotMember::getMemberId).distinct().toList())
			.stream()
			.collect(Collectors.toMap(Member::getId, Member::getNickname));

		return potMembers.stream().collect(Collectors.groupingBy(
			PotMember::getPotId,
			Collectors.mapping(
				// 회원이 지워진 참여 기록은 닉네임이 없다. 목록 전체를 죽이지 않게 빈 문자열로 흘린다.
				pm -> new PotMemberResponse(pm.getMemberId(), nicknameById.getOrDefault(pm.getMemberId(), "")),
				Collectors.toList()
			)
		));
	}

	private List<Long> concatIds(List<Pot> hosted, List<Pot> joined, List<Pot> others) {
		List<Long> ids = new ArrayList<>();
		for (List<Pot> pots : List.of(hosted, joined, others)) {
			pots.forEach(pot -> ids.add(pot.getId()));
		}
		return ids;
	}

	private List<PotSummaryResponse> toSummaries(
		List<Pot> pots, Long memberId, Map<Long, List<PotMemberResponse>> membersByPotId
	) {
		return pots.stream()
			.map(pot -> PotSummaryResponse.of(
				pot,
				pot.isHost(memberId),
				membersByPotId.getOrDefault(pot.getId(), List.of())
			))
			.toList();
	}

	private Pot findPot(Long potId) {
		return potRepository.findById(potId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	private void requireHost(Pot pot, Long memberId) {
		if (!pot.isHost(memberId)) {
			throw new BusinessException(ErrorCode.POT_ACCESS_DENIED);
		}
	}

	/** 사각형을 통과한 후보 중 실제 반경 밖(모서리에 걸친 팟)을 걸러낸다. */
	private boolean isWithinRadius(Member me, Pot pot) {
		double distance = Geo.distanceMeters(
			me.getLatitude(), me.getLongitude(), pot.getLatitude(), pot.getLongitude());
		return distance <= SEARCH_RADIUS_METERS;
	}

	/**
	 * {@code @Future}가 과거 시각은 걸러 주지만, "지금부터 10분"이라는 도메인 규칙은
	 * Bean Validation으로 표현할 수 없어 주입받은 Clock으로 여기서 확인한다.
	 *
	 * <p>{@link OffsetDateTime} 비교는 오프셋을 반영한 절대 시각(instant) 기준이라
	 * 서버의 JVM 타임존이 UTC든 KST든 결과가 같다. 벽시계 타입({@code LocalDateTime})으로
	 * 비교하면 UTC 서버에서 9시간 어긋나 이미 지난 마감이 통과한다.
	 */
	private void validateDeadline(OffsetDateTime deadline) {
		OffsetDateTime earliest = OffsetDateTime.now(clock).plusMinutes(MIN_DEADLINE_MINUTES);
		if (deadline.isBefore(earliest)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
				"마감시간은 현재 시각으로부터 %d분 이후여야 합니다.".formatted(MIN_DEADLINE_MINUTES));
		}
	}
}
