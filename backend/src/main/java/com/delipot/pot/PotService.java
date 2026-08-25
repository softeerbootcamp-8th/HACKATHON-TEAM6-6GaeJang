package com.delipot.pot;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.delipot.global.error.BusinessException;
import com.delipot.global.error.ErrorCode;
import com.delipot.pot.dto.PotCreateRequest;
import com.delipot.pot.dto.PotCreateResponse;
import com.delipot.pot.dto.PotListRequest;
import com.delipot.pot.dto.PotListResponse;
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
	 * 한 번에 내려줄 팟 수의 상한. 300m 반경이면 보통 수십 건이지만, 밀집 지역에서 폭증했을 때
	 * TEXT 설명까지 전부 직렬화되는 것을 막는다. 페이징이 붙으면 페이지 크기로 대체된다.
	 */
	private static final Limit MAX_RESULTS = Limit.of(100);

	private final PotRepository potRepository;
	private final Clock clock;

	@Transactional
	public PotCreateResponse create(Long hostId, PotCreateRequest request) {
		validateDeadline(request.deadline());

		Pot pot = Pot.builder()
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
			.build();

		return PotCreateResponse.from(potRepository.save(pot));
	}

	/**
	 * 내 인증 주소 기준 300m 이내에서 참여 가능한 팟을 마감 임박순으로 준다.
	 *
	 * <p>정원이 찬 팟은 목록에서 뺀다 — 참여할 수 없는 카드이고, 참여자는 "참여중인 배달팟"
	 * 섹션에서 따로 본다. 이 조건은 쿼리에 있다.
	 *
	 * <p>사각형으로 후보를 줄인 뒤 구면 거리로 한 번 더 거른다. 사각형만 쓰면 모서리 때문에
	 * 300m 요청에 최대 424m가 섞인다.
	 */
	@Transactional(readOnly = true)
	public PotListResponse findNearby(PotListRequest request) {
		Geo.Box box = Geo.boxAround(request.latitude(), request.longitude(), SEARCH_RADIUS_METERS);

		List<Pot> candidates = potRepository.findOpenPotsInBox(
			PotStatus.RECRUITING,
			OffsetDateTime.now(clock),
			box.minLatitude(), box.maxLatitude(),
			box.minLongitude(), box.maxLongitude(),
			request.searchKeyword(),
			MAX_RESULTS
		);

		// 쿼리가 이미 마감 임박순으로 정렬해 두었고 stream 은 순서를 보존하므로 여기서 다시 정렬하지 않는다.
		List<PotSummaryResponse> pots = candidates.stream()
			.filter(pot -> isWithinRadius(request, pot))
			.map(PotSummaryResponse::from)
			.toList();

		return new PotListResponse(pots);
	}

	/** 사각형을 통과한 후보 중 실제 반경 밖(모서리에 걸친 팟)을 걸러낸다. */
	private boolean isWithinRadius(PotListRequest request, Pot pot) {
		double distance = Geo.distanceMeters(
			request.latitude(), request.longitude(), pot.getLatitude(), pot.getLongitude());
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
