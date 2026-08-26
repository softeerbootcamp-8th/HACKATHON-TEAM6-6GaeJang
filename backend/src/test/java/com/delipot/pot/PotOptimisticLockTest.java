package com.delipot.pot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.hibernate.StaleStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;

/**
 * {@code Pot.version} 낙관적 락이 실제 DB에서 갱신 손실(lost update)을 막는지 검증한다.
 *
 * <p>스레드를 쓰지 않는다. 스레드 + latch 방식은 경합이 실제로 일어났는지를 보장할 수 없어서다 —
 * 스케줄러가 A를 커밋까지 다 돌린 뒤 B를 시작하면 B는 {@code isFull()} 선검사에서 걸러지고,
 * 낙관적 락은 한 번도 실행되지 않은 채 테스트가 통과한다. 락을 지워도 초록불이 나오는 테스트는
 * 증명력이 없다.
 *
 * <p>대신 {@link EntityManager} 두 개를 테스트 스레드에서 직접 순서대로 몰아 경합을 100% 재현한다.
 * 두 트랜잭션이 모두 낡은 version을 읽은 뒤 차례로 커밋하게 만들면, 락이 없으면 반드시 실패한다.
 *
 * <p>{@code @DataJpaTest}로는 할 수 없다 — 테스트 전체를 한 트랜잭션으로 감싸고 롤백하므로
 * 커밋이 일어나지 않고, 커밋 시점에 터지는 version 충돌을 재현할 수 없다.
 */
@SpringBootTest
@ActiveProfiles("h2")
class PotOptimisticLockTest {

	@Autowired
	private EntityManagerFactory emf;

	@Autowired
	private PotRepository potRepository;

	@Autowired
	private PotMemberRepository potMemberRepository;

	private TransactionTemplate tx;

	@Autowired
	void setTransactionManager(PlatformTransactionManager transactionManager) {
		this.tx = new TransactionTemplate(transactionManager);
	}

	/** 커밋된 팟을 하나 만든다. 경합을 재현하려면 실제로 커밋돼 있어야 한다. */
	private Long givenCommittedPot(int capacity) {
		return tx.execute(status -> potRepository.save(Pot.builder()
			.hostId(1L)
			.title("역삼역 호백반점 같이 시켜요")
			.storeName("호백반점")
			.storeUrl("https://web.coupangeats.com/share?storeId=781313")
			.meetingPlace("역삼 스타빌 1층 로비")
			.latitude(new BigDecimal("37.5006000"))
			.longitude(new BigDecimal("127.0366000"))
			.capacity(capacity)
			.minOrderAmount(20000)
			.deadline(OffsetDateTime.now().plusHours(2))
			.bankName("카카오뱅크")
			.accountNumber("3333-01-1234567")
			.accountHolder("김하나")
			.build()).getId());
	}

	@Test
	@DisplayName("마지막 한 자리를 두 트랜잭션이 동시에 노리면 늦게 커밋한 쪽이 version 충돌로 실패한다")
	void lastSeatRaceLosesOnVersionConflict() {
		Long potId = givenCommittedPot(2); // 총대 1명 + 남은 자리 1

		EntityManager em1 = emf.createEntityManager();
		EntityManager em2 = emf.createEntityManager();
		try {
			// 두 트랜잭션이 모두 커밋 전에 같은 상태(1/2)를 읽는다 — 이게 경합의 전제다.
			em1.getTransaction().begin();
			Pot seenByFirst = em1.find(Pot.class, potId);
			em2.getTransaction().begin();
			Pot seenBySecond = em2.find(Pot.class, potId);

			assertThat(seenByFirst.isFull()).isFalse();
			assertThat(seenBySecond.isFull()).isFalse(); // 둘 다 "자리가 있다"고 판단한다

			seenByFirst.join();
			em1.getTransaction().commit(); // version 0 -> 1

			seenBySecond.join();
			assertThatThrownBy(() -> em2.getTransaction().commit())
				.isInstanceOf(RollbackException.class)
				.hasCauseInstanceOf(OptimisticLockException.class)
				// UPDATE 의 where 절에 version 이 붙어 0행이 매칭된 결과다 — 이게 갱신 손실을 막는 실체다.
				.hasRootCauseInstanceOf(StaleStateException.class);
		} finally {
			close(em1);
			close(em2);
		}

		// 락이 없으면 두 번째 커밋이 통과해 2/2가 아니라 갱신 손실로 인원이 어긋난다.
		int finalCount = tx.execute(status -> potRepository.findById(potId).orElseThrow().getCurrentMemberCount());
		assertThat(finalCount).isEqualTo(2);
	}

