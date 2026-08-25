package com.delipot.pot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import com.delipot.global.error.BusinessException;
import com.delipot.global.error.ErrorCode;
import com.delipot.pot.dto.PotCreateRequest;
import com.delipot.pot.dto.PotCreateResponse;

@ExtendWith(MockitoExtension.class)
class PotServiceTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	/** 고정 현재 시각: 2026-08-25 18:00 KST */
	private static final Instant NOW = Instant.parse("2026-08-25T09:00:00Z");
	private static final OffsetDateTime CURRENT = NOW.atZone(SEOUL).toOffsetDateTime();

	@Mock
	private PotRepository potRepository;

	private PotService potService() {
		return new PotService(potRepository, Clock.fixed(NOW, SEOUL));
	}

	private void givenSaveEchoes() {
		given(potRepository.save(any(Pot.class))).willAnswer(invocation -> invocation.getArgument(0));
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
			1L,
			"역삼역 호백반점 같이 시켜요",
			"호백반점",
			"https://web.coupangeats.com/share?storeId=781313",
			"역삼 스타빌 1층 로비",
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
	@DisplayName("팟을 생성하면 RECRUITING 상태로 총대 본인이 첫 참여자가 된다")
	void createSetsInitialState() {
		givenSaveEchoes();

		PotCreateResponse response = potService().create(request(CURRENT.plusHours(1)));

		Pot saved = capturedPot();
		assertThat(saved.getStatus()).isEqualTo(PotStatus.RECRUITING);
		assertThat(saved.getCurrentMemberCount()).isEqualTo(1);
		assertThat(saved.getHostId()).isEqualTo(1L);
		assertThat(saved.getStoreName()).isEqualTo("호백반점");
		assertThat(saved.getCapacity()).isEqualTo(4);
		assertThat(response.status()).isEqualTo(PotStatus.RECRUITING);
		assertThat(response.currentMemberCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("마감시간이 10분 이내로 촉박하면 INVALID_INPUT으로 거부한다")
	void rejectsTooSoonDeadline() {
		assertThatThrownBy(() -> potService().create(request(CURRENT.plusMinutes(9))))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(ErrorCode.INVALID_INPUT);

		verify(potRepository, never()).save(any());
	}

	@Test
	@DisplayName("마감시간이 정확히 10분 뒤면 경계값으로 허용한다")
	void allowsExactlyMinimumDeadline() {
		givenSaveEchoes();

		potService().create(request(CURRENT.plusMinutes(10)));

		verify(potRepository).save(any(Pot.class));
	}

	@Test
	@DisplayName("과거 마감시간도 서비스 계층에서 거부한다 — @Valid를 우회한 호출 대비")
	void rejectsPastDeadline() {
		assertThatThrownBy(() -> potService().create(request(CURRENT.minusHours(1))))
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
		PotService utcServer = new PotService(potRepository, Clock.fixed(NOW, ZoneOffset.UTC));

		// KST 벽시계로는 17:30 — 고정 현재 시각(18:00 KST)보다 30분 전이다.
		OffsetDateTime pastInKst = NOW.atZone(SEOUL).toOffsetDateTime().minusMinutes(30);

		assertThatThrownBy(() -> utcServer.create(request(pastInKst)))
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

		potService().create(request(asKst));
		potService().create(request(asUtc));

		verify(potRepository, org.mockito.Mockito.times(2)).save(any(Pot.class));
	}

	@Test
	@DisplayName("생성 직후 팟은 정원이 남아 있고 마감 전이다")
	void newPotIsOpen() {
		givenSaveEchoes();

		potService().create(request(CURRENT.plusHours(1)));

		Pot saved = capturedPot();
		assertThat(saved.isFull()).isFalse();
		assertThat(saved.isDeadlinePassed(CURRENT)).isFalse();
		assertThat(saved.isDeadlinePassed(CURRENT.plusHours(2))).isTrue();
	}

	@Test
	@DisplayName("정원 2명인 팟은 총대 1명만으로는 아직 정원이 차지 않는다")
	void capacityTwoIsNotFullWithHostOnly() {
		givenSaveEchoes();

		potService().create(request(CURRENT.plusHours(1), 2));

		assertThat(capturedPot().isFull()).isFalse();
	}
}
