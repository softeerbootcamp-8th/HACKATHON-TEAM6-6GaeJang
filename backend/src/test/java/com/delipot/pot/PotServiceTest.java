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
import java.time.LocalDateTime;
import java.time.ZoneId;

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
	/** 고정 현재 시각: 2026-08-25 18:00 (KST) */
	private static final Instant NOW = Instant.parse("2026-08-25T09:00:00Z");
	private static final LocalDateTime CURRENT = LocalDateTime.ofInstant(NOW, SEOUL);

	@Mock
	private PotRepository potRepository;

	private final Clock clock = Clock.fixed(NOW, SEOUL);

	private PotService potService() {
		return new PotService(potRepository, clock);
	}

	private PotCreateRequest request(LocalDateTime deadline) {
		return new PotCreateRequest(
			1L,
			"역삼역 호백반점 같이 시켜요",
			"호백반점",
			"https://web.coupangeats.com/share?storeId=781313",
			"역삼 스타빌 1층 로비",
			new BigDecimal("37.5006000"),
			new BigDecimal("127.0366000"),
			4,
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
		given(potRepository.save(any(Pot.class))).willAnswer(invocation -> invocation.getArgument(0));

		PotCreateResponse response = potService().create(request(CURRENT.plusHours(1)));

		ArgumentCaptor<Pot> captor = ArgumentCaptor.forClass(Pot.class);
		verify(potRepository).save(captor.capture());
		Pot saved = captor.getValue();

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
		given(potRepository.save(any(Pot.class))).willAnswer(invocation -> invocation.getArgument(0));

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

	@Test
	@DisplayName("생성 직후 팟은 정원이 남아 있고 마감 전이다")
	void newPotIsOpen() {
		given(potRepository.save(any(Pot.class))).willAnswer(invocation -> invocation.getArgument(0));

		potService().create(request(CURRENT.plusHours(1)));

		ArgumentCaptor<Pot> captor = ArgumentCaptor.forClass(Pot.class);
		verify(potRepository).save(captor.capture());
		Pot saved = captor.getValue();

		assertThat(saved.isFull()).isFalse();
		assertThat(saved.isDeadlinePassed(CURRENT)).isFalse();
		assertThat(saved.isDeadlinePassed(CURRENT.plusHours(2))).isTrue();
	}

	@Test
	@DisplayName("정원 2명인 팟은 총대 1명만으로는 아직 정원이 차지 않는다")
	void capacityTwoIsNotFullWithHostOnly() {
		given(potRepository.save(any(Pot.class))).willAnswer(invocation -> invocation.getArgument(0));

		PotCreateRequest base = request(CURRENT.plusHours(1));
		PotCreateRequest twoSeats = new PotCreateRequest(
			base.hostId(), base.title(), base.storeName(), base.storeUrl(), base.meetingPlace(),
			base.latitude(), base.longitude(), 2, base.minOrderAmount(), base.deadline(),
			base.description(), base.bankName(), base.accountNumber(), base.accountHolder()
		);

		potService().create(twoSeats);

		ArgumentCaptor<Pot> captor = ArgumentCaptor.forClass(Pot.class);
		verify(potRepository).save(captor.capture());
		assertThat(captor.getValue().isFull()).isFalse();
	}
}
