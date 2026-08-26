package com.delipot.pot;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
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
import com.delipot.pot.dto.PotRecruitmentUpdateRequest;
import com.delipot.pot.dto.PotSummaryResponse;
import com.delipot.pot.dto.PotUpdateRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PotService {

	/** 마감시간이 너무 촉박하면 아무도 참여하지 못한다. 최소 여유를 도메인 규칙으로 둔다. */
	private static final int MIN_DEADLINE_MINUTES = 30;

	/** 채팅 공지에 찍는 마감시간의 기준 시간대. 참여자는 전부 같은 동네에 있다. */
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter DEADLINE_NOTICE_FORMAT = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

	/** 홈 목록 조회 반경. 걸어가서 받아올 수 있는 거리로 기획에서 정한 값이다. */
	private static final int SEARCH_RADIUS_METERS = 300;

	/** 총대가 나눔 완료를 누르지 않았을 때 자동 완료로 넘기는 유예 시간. 마감시간부터 잰다. */
	private static final int AUTO_COMPLETE_HOURS = 5;

	/** 한 번에 내려줄 팟 수의 상한. 페이징이 붙으면 페이지 크기로 대체된다. */
	private static final Limit MAX_RESULTS = Limit.of(100);

	private final PotRepository potRepository;
	private final PotMemberRepository potMemberRepository;
	private final MemberService memberService;
	private final ChatService chatService;
	private final Clock clock;

	/**
	 * 팟 생성. 총대를 첫 참여자로 기록하고, 총대 혼자 있는 채팅방과 가게 링크 말풍선을 함께 만든다.
	 *
	 * <p>방 생성이 같은 트랜잭션인 이유는 방 없는 팟이 남으면 복구 경로가 없어서다 — 참여자가
	 * 들어갈 방이 없고, 총대에게 방을 다시 만들 화면도 없다.
	 *
	 * <p>의존 방향은 팟 → 채팅 단방향을 유지한다. 채팅이 팟을 부르면 순환 참조가 된다.
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
			.meetingRoadAddress(request.meetingRoadAddress())
			.meetingJibunAddress(request.meetingJibunAddress())
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
			hostId, new ChatRoomCreateRequest(pot.getStoreName(), List.of(hostId), pot.getMeetingPlace()));
		pot.linkChatRoom(room.id());
		chatService.postStoreLinkMessage(room.id(), hostId, pot.getStoreUrl());

		return PotCreateResponse.from(pot);
	}

	/**
	 * 팟 내용 수정. 총대 본인이, 아직 참여자가 없고({@link ErrorCode#POT_NOT_EDITABLE}),
	 * 나눔이 끝나지 않은 팟만 고칠 수 있다.
	 *
	 * <p>수정 폼을 열어 둔 사이 누군가 참여하는 경합은 {@code Pot.version} 낙관적 락이 막는다 —
	 * 여기 인원 검사만 두면 폼을 연 시점의 낡은 값으로 통과할 수 있다.
	 *
	 * <p>가게명·만날 장소가 바뀌면 연결된 채팅방의 이름·장소도 함께 맞춘다. 채팅방은 팟을 모르므로
	 * 여기서 밀어주지 않으면 옛 값이 그대로 남는다.
	 */
	@Transactional
	public void update(Long memberId, Long potId, PotUpdateRequest request) {
		Pot pot = findPot(potId);
		requireHost(pot, memberId);

		if (!pot.isActive()) {
			throw new BusinessException(ErrorCode.POT_NOT_ACTIVE, "이미 나눔이 완료된 팟입니다.");
		}
		if (!pot.hasOnlyHost()) {
			throw new BusinessException(ErrorCode.POT_NOT_EDITABLE);
		}
		validateDeadline(request.deadline());

		pot.update(
			request.title(),
			request.description(),
			request.storeName(),
			request.storeUrl(),
			request.meetingPlace(),
			request.meetingRoadAddress(),
			request.meetingJibunAddress(),
			request.latitude(),
			request.longitude(),
			request.capacity(),
			request.minOrderAmount(),
			request.deadline(),
			request.bankName(),
			request.accountNumber(),
			request.accountHolder()
		);

		if (pot.getChatRoomId() != null) {
			chatService.updateRoomInfo(pot.getChatRoomId(), pot.getStoreName(), pot.getMeetingPlace());
		}
	}

	/**
	 * 모집 조건 확장. 정원·마감시간을 늘린다. 참여자가 이미 있어도 열려 있는 유일한 변경 경로다.
	 *
	 * <p>마감이 이미 지난 팟도 늘릴 수 있다(정원이 안 차 마감만 지난 팟을 살리는 것이 주 용도).
	 * {@code DONE}이면 막는다 — 끝난 팟을 되살리는 통로는 아니다.
	 *
	 * <p>값이 실제로 바뀐 경우에만 채팅방에 공지한다. 같은 값 저장에 공지가 나가면 방이 시끄러워진다.
	 */
	@Transactional
	public void expandRecruitment(Long memberId, Long potId, PotRecruitmentUpdateRequest request) {
		Pot pot = findPot(potId);
		requireHost(pot, memberId);

		if (!pot.isActive()) {
			throw new BusinessException(ErrorCode.POT_NOT_ACTIVE, "이미 나눔이 완료된 팟입니다.");
		}
		if (!pot.isExpansionOf(request.capacity(), request.deadline())) {
			throw new BusinessException(ErrorCode.POT_RECRUITMENT_CANNOT_SHRINK);
		}
		validateDeadline(request.deadline());

		List<String> changes = describeExpansion(pot, request);
		pot.expandRecruitment(request.capacity(), request.deadline());

		if (!changes.isEmpty() && pot.getChatRoomId() != null) {
			chatService.postSystemNoticeMessage(pot.getChatRoomId(), String.join(", ", changes) + " 변경되었어요");
		}
	}

	/**
	 * 공지 문구용 변경 내역. 확장 직전에 뽑아야 이전 값을 읽을 수 있다.
	 * 마감시간은 KST 벽시계로 찍는다 — 저장값을 그대로 쓰면 요청이 보낸 오프셋이 노출된다.
	 */
	private List<String> describeExpansion(Pot pot, PotRecruitmentUpdateRequest request) {
		List<String> changes = new ArrayList<>();
		if (request.capacity() != pot.getCapacity()) {
			changes.add("배달팟 인원이 " + request.capacity() + "명으로");
		}
		if (!request.deadline().isEqual(pot.getDeadline())) {
			changes.add("마감 시간이 "
				+ request.deadline().atZoneSameInstant(KST).format(DEADLINE_NOTICE_FORMAT) + "으로");
		}
		return changes;
	}

	/**
	 * 홈 목록. 화면의 세 섹션을 각각 다른 조건으로 뽑아 한 번에 내려준다.
	 *
	 * <ul>
	 *   <li>{@code hosted}/{@code joined} — 내가 속한 팟. 반경도 마감시간도 보지 않는다.</li>
	 *   <li>{@code all} — 300m 이내, 마감 전, 정원 여유 있고, 내가 속하지 않은 팟.</li>
	 * </ul>
	 *
	 * <p>{@code readOnly}가 아닌 이유는 맨 앞에서 방치된 팟(마감 + 5시간)을 일괄 {@code DONE}으로
	 * 전이시키고 각 채팅방에 완료 공지를 남기기 때문이다.
	 */
	@Transactional
	public PotListResponse findPots(Long memberId, PotListRequest request) {
		Member me = memberService.getById(memberId);
		if (me.getLatitude() == null || me.getLongitude() == null) {
			throw new BusinessException(ErrorCode.ADDRESS_NOT_SET);
		}

		OffsetDateTime now = OffsetDateTime.now(clock);
		completeAbandonedPots(now);

		String keyword = request.searchKeyword();

		List<Pot> myPots = potRepository.findMyLivePots(memberId, keyword);

		List<Pot> hosted = myPots.stream().filter(pot -> pot.isHost(memberId)).toList();
		List<Pot> joined = myPots.stream().filter(pot -> !pot.isHost(memberId)).toList();
		List<Pot> others = findOthersNearby(me, memberId, keyword, now);

		// 세 섹션의 참여자를 한 번에 긁는다. 섹션별로 나눠 부르면 같은 쿼리가 세 번 나간다.
		List<Pot> allPots = concatPots(hosted, joined, others);
		Map<Long, List<PotMemberResponse>> membersByPotId = loadMembers(allPots);

		return new PotListResponse(
			toSummaries(hosted, memberId, membersByPotId),
			toSummaries(joined, memberId, membersByPotId),
			toSummaries(others, memberId, membersByPotId)
		);
	}

	/**
	 * 팟 참여. 참여 기록 + 인원 증가 + 채팅방 입장 + 입장/메뉴 공지가 한 트랜잭션이다.
	 * 참여가 곧 메뉴 전달이라 메뉴를 {@link PotMember}에 함께 저장한다.
	 *
	 * <p>아래 검사들은 정상 요청에 친절한 에러를 주기 위한 선검사다. 동시 요청의 정합성은
	 * 두 번째 겹이 보장한다 — 정원은 {@code Pot.version} 낙관적 락({@code CONFLICT}),
	 * 중복 참여는 {@code pot_members} unique 제약({@link #saveMembership}이 번역).
	 *
	 * <p>닉네임은 채팅이 회원을 몰라도 되게 여기서 조회해 완성된 문구로 넘긴다.
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

		saveMembership(PotMember.join(potId, memberId, request.menuContent(), request.menuPrice(), now));
		pot.join();

		String nickname = memberService.getById(memberId).getNickname();
		chatService.addMember(pot.getChatRoomId(), memberId);
		chatService.postSystemNoticeMessage(pot.getChatRoomId(), nickname + "님이 들어왔어요");
		chatService.postSystemMenuMessage(pot.getChatRoomId(), memberId, request.menuContent(), request.menuPrice());

		return new PotJoinResponse(pot.getId(), pot.getChatRoomId(), pot.getCurrentMemberCount());
	}

	/**
	 * 참여 기록 저장. unique 제약 위반만 {@code POT_ALREADY_JOINED}로 번역한다 —
	 * 번역이 없으면 동시 중복 참여가 사용자에게 500으로 나간다.
	 *
	 * <p>{@code saveAndFlush}가 아니어도 되는 이유는 PK가 {@code IDENTITY}라 INSERT가 이 호출에서
	 * 바로 실행되기 때문이다. 예외를 잡은 뒤 DB 작업을 더 하지 않고 즉시 던지는 것이 중요하다 —
	 * 제약 위반이 나면 Hibernate 세션이 오염돼 이후 flush 동작을 보장할 수 없다.
	 */
	private void saveMembership(PotMember membership) {
		try {
			potMemberRepository.save(membership);
		} catch (DataIntegrityViolationException e) {
			if (isDuplicateMembership(e)) {
				throw new BusinessException(ErrorCode.POT_ALREADY_JOINED);
			}
			throw e;
		}
	}

	/**
	 * 중복 참여 제약 위반인지. 제약 이름으로 좁혀 판정한다 — 전부 409로 포장하면 NOT NULL 위반 같은
	 * 코드 버그가 조용히 묻힌다.
	 *
	 * <p>{@code contains}로 보는 이유는 DB마다 장식이 붙어서다 — h2는
	 * {@code PUBLIC.UK_... INDEX ...}, MySQL은 {@code pot_members.uk_...}로 돌려준다.
	 */
	private boolean isDuplicateMembership(DataIntegrityViolationException e) {
		Throwable cause = e.getCause();
		while (cause != null) {
			if (cause instanceof ConstraintViolationException violation) {
				String name = violation.getConstraintName();
				return name != null && name.toLowerCase().contains(PotMember.UK_POT_MEMBER);
			}
			cause = cause.getCause();
		}
		return false;
	}

	/**
	 * 팟 상세. 참여 전 첫 진입 화면과 채팅방 상단 헤더가 함께 쓴다.
	 * 나눔 완료된 팟도 조회된다 — 막으면 완료된 팟의 채팅방 헤더가 비어버린다.
	 */
	@Transactional(readOnly = true)
	public PotDetailResponse findDetail(Long memberId, Long potId) {
		return buildDetail(findPot(potId), memberId);
	}

	/**
	 * 채팅방 헤더/배너가 potId 없이 roomId만 갖고 있을 때 쓰는 역조회.
	 * 필드·정책은 {@link #findDetail}과 동일하다.
	 */
	@Transactional(readOnly = true)
	public PotDetailResponse findDetailByChatRoomId(Long memberId, Long chatRoomId) {
		Pot pot = potRepository.findByChatRoomId(chatRoomId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		return buildDetail(pot, memberId);
	}

	private PotDetailResponse buildDetail(Pot pot, Long memberId) {
		boolean isJoined = potMemberRepository.existsByPotIdAndMemberId(pot.getId(), memberId);
		Map<Long, List<PotMemberResponse>> members = loadMembers(List.of(pot));

		return PotDetailResponse.of(
			pot,
			memberService.getById(pot.getHostId()).getNickname(),
			potRepository.countByHostIdAndCountsAsHostExperienceTrue(pot.getHostId()),
			pot.isHost(memberId),
			isJoined,
			pot.isDeadlinePassed(OffsetDateTime.now(clock)),
			members.getOrDefault(pot.getId(), List.of())
		);
	}

	/**
	 * 팟 나가기. 채팅방의 "팟 나가기" 버튼이 이걸 부른다.
	 *
	 * <p>총대는 완료 전엔 나갈 수 없다 — 정산 계좌 주인이 없어지고 주문을 이어받을 방법이 없다.
	 * 완료 후에는 총대도 참여자와 동일하게 나갈 수 있다.
	 *
	 * <p>채팅방 멤버십도 함께 제거한다 — 나간 뒤에도 방에 남아 메시지를 보고 보낼 수 있으면 안 된다.
	 */
	@Transactional
	public void leave(Long memberId, Long potId) {
		Pot pot = findPot(potId);

		if (pot.isHost(memberId) && pot.isActive()) {
			throw new BusinessException(ErrorCode.POT_HOST_CANNOT_LEAVE);
		}
		if (potMemberRepository.deleteByPotIdAndMemberId(potId, memberId) == 0) {
			throw new BusinessException(ErrorCode.POT_NOT_JOINED);
		}

		String nickname = memberService.getById(memberId).getNickname();
		pot.leave();
		chatService.removeMember(pot.getChatRoomId(), memberId);
		chatService.postSystemNoticeMessage(pot.getChatRoomId(), nickname + "님이 채팅방을 나갔어요");
	}

	/**
	 * 나눔 완료. 참여자를 포함한 모두의 목록에서 사라지고 채팅방만 남는다.
	 * 마감시간 전이라도 누를 수 있다 — 정원이 차서 일찍 받아 나눈 경우가 정상 흐름이다.
	 */
	@Transactional
	public void complete(Long memberId, Long potId) {
		Pot pot = findPot(potId);
		requireHost(pot, memberId);

		if (!pot.isActive()) {
			throw new BusinessException(ErrorCode.POT_NOT_ACTIVE, "이미 나눔이 완료된 팟입니다.");
		}

		pot.complete();
		chatService.postSystemNoticeMessage(pot.getChatRoomId(), "배달팟의 나눔이 완료되었어요");
	}

	/** 마이페이지 "총대 N회" 배지. */
	@Transactional(readOnly = true)
	public long countHostedPots(Long memberId) {
		return potRepository.countByHostIdAndCountsAsHostExperienceTrue(memberId);
	}

	/** 회원 탈퇴 검증용 — 총대로 있는 살아있는 팟이 있으면 탈퇴를 막아야 한다. */
	@Transactional(readOnly = true)
	public boolean hasActiveHostedPot(Long memberId) {
		return potRepository.existsByHostIdAndStatus(memberId, PotStatus.ACTIVE);
	}

	/**
	 * 회원 탈퇴 시 참여 중인 팟에서 자동으로 나가기 처리한다.
	 *
	 * <p>호출 전 {@link #hasActiveHostedPot(Long)}로 총대인 ACTIVE 팟이 없음을 확인했다는 전제로
	 * 동작한다 — 그래서 {@link #leave(Long, Long)}를 재사용해도 {@code POT_HOST_CANNOT_LEAVE}에
	 * 걸리지 않는다.
	 */
	@Transactional
	public void leaveAllActivePots(Long memberId) {
		for (Long potId : potMemberRepository.findActivePotIdsByMemberId(memberId)) {
			leave(memberId, potId);
		}
	}

	/**
	 * 마감 후 {@link #AUTO_COMPLETE_HOURS}가 지나도록 방치된 팟을 일괄 {@code DONE}으로 전이시키고
	 * 각 채팅방에 완료 공지를 남긴다.
	 *
	 * <p>벌크 UPDATE는 어느 팟이 바뀌었는지 알 수 없으므로 UPDATE 직전에 같은 조건으로 채팅방 id를
	 * 먼저 조회한다. 그 사이에 동시 접속이 끼어들면 완료 공지가 중복될 수 있는데, 늦어도 손해가 없는
	 * 정리 작업이라 이 경합까지는 막지 않는다.
	 */
	private void completeAbandonedPots(OffsetDateTime now) {
		OffsetDateTime threshold = now.minusHours(AUTO_COMPLETE_HOURS);
		List<Long> abandonedChatRoomIds = potRepository.findChatRoomIdsAbandoned(threshold);
		potRepository.completeAbandoned(threshold);

		for (Long chatRoomId : abandonedChatRoomIds) {
			chatService.postSystemNoticeMessage(chatRoomId, "배달팟의 나눔이 완료되었어요");
		}
	}

	/** 전체 배달팟 섹션. 사각형으로 후보를 줄인 뒤 구면 거리로 모서리에 걸친 팟을 걸러낸다. */
	private List<Pot> findOthersNearby(Member me, Long memberId, String keyword, OffsetDateTime now) {
		Geo.Box box = Geo.boxAround(me.getLatitude(), me.getLongitude(), SEARCH_RADIUS_METERS);

		List<Pot> candidates = potRepository.findOpenPotsInBox(
			now, memberId,
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
	 * 팟마다 조회하면 목록 크기만큼(N+1), 참여자마다 회원을 조회하면 그 곱만큼 쿼리가 나간다.
	 */
	private Map<Long, List<PotMemberResponse>> loadMembers(List<Pot> pots) {
		if (pots.isEmpty()) {
			return Map.of();
		}

		Map<Long, Long> hostIdByPotId = pots.stream()
			.collect(Collectors.toMap(Pot::getId, Pot::getHostId));

		List<PotMember> potMembers = potMemberRepository.findByPotIdIn(hostIdByPotId.keySet());

		Map<Long, String> nicknameById = memberService
			.findAllByIds(potMembers.stream().map(PotMember::getMemberId).distinct().toList())
			.stream()
			.collect(Collectors.toMap(Member::getId, Member::getNickname));

		return potMembers.stream().collect(Collectors.groupingBy(
			PotMember::getPotId,
			Collectors.mapping(
				// 회원이 지워진 참여 기록은 닉네임이 없다. 목록 전체를 죽이지 않게 빈 문자열로 흘린다.
				pm -> new PotMemberResponse(
					pm.getMemberId(),
					nicknameById.getOrDefault(pm.getMemberId(), ""),
					pm.getMemberId().equals(hostIdByPotId.get(pm.getPotId()))
				),
				Collectors.toList()
			)
		));
	}

	private List<Pot> concatPots(List<Pot> hosted, List<Pot> joined, List<Pot> others) {
		List<Pot> all = new ArrayList<>();
		all.addAll(hosted);
		all.addAll(joined);
		all.addAll(others);
		return all;
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
	 * "지금부터 30분"은 Bean Validation으로 표현할 수 없어 주입받은 Clock으로 확인한다.
	 * {@link OffsetDateTime} 비교는 절대 시각 기준이라 서버 타임존과 무관하게 같은 결과가 나온다.
	 */
	private void validateDeadline(OffsetDateTime deadline) {
		OffsetDateTime earliest = OffsetDateTime.now(clock).plusMinutes(MIN_DEADLINE_MINUTES);
		if (deadline.isBefore(earliest)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
				"마감시간은 현재 시각으로부터 %d분 이후여야 합니다.".formatted(MIN_DEADLINE_MINUTES));
		}
	}
}
