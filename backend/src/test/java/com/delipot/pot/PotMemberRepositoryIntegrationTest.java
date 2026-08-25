package com.delipot.pot;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 회원 탈퇴 시 자동 나가기 대상을 찾는 {@code findActivePotIdsByMemberId}가 실제 DB에서
 * status 조건을 정확히 반영하는지 확인한다. 서비스 단위 테스트는 이 쿼리를 목으로 대체하므로
 * JPQL 자체가 틀려도 잡히지 않는다 — 여기서 실제로 실행해본다.
 */
@DataJpaTest
@ActiveProfiles("h2")
class PotMemberRepositoryIntegrationTest {

	private static final Long MEMBER_ID = 1L;

	@Autowired
	private PotRepository potRepository;

	@Autowired
	private PotMemberRepository potMemberRepository;

	private Pot pot(PotStatus status) {
		Pot pot = potRepository.save(Pot.builder()
			.hostId(999L)
			.title("역삼역 호백반점 같이 시켜요")
			.storeName("호백반점")
			.storeUrl("https://web.coupangeats.com/share?storeId=781313")
			.meetingPlace("역삼 스타빌 1층 로비")
			.latitude(new BigDecimal("37.5006000"))
			.longitude(new BigDecimal("127.0366000"))
			.capacity(4)
			.minOrderAmount(20000)
			.deadline(OffsetDateTime.of(2026, 8, 25, 19, 30, 0, 0, ZoneOffset.ofHours(9)))
			.bankName("카카오뱅크")
			.accountNumber("3333-01-1234567")
			.accountHolder("김하나")
			.build());
		if (status == PotStatus.DONE) {
			pot.complete();
			potRepository.saveAndFlush(pot);
		}
		return pot;
	}

	@Test
	@DisplayName("ACTIVE 팟만 반환하고, 나눔 완료(DONE)된 팟은 제외한다")
	void returnsOnlyActivePots() {
		Pot activePot = pot(PotStatus.ACTIVE);
		Pot donePot = pot(PotStatus.DONE);
		potMemberRepository.save(PotMember.join(activePot.getId(), MEMBER_ID, "후라이드", 18000, OffsetDateTime.now()));
		potMemberRepository.save(PotMember.join(donePot.getId(), MEMBER_ID, "양념", 18000, OffsetDateTime.now()));

		assertThat(potMemberRepository.findActivePotIdsByMemberId(MEMBER_ID))
			.containsExactly(activePot.getId());
	}

	@Test
	@DisplayName("참여 기록이 없으면 빈 목록을 반환한다")
	void returnsEmptyWhenNotJoinedAnything() {
		assertThat(potMemberRepository.findActivePotIdsByMemberId(MEMBER_ID)).isEmpty();
	}
}
