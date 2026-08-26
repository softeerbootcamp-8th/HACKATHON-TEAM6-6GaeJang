package com.delipot.pot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;

/**
 * 엔티티 매핑이 실제 스키마로 떨어지는지 확인한다. prod 프로파일이 {@code ddl-auto: validate}라서
 * 매핑이 어긋나면 배포 시점에야 터진다 — 여기서 미리 잡는다.
 */
@DataJpaTest
@ActiveProfiles("h2")
class PotRepositoryIntegrationTest {

	@Autowired
	private PotRepository potRepository;

	@Autowired
	private EntityManager entityManager;

	private Pot.PotBuilder validPot() {
		return Pot.builder()
			.hostId(1L)
			.title("역삼역 호백반점 같이 시켜요")
			.description("짜장면 먹고 싶은데 최소주문금액이 안 채워져요")
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
			.accountHolder("김하나");
	}

	@Test
	@DisplayName("팟을 저장하면 ID·생성시각·낙관적 락 버전이 채워진다")
	void savePersistsAuditFields() {
		Pot saved = potRepository.saveAndFlush(validPot().build());
		entityManager.clear();

		Pot found = potRepository.findById(saved.getId()).orElseThrow();

		assertThat(found.getId()).isNotNull();
		assertThat(found.getCreatedAt()).isNotNull();
		assertThat(found.getVersion()).isZero();
		assertThat(found.getStatus()).isEqualTo(PotStatus.ACTIVE);
		assertThat(found.getCurrentMemberCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("위경도 소수점 7자리가 잘리지 않고 왕복한다 — 300m 반경 판정의 전제")
	void coordinatePrecisionSurvivesRoundTrip() {
		Pot saved = potRepository.saveAndFlush(validPot()
			.latitude(new BigDecimal("37.5006123"))
			.longitude(new BigDecimal("127.0366789"))
			.build());
		entityManager.clear();

		Pot found = potRepository.findById(saved.getId()).orElseThrow();

		assertThat(found.getLatitude()).isEqualByComparingTo("37.5006123");
		assertThat(found.getLongitude()).isEqualByComparingTo("127.0366789");
	}

	@Test
	@DisplayName("정산 계좌가 비면 NOT NULL 제약으로 저장이 실패한다")
	void accountIsRequiredAtSchemaLevel() {
		Pot invalid = validPot().accountNumber(null).build();

		assertThatThrownBy(() -> potRepository.saveAndFlush(invalid))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("status는 문자열로 저장된다 — 프론트 계약이 enum 이름이므로 ordinal이면 안 된다")
	void statusIsStoredAsString() {
		Pot saved = potRepository.saveAndFlush(validPot().build());
		entityManager.flush();

		Object status = entityManager
			.createNativeQuery("select status from pots where id = :id")
			.setParameter("id", saved.getId())
			.getSingleResult();

		assertThat(status).hasToString("ACTIVE");
	}

	@Test
	@DisplayName("existsByHostIdAndStatus: 총대인 ACTIVE 팟이 있으면 true, DONE으로 완료하면 false")
	void existsByHostIdAndStatusReflectsCompletion() {
		Pot pot = potRepository.saveAndFlush(validPot().hostId(99L).build());

		assertThat(potRepository.existsByHostIdAndStatus(99L, PotStatus.ACTIVE)).isTrue();

		pot.complete();
		potRepository.saveAndFlush(pot);

		assertThat(potRepository.existsByHostIdAndStatus(99L, PotStatus.ACTIVE)).isFalse();
	}

	@Test
	@DisplayName("countByHostIdAndCountsAsHostExperienceTrue: 참여자가 한 번이라도 있었던 완료 팟만 센다")
	void countByHostIdAndCountsAsHostExperienceTrueCountsPotsThatEverHadParticipant() {
		Long hostId = 100L;

		Pot soloComplete = validPot().hostId(hostId).build(); // 참여자 없이 혼자 완료 — 경험치 아님
		soloComplete.complete();

		Pot leftBeforeComplete = validPot().hostId(hostId).build();
		leftBeforeComplete.join();
		leftBeforeComplete.leave(); // ACTIVE일 때 이탈이므로 hasMemberLeft 기록
		leftBeforeComplete.complete();

		Pot qualifying = validPot().hostId(hostId).build();
		qualifying.join();
		qualifying.complete();

		// 아직 ACTIVE라 완료 자체가 안 된 팟 — 카운트에서 당연히 빠져야 한다.
		Pot stillActive = validPot().hostId(hostId).build();
		stillActive.join();

		potRepository.saveAll(List.of(soloComplete, leftBeforeComplete, qualifying, stillActive));
		potRepository.flush();

		assertThat(potRepository.countByHostIdAndCountsAsHostExperienceTrue(hostId)).isEqualTo(2);
	}

	@Test
	@DisplayName("completeAbandoned: 방치된 ACTIVE 팟을 DONE으로 전이시키며 총대 경험치 여부도 함께 확정한다")
	void completeAbandonedAlsoFreezesHostExperienceFlag() {
		OffsetDateTime deadline = OffsetDateTime.of(2026, 8, 25, 12, 0, 0, 0, ZoneOffset.ofHours(9));

		Pot abandonedWithParticipant = validPot().hostId(101L).deadline(deadline).build();
		abandonedWithParticipant.join();
		Long withParticipantId = potRepository.saveAndFlush(abandonedWithParticipant).getId();

		Pot abandonedAfterParticipantLeft = validPot().hostId(103L).deadline(deadline).build();
		abandonedAfterParticipantLeft.join();
		abandonedAfterParticipantLeft.leave();
		Long afterParticipantLeftId = potRepository.saveAndFlush(abandonedAfterParticipantLeft).getId();

		Long soloId = potRepository.saveAndFlush(validPot().hostId(102L).deadline(deadline).build()).getId();

		entityManager.clear();

		int updated = potRepository.completeAbandoned(deadline.plusHours(5));
		entityManager.clear();

		assertThat(updated).isEqualTo(3);
		assertThat(potRepository.findById(withParticipantId).orElseThrow().getStatus()).isEqualTo(PotStatus.DONE);
		assertThat(potRepository.findById(afterParticipantLeftId).orElseThrow().getStatus()).isEqualTo(PotStatus.DONE);
		assertThat(potRepository.findById(soloId).orElseThrow().getStatus()).isEqualTo(PotStatus.DONE);
		assertThat(potRepository.countByHostIdAndCountsAsHostExperienceTrue(101L)).isEqualTo(1);
		assertThat(potRepository.countByHostIdAndCountsAsHostExperienceTrue(103L)).isEqualTo(1);
		assertThat(potRepository.countByHostIdAndCountsAsHostExperienceTrue(102L)).isEqualTo(0);
	}
}