	/**
	 * 참여와 나가기가 겹치는 경우. 두 연산 모두 {@code Pot}을 건드리므로 같은 락에 걸린다.
	 *
	 * <p>여기서 중요한 것은 인원 수 자체가 아니라, 실패한 쪽의 {@code pot_members} 행 변경까지
	 * 같이 롤백되어 {@code currentMemberCount}와 실제 행 수가 어긋나지 않는다는 점이다.
	 * 두 값을 한 트랜잭션에서 같이 움직이는 설계가 이걸 보장한다.
	 */
	@Test
	@DisplayName("참여와 나가기가 겹치면 늦게 커밋한 쪽이 실패하고, 인원 수와 참여 행 수가 어긋나지 않는다")
	void joinAndLeaveRaceKeepsCountAndRowsConsistent() {
		Long potId = givenCommittedPot(4);
		Long leavingMemberId = 77L;

		// 나갈 사람이 이미 참여해 있는 상태로 만든다 — 총대 + 참여자 1명.
		tx.executeWithoutResult(status -> {
			potMemberRepository.save(PotMember.host(potId, 1L, OffsetDateTime.now()));
			potMemberRepository.save(PotMember.join(potId, leavingMemberId, "후라이드", 18000, OffsetDateTime.now()));
			potRepository.findById(potId).orElseThrow().join();
		});

		EntityManager em1 = emf.createEntityManager();
		EntityManager em2 = emf.createEntityManager();
		try {
			em1.getTransaction().begin();
			Pot seenByJoiner = em1.find(Pot.class, potId);
			em2.getTransaction().begin();
			Pot seenByLeaver = em2.find(Pot.class, potId);

			// 새 참여자가 들어온다 — 행 추가 + 인원 증가를 한 트랜잭션에서.
			seenByJoiner.join();
			em1.persist(PotMember.join(potId, 88L, "양념", 18000, OffsetDateTime.now()));
			em1.getTransaction().commit();

			// 기존 참여자가 나간다 — 행 삭제 + 인원 감소. 낡은 version이라 실패한다.
			seenByLeaver.leave();
			em2.createQuery("delete from PotMember pm where pm.potId = :potId and pm.memberId = :memberId")
				.setParameter("potId", potId)
				.setParameter("memberId", leavingMemberId)
				.executeUpdate();
			assertThatThrownBy(() -> em2.getTransaction().commit())
				.isInstanceOf(RollbackException.class)
				.hasCauseInstanceOf(OptimisticLockException.class)
				// UPDATE 의 where 절에 version 이 붙어 0행이 매칭된 결과다 — 이게 갱신 손실을 막는 실체다.
				.hasRootCauseInstanceOf(StaleStateException.class);
		} finally {
			close(em1);
			close(em2);
		}

		tx.executeWithoutResult(status -> {
			int count = potRepository.findById(potId).orElseThrow().getCurrentMemberCount();
			long rows = potMemberRepository.findByPotIdIn(java.util.List.of(potId)).size();
			assertThat(count).isEqualTo(3);  // 총대 + 기존 참여자 + 새 참여자
			assertThat(rows).isEqualTo(count); // 실패한 나가기의 행 삭제도 함께 롤백됐다
		});
	}

	private void close(EntityManager em) {
		if (em.getTransaction().isActive()) {
			em.getTransaction().rollback();
		}
		em.close();
	}
}
