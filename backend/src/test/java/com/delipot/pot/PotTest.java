package com.delipot.pot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 엔티티 스스로 지키는 불변식.
 *
 * <p>사용자에게 보여줄 검증({@code POT_FULL} 등)은 {@link PotService}가 하고 그쪽에서 테스트한다.
 * 여기서 보는 것은 서비스가 그 검증을 빠뜨렸을 때 엔티티가 잘못된 상태로 넘어가지 않는지다 —
 * 그래서 기대 예외도 {@code BusinessException}이 아니라 {@code IllegalStateException}이다.
 */
class PotTest {

	private static final OffsetDateTime DEADLINE =
		OffsetDateTime.of(2026, 8, 25, 19, 0, 0, 0, ZoneOffset.ofHours(9));

	private Pot pot(int capacity) {
		Pot pot = Pot.builder()
			.hostId(1L)
			.title("역삼역 호백반점 같이 시켜요")
			.storeName("호백반점")
			.meetingPlace("역삼 스타빌 1층 로비")
			.latitude(new BigDecimal("37.5006000"))
			.longitude(new BigDecimal("127.0366000"))
			.capacity(capacity)
			.minOrderAmount(20000)
			.deadline(DEADLINE)
			.bankName("카카오뱅크")
			.accountNumber("3333-01-1234567")
			.accountHolder("김하나")
			.build();
		pot.linkChatRoom(10L);
		return pot;
	}

	@Test
	@DisplayName("정원이 찬 팟에는 참여시킬 수 없다 — 서비스가 POT_FULL 검사를 빠뜨려도 5/4가 되지 않는다")
	void joinCannotExceedCapacity() {
		Pot pot = pot(2);
		pot.join();

		assertThatThrownBy(pot::join)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("정원");
		assertThat(pot.getCurrentMemberCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("참여자 수는 0 아래로 내려가지 않는다")
	void leaveCannotGoBelowZero() {
		Pot pot = pot(4);
		pot.leave(); // 총대 1명 -> 0

		assertThatThrownBy(pot::leave).isInstanceOf(IllegalStateException.class);
		assertThat(pot.getCurrentMemberCount()).isZero();
	}

	@Test
	@DisplayName("살아 있는 팟에서 나가면 이탈 이력이 함께 남아 총대 경험치로 인정된다")
	void leaveRecordsMemberLeftWhileActive() {
		Pot pot = pot(4);
		pot.join();
		pot.leave();
		pot.complete();

		assertThat(pot.getCurrentMemberCount()).isEqualTo(1);
		assertThat(pot.isCountsAsHostExperience()).isTrue();
	}

	@Test
	@DisplayName("완료된 팟에서 나가는 것은 경험치 판정과 무관하다")
	void leaveAfterCompleteDoesNotAffectHostExperience() {
		Pot pot = pot(4);
		pot.complete();
		pot.leave();

		assertThat(pot.isCountsAsHostExperience()).isFalse();
	}

	@Test
	@DisplayName("모집 조건을 줄이는 방향으로는 바꿀 수 없다 — 이미 들어온 사람이 정원 밖으로 밀린다")
	void expandRecruitmentRejectsShrink() {
		Pot pot = pot(4);

		assertThatThrownBy(() -> pot.expandRecruitment(2, DEADLINE))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> pot.expandRecruitment(4, DEADLINE.minusMinutes(1)))
			.isInstanceOf(IllegalStateException.class);

		assertThat(pot.getCapacity()).isEqualTo(4);
		assertThat(pot.getDeadline()).isEqualTo(DEADLINE);
	}

	@Test
	@DisplayName("같은 값으로 다시 확장해도 문제없다 — 화면이 두 값을 함께 보내온다")
	void expandRecruitmentIsIdempotent() {
		Pot pot = pot(4);

		pot.expandRecruitment(4, DEADLINE);

		assertThat(pot.getCapacity()).isEqualTo(4);
	}
}
