package com.delipot.pot;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import com.delipot.global.error.BusinessException;
import com.delipot.global.error.ErrorCode;
import com.delipot.member.Member;
import com.delipot.member.MemberRepository;
import com.delipot.pot.dto.PotCreateRequest;
import com.delipot.pot.dto.PotJoinRequest;

/**
 * 실제 서비스 경로({@link PotService#join})를 스레드 두 개로 동시에 호출한다.
 *
 * <p>이 테스트가 검증하는 것은 "락이 발동했는지"가 아니라 <b>어떤 순서로 실행돼도 불변식이
 * 깨지지 않는지</b>다. 스레드 스케줄링은 제어할 수 없어, 두 요청이 겹칠 때도 있고 한쪽이 먼저
 * 완주할 때도 있다. 어느 쪽이든 아래 세 가지가 성립해야 한다.
 *
 * <ul>
 *   <li>성공은 정확히 한 건</li>
 *   <li>{@code Pot.currentMemberCount}가 정원을 넘지 않음</li>
 *   <li>{@code pot_members} 행 수와 {@code currentMemberCount}가 일치</li>
 * </ul>
 *
 * <p>경합 발생 자체를 보장하지 않으므로 락의 증명은 {@link PotOptimisticLockTest}가 담당한다.
 * 이쪽은 실패해도 flaky가 아니라 실제 정합성 깨짐을 뜻한다 — 두 테스트의 역할이 다르다.
 *
 * <p>데이터를 커밋하므로 테스트 간 격리를 트랜잭션 롤백에 의존할 수 없다. 대신 매 테스트가
 * 자기 팟·자기 회원을 새로 만들고, 검증도 그 팟 범위로만 한다.
 */
@SpringBootTest
@ActiveProfiles("h2")
class PotJoinConcurrencyIntegrationTest {

	private static final AtomicInteger SEQ = new AtomicInteger();

	@Autowired
	private PotService potService;

	@Autowired
	private PotRepository potRepository;

	@Autowired
	private PotMemberRepository potMemberRepository;

	@Autowired
	private MemberRepository memberRepository;

	private Long givenMember() {
		int n = SEQ.incrementAndGet();
		return memberRepository.save(Member.register(
			"010%08d".formatted(n), "hash", "동시성%03d".formatted(n), "서울시 강남구")).getId();
	}

	private Long givenPot(Long hostId, int capacity) {
		return potService.create(hostId, new PotCreateRequest(
			"역삼역 호백반점 같이 시켜요", "호백반점", "https://web.coupangeats.com/share?storeId=781313",
			"역삼 스타빌 1층 로비", null, null,
			new BigDecimal("37.5006000"), new BigDecimal("127.0366000"),
			capacity, 20000, OffsetDateTime.now().plusHours(2),
			null, "카카오뱅크", "3333-01-1234567", "김하나")).potId();
	}

	/** 두 작업을 배리어로 정렬해 동시에 출발시키고, 각각의 결과(성공 또는 예외)를 돌려준다. */
	private List<Throwable> runConcurrently(Runnable first, Runnable second) {
		CyclicBarrier barrier = new CyclicBarrier(2);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Future<Throwable>> futures = pool.invokeAll(List.of(
				attempt(barrier, first), attempt(barrier, second)));
			return futures.stream().map(f -> {
				try {
					return f.get();
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}).toList();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		} finally {
			pool.shutdownNow();
		}
	}

	/** 성공하면 null, 실패하면 그 예외를 돌려준다 — 두 스레드의 결과를 같은 방식으로 모으기 위해서다. */
	private Callable<Throwable> attempt(CyclicBarrier barrier, Runnable action) {
		return () -> {
			barrier.await();
			try {
				action.run();
				return null;
			} catch (Throwable e) {
				return e;
			}
		};
	}

	private PotJoinRequest menu(String content) {
		return new PotJoinRequest(content, 12000);
	}

	private void assertCountMatchesRows(Long potId, int expected) {
		Pot pot = potRepository.findById(potId).orElseThrow();
		long rows = potMemberRepository.findByPotIdIn(List.of(potId)).size();
		assertThat(pot.getCurrentMemberCount()).isEqualTo(expected);
		assertThat(rows).isEqualTo(expected);
		assertThat(pot.getCurrentMemberCount()).isLessThanOrEqualTo(pot.getCapacity());
	}

