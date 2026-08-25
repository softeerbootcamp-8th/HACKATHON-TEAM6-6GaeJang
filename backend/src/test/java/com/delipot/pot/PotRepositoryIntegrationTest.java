package com.delipot.pot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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
			.chatRoomId(1L)
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
}
