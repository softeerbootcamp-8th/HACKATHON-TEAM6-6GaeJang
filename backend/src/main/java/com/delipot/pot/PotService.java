package com.delipot.pot;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.delipot.global.error.BusinessException;
import com.delipot.global.error.ErrorCode;
import com.delipot.pot.dto.PotCreateRequest;
import com.delipot.pot.dto.PotCreateResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PotService {

	/** 마감시간이 너무 촉박하면 아무도 참여하지 못한다. 최소 여유를 도메인 규칙으로 둔다. */
	private static final int MIN_DEADLINE_MINUTES = 10;

	private final PotRepository potRepository;
	private final Clock clock;

	@Transactional
	public PotCreateResponse create(PotCreateRequest request) {
		validateDeadline(request.deadline());

		Pot pot = Pot.builder()
			.hostId(request.hostId())
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