	@Test
	@DisplayName("마지막 한 자리에 두 사람이 동시에 참여하면 한 명만 성공하고 인원과 참여 행 수가 일치한다")
	void twoMembersRaceForLastSeat() {
		Long hostId = givenMember();
		Long potId = givenPot(hostId, 2); // 총대 1명 + 남은 자리 1
		Long first = givenMember();
		Long second = givenMember();

		List<Throwable> results = runConcurrently(
			() -> potService.join(first, potId, menu("허니콤보")),
			() -> potService.join(second, potId, menu("양념치킨")));

		assertThat(results.stream().filter(java.util.Objects::isNull)).hasSize(1);

		// 실패한 쪽은 선검사에 걸렸으면 POT_FULL, 경합했으면 낙관적 락 충돌이다. 둘 다 정상이다.
		Throwable failure = results.stream().filter(java.util.Objects::nonNull).findFirst().orElseThrow();
		assertThat(isFullOrConflict(failure))
			.withFailMessage("예상치 못한 실패: %s", failure)
			.isTrue();

		assertCountMatchesRows(potId, 2);
	}

	@Test
	@DisplayName("같은 사람이 동시에 두 번 참여해도 참여 행은 하나만 생기고 인원도 한 번만 늘어난다")
	void sameMemberJoinsTwiceConcurrently() {
		Long hostId = givenMember();
		Long potId = givenPot(hostId, 4);
		Long joiner = givenMember();

		List<Throwable> results = runConcurrently(
			() -> potService.join(joiner, potId, menu("허니콤보")),
			() -> potService.join(joiner, potId, menu("허니콤보")));

		assertThat(results.stream().filter(java.util.Objects::isNull)).hasSize(1);

		// 선검사에 걸렸으면 POT_ALREADY_JOINED, 경합했으면 unique 제약 번역 또는 낙관적 락 충돌이다.
		Throwable failure = results.stream().filter(java.util.Objects::nonNull).findFirst().orElseThrow();
		assertThat(isAlreadyJoinedOrConflict(failure))
			.withFailMessage("예상치 못한 실패: %s", failure)
			.isTrue();

		assertCountMatchesRows(potId, 2); // 총대 + 참여자 1명
	}

	@Test
	@DisplayName("참여와 나가기가 동시에 실행돼도 인원 수와 참여 행 수가 어긋나지 않는다")
	void joinAndLeaveConcurrently() {
		Long hostId = givenMember();
		Long potId = givenPot(hostId, 4);
		Long leaver = givenMember();
		Long joiner = givenMember();

		potService.join(leaver, potId, menu("후라이드")); // 총대 + leaver = 2명

		List<Throwable> results = runConcurrently(
			() -> potService.join(joiner, potId, menu("양념치킨")),
			() -> potService.leave(leaver, potId));

		// 둘 다 성공할 수도, 한쪽이 락 충돌로 실패할 수도 있다. 어느 쪽이든 두 값은 일치해야 한다.
		long succeeded = results.stream().filter(java.util.Objects::isNull).count();
		assertThat(succeeded).isBetween(1L, 2L);

		Pot pot = potRepository.findById(potId).orElseThrow();
		long rows = potMemberRepository.findByPotIdIn(List.of(potId)).size();
		assertThat(rows)
			.withFailMessage("currentMemberCount=%d 인데 pot_members 행은 %d 개다",
				pot.getCurrentMemberCount(), rows)
			.isEqualTo(pot.getCurrentMemberCount());
	}

	private boolean isFullOrConflict(Throwable e) {
		return hasErrorCode(e, ErrorCode.POT_FULL) || e instanceof OptimisticLockingFailureException;
	}

	private boolean isAlreadyJoinedOrConflict(Throwable e) {
		return hasErrorCode(e, ErrorCode.POT_ALREADY_JOINED) || e instanceof OptimisticLockingFailureException;
	}

	private boolean hasErrorCode(Throwable e, ErrorCode code) {
		return e instanceof BusinessException business && business.getErrorCode() == code;
	}
}
