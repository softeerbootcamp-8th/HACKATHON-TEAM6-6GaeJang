package com.delipot.pot;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
	 * <p>만날 장소를 방 생성 요청에 함께 실어 보낸다 — 채팅 도메인이 배달팟을 몰라도 헤더에
	 * 장소를 띄울 수 있게, 채팅 쪽이 자체 보유하는 필드에 값만 얹어주는 것이다.
	 *
	 * <p>의존 방향은 팟 → 채팅 단방향으로 유지한다. 채팅이 팟을 부르면 순환 참조가 되어
	 * 빈 생성 단계에서 실패한다. 채팅방에서 팟 정보가 필요하면 {@code Pot.chatRoomId}를
	 * 거꾸로 타는 조회({@code GET /api/pots/by-chat-room/{chatRoomId}})를 쓴다.
	 *
	 * <p>방을 만든 직후 총대가 입력한 가게 링크를 총대 명의 말풍선으로 바로 올린다. 참여자가
	 * 들어왔을 때 무슨 가게인지 스크롤을 올리지 않고도 바로 볼 수 있어야 해서다.
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
	 * 팟 내용 수정. 총대 본인이, 아직 참여자가 없고, 나눔이 끝나지 않은 팟만 고칠 수 있다.
	 *
	 * <p>참여자가 한 명이라도 들어오면 막는다({@link ErrorCode#POT_NOT_EDITABLE}). 이미 전달된
	 * 메뉴는 그 가게 기준이고, 참여자는 그 계좌·그 장소를 보고 들어왔다. 값을 갈아치우면
	 * 참여자에게는 아무 신호 없이 다른 팟이 되어 버린다. 대신 총대는 채팅으로 정리할 수 있다.
	 *
	 * <p>수정 폼을 열어 둔 사이 누군가 참여하는 경합은 {@code Pot.version} 낙관적 락이 막는다 —
	 * 참여 트랜잭션이 먼저 커밋되면 이 저장이 실패하고 {@code CONFLICT}로 응답된다.
	 * 여기 인원 검사만 두면 폼을 연 시점의 낡은 값으로 통과할 수 있다.
	 *
	 * <p>가게명·만날 장소가 바뀌면 연결된 채팅방의 이름·장소도 함께 맞춘다. 채팅방은 이 값들을
	 * 자체 컬럼으로 들고 있어(팟을 모른다) 여기서 밀어주지 않으면 옛 값이 그대로 남는다.
	 *
	 * <p>수정 결과를 돌려주지 않는 이유는, 상세를 만들려면 총대 닉네임·총대 횟수·참여자 목록까지
	 * 다시 긁어야 하는데 프론트는 저장 직후 목록/상세를 어차피 새로 조회하기 때문이다.
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
	 * <p>{@link #update}와 나눈 이유는 규칙이 반대여서다. 전체 수정은 참여자가 없을 때만 열리는
	 * 대신 무엇이든 바꿀 수 있고, 이쪽은 참여자가 있어도 열리는 대신 늘리는 방향만 허용한다.
	 * 늘리는 방향은 참여자에게 손해가 없다 — 자리가 더 생기고 시간이 더 생긴다.
	 *
	 * <p>마감이 이미 지난 팟도 늘릴 수 있다. 정원이 안 차서 마감만 지난 팟을 다시 살리는 것이
	 * 이 기능의 주 용도다. 상태가 {@code DONE}이면 막는다 — 끝난 팟을 되살리는 통로는 아니다.
	 *
	 * <p>값이 실제로 바뀐 경우에만 채팅방에 공지한다. 참여자는 이 두 값을 보고 들어왔으므로
	 * 조용히 바꾸면 안 되고, 반대로 같은 값으로 저장했는데 공지가 나가면 방이 시끄러워진다.
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
	 *
	 * <p>마감시간은 참여자의 시계 기준으로 읽혀야 하므로 KST 벽시계로 찍는다 — 저장된
	 * {@link OffsetDateTime}을 그대로 문자열화하면 요청이 보낸 오프셋이 그대로 노출된다.
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
	 *   <li>{@code hosted}/{@code joined} — 내가 속한 팟. 반경도 마감시간도 보지 않는다. 마감 후가
	 *       오히려 중요한 구간(주문·입금·수령)이고, 여기서 사라지는 조건은 나눔 완료뿐이다.</li>
	 *   <li>{@code all} — 300m 이내, 마감 전, 정원 여유 있고, 내가 속하지 않은 팟.
	 *       마감시간이 지나면 참여할 수 없으니 참여하지 않은 사람에게는 보일 이유가 없다.</li>
	 * </ul>
	 *
	 * <p>{@code readOnly}가 아닌 이유는 맨 앞에서 방치된 팟(마감 + 5시간)을 일괄 {@code DONE}으로
	 * 전이시키고, 각 채팅방에 나눔완료 공지를 남기기 때문이다. 이걸 안 하면 참여자 목록에 끝난 팟이
	 * 영구히 쌓이고, 참여자들은 방이 왜 조용해졌는지 알 방법이 없다.
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
	 *
	 * <p>참여는 곧 메뉴 전달이다. 입력한 메뉴는 {@link PotMember}에 함께 저장된다. 메뉴를 따로
	 * 보내는 API를 두지 않는 이유는 화면이 한 버튼("총대에게 메뉴 전달하기")으로 둘을 동시에 하기
	 * 때문이다 — 나누면 참여는 됐는데 메뉴는 없는 중간 상태가 생긴다.
	 *
	 * <p>채팅방 입장·공지 순서: 멤버십 추가 → 입장 공지 → 메뉴 공지. 닉네임은 채팅이 몰라도 되게
	 * 여기서 조회해 완성된 문구로 넘긴다({@code postSystemNoticeMessage} 계약).
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

		String nickname = memberService.getById(memberId).getNickname();
		chatService.addMember(pot.getChatRoomId(), memberId);
		chatService.postSystemNoticeMessage(pot.getChatRoomId(), nickname + "님이 들어왔어요");
		chatService.postSystemMenuMessage(pot.getChatRoomId(), memberId, request.menuContent(), request.menuPrice());

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
		return buildDetail(findPot(potId), memberId);
	}

	/**
	 * 채팅방 헤더/배너가 potId가 아니라 roomId만 갖고 있을 때 쓰는 역조회.
	 * {@code Pot.chatRoomId}는 단방향(팟 → 채팅)이라 채팅 쪽에는 이 관계가 없다 — 그래서
	 * 팟 쪽에 열어준다({@link Pot} 클래스 주석 참고). 그 외 필드·정책은 {@link #findDetail}과 동일하다.
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
	 * <p>총대는 완료 전엔 나갈 수 없다. 완료 전에 사라지면 정산 계좌 주인이 없어지고 남은 사람들이
	 * 주문을 이어받을 방법이 없다. 완료 전 총대에게는 대신 나눔 완료가 있다 — 완료 후에는 총대도
	 * 참여자와 동일하게 나갈 수 있다.
	 *
	 * <p>채팅방 멤버십도 함께 제거한다 — 나간 뒤에도 방에 남아 메시지를 보고 보낼 수 있으면 안 된다.
	 * 채팅방에는 "~님이 채팅방을 나갔어요" 안내를 남긴다.
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
		pot.recordMemberLeft();
		pot.decreaseMemberCount();
		chatService.removeMember(pot.getChatRoomId(), memberId);
		chatService.postSystemNoticeMessage(pot.getChatRoomId(), nickname + "님이 채팅방을 나갔어요");
	}

	/**
	 * 나눔 완료. 총대가 배달을 받아 나누는 것까지 끝냈다는 뜻이고, 참여자를 포함한 모두의 목록에서
	 * 사라진다. 채팅방은 남으므로 하단 채팅 탭에서 계속 볼 수 있다.
	 *
	 * <p>마감시간 전이라도 누를 수 있다. 정원이 다 차서 일찍 주문하고 받아 나눈 경우가 정상 흐름이고,
	 * 그때 마감시간까지 기다리게 하면 끝난 팟이 전체 목록에 계속 떠 있게 된다.
	 *
	 * <p>완료 공지를 채팅방에 남긴다({@code postSystemNoticeMessage} 재사용 — 문구만 다른
	 * 센터 정렬 시스템 안내라 별도 타입을 만들지 않는다).
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
	 * <p>호출 전 {@link #hasActiveHostedPot(Long)}로 총대인 ACTIVE 팟이 없음을 이미 확인했다는
	 * 전제 하에 동작한다 — 그래서 여기서 찾은 ACTIVE 팟은 전부 참여자로만 속한 팟이고, 기존
	 * {@link #leave(Long, Long)}를 그대로 재사용해도 {@code POT_HOST_CANNOT_LEAVE}에 걸리지 않는다.
	 */
	@Transactional
	public void leaveAllActivePots(Long memberId) {
		for (Long potId : potMemberRepository.findActivePotIdsByMemberId(memberId)) {
			leave(memberId, potId);
		}
	}

	/**
	 * 마감 후 {@link #AUTO_COMPLETE_HOURS}가 지나도록 총대가 나눔완료를 누르지 않은 팟을 일괄
	 * {@code DONE}으로 전이시키고, 각 채팅방에 {@link #complete}와 같은 완료 공지를 남긴다.
	 *
	 * <p>벌크 UPDATE는 엔티티를 거치지 않아 어느 팟이 바뀌었는지 알 수 없으므로, UPDATE 직전에
	 * 같은 조건으로 채팅방 id만 먼저 조회해 둔다. 두 조회·갱신 사이에 극히 드물게 동시 접속이
	 * 끼어들면 완료 공지가 중복 발송될 수 있지만, 자동완료 자체가 늦어도 손해가 없는 정리
	 * 작업이라 이 경합까지 막을 정도는 아니라고 본다({@link PotRepository#completeAbandoned} 설계와 같은 결).
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
	 * {@code @Future}가 과거 시각은 걸러 주지만, "지금부터 30분"이라는 도메인 규칙은
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
