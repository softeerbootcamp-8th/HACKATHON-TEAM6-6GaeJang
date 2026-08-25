package com.delipot.pot;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.delipot.pot.dto.PotListRequest;
import com.delipot.pot.dto.PotListResponse;
import com.delipot.pot.dto.PotSummaryResponse;

/**
 * JPQL 필터와 구면 거리 재검증이 실제 DB에서 함께 동작하는지 본다.
 * 서비스만 목으로 테스트하면 쿼리 조건이 틀려도 통과하므로 여기서 같이 확인한다.
 */
@DataJpaTest
@ActiveProfiles("h2")
class PotListIntegrationTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	/** 고정 현재 시각: 2026-08-25 18:00 KST */
	private static final Instant NOW = Instant.parse("2026-08-25T09:00:00Z");
	private static final OffsetDateTime CURRENT = NOW.atZone(SEOUL).toOffsetDateTime();

	/** 목업의 "학동로 171" 기준점. */
	private static final BigDecimal MY_LAT = new BigDecimal("37.5172000");
	private static final BigDecimal MY_LNG = new BigDecimal("127.0286000");

	@Autowired
	private PotRepository potRepository;

	private PotService potService;

	@BeforeEach
	void setUp() {
		potService = new PotService(potRepository, Clock.fixed(NOW, SEOUL));
	}

	/** 정북으로 지정한 미터만큼 떨어진 좌표. 위도 1도 ≈ 111,320m. */
	private static BigDecimal latitudeOffsetBy(int meters) {
		return MY_LAT.add(BigDecimal.valueOf(meters / 111_320.0).setScale(7, java.math.RoundingMode.HALF_UP));
	}

	private Pot.PotBuilder pot(String storeName, BigDecimal latitude, OffsetDateTime deadline) {
		return Pot.builder()
			.hostId(1L)
			.title(storeName + " 같이 시켜요")
			.description("저녁에 같이 시키실 분 구해요")
			.storeName(storeName)
			.storeUrl("https://web.coupangeats.com/share?storeId=1")
			.meetingPlace("동진시장 사거리 편의점 앞")
			.latitude(latitude)
			.longitude(MY_LNG)
			.capacity(4)
			.minOrderAmount(20000)
			.deadline(deadline)
			.bankName("카카오뱅크")
			.accountNumber("3333-01-1234567")
			.accountHolder("김하나");
	}

	private PotListResponse search(String keyword) {
		return potService.findNearby(new PotListRequest(MY_LAT, MY_LNG, keyword));
	}

	@Test
	@DisplayName("300m 이내 팟만 나오고 밖은 걸러진다")
	void filtersByRadius() {
		potRepository.save(pot("교촌 치킨 연남점", latitudeOffsetBy(100), CURRENT.plusHours(1)).build());
		potRepository.save(pot("호백반점", latitudeOffsetBy(280), CURRENT.plusHours(2)).build());
		potRepository.save(pot("먼가게", latitudeOffsetBy(500), CURRENT.plusHours(3)).build());
		potRepository.flush();

		PotListResponse response = search(null);

		assertThat(response.pots()).extracting(PotSummaryResponse::storeName)
			.containsExactly("교촌 치킨 연남점", "호백반점");
	}

	/**
	 * 사각형은 통과하지만 구면 거리로는 300m를 넘는 대각선 위치.
	 * 자바 쪽 재검증이 빠지면 이 팟이 목록에 섞인다.
	 */
	@Test
	@DisplayName("사각형 모서리에 걸친 팟은 거리 재검증에서 걸러진다")
	void filtersBoxCornerByRealDistance() {
		// 북쪽 250m + 동쪽 250m → 직선거리 약 354m
		BigDecimal cornerLat = latitudeOffsetBy(250);
		BigDecimal cornerLng = MY_LNG.add(new BigDecimal("0.0028300"));

		potRepository.save(pot("모서리가게", cornerLat, CURRENT.plusHours(1)).longitude(cornerLng).build());
		potRepository.flush();

		assertThat(Geo.distanceMeters(MY_LAT, MY_LNG, cornerLat, cornerLng)).isGreaterThan(300.0);
		assertThat(search(null).pots()).isEmpty();
	}

	@Test
	@DisplayName("마감 임박순으로 정렬된다")
	void sortsByDeadlineAscending() {
		potRepository.save(pot("세번째", latitudeOffsetBy(50), CURRENT.plusHours(3)).build());
		potRepository.save(pot("첫번째", latitudeOffsetBy(50), CURRENT.plusMinutes(30)).build());
		potRepository.save(pot("두번째", latitudeOffsetBy(50), CURRENT.plusHours(1)).build());
		potRepository.flush();

		assertThat(search(null).pots()).extracting(PotSummaryResponse::storeName)
			.containsExactly("첫번째", "두번째", "세번째");
	}

	@Test
	@DisplayName("마감이 지난 팟은 나오지 않는다")
	void excludesPastDeadline() {
		potRepository.save(pot("지난팟", latitudeOffsetBy(50), CURRENT.minusMinutes(1)).build());
		potRepository.save(pot("살아있는팟", latitudeOffsetBy(50), CURRENT.plusMinutes(1)).build());
		potRepository.flush();

		assertThat(search(null).pots()).extracting(PotSummaryResponse::storeName)
			.containsExactly("살아있는팟");
	}

	@Test
	@DisplayName("정원이 다 찬 팟은 목록에서 제외된다")
	void excludesFullPots() {
		Pot full = potRepository.save(pot("만석가게", latitudeOffsetBy(50), CURRENT.plusHours(1)).capacity(2).build());
		potRepository.save(pot("여유가게", latitudeOffsetBy(50), CURRENT.plusHours(2)).build());
		potRepository.flush();

		// 참여 도메인이 없어 카운트를 직접 올린다. 정원 2에 2명이면 만석이다.
		potRepository.findById(full.getId()).ifPresent(p -> setMemberCount(p, 2));
		potRepository.flush();

		assertThat(search(null).pots()).extracting(PotSummaryResponse::storeName)
			.containsExactly("여유가게");
	}

	@Test
	@DisplayName("키워드로 가게 이름을 대소문자 구분 없이 거른다")
	void filtersByKeyword() {
		potRepository.save(pot("교촌 치킨 연남점", latitudeOffsetBy(50), CURRENT.plusHours(1)).build());
		potRepository.save(pot("BHC 치킨", latitudeOffsetBy(50), CURRENT.plusHours(2)).build());
		potRepository.save(pot("호백반점", latitudeOffsetBy(50), CURRENT.plusHours(3)).build());
		potRepository.flush();

		assertThat(search("치킨").pots()).extracting(PotSummaryResponse::storeName)
			.containsExactly("교촌 치킨 연남점", "BHC 치킨");
		assertThat(search("bhc").pots()).extracting(PotSummaryResponse::storeName)
			.containsExactly("BHC 치킨");
	}

	@Test
	@DisplayName("키워드가 빈 문자열이거나 공백뿐이면 전체를 준다")
	void blankKeywordMeansNoFilter() {
		potRepository.save(pot("교촌 치킨 연남점", latitudeOffsetBy(50), CURRENT.plusHours(1)).build());
		potRepository.save(pot("호백반점", latitudeOffsetBy(50), CURRENT.plusHours(2)).build());
		potRepository.flush();

		assertThat(search("").pots()).hasSize(2);
		assertThat(search("   ").pots()).hasSize(2);
		assertThat(search(null).pots()).hasSize(2);
	}

	@Test
	@DisplayName("카드에 필요한 필드가 모두 채워진다")
	void summaryCarriesCardFields() {
		potRepository.save(pot("교촌 치킨 연남점", latitudeOffsetBy(100), CURRENT.plusHours(1)).build());
		potRepository.flush();

		PotSummaryResponse card = search(null).pots().getFirst();

		assertThat(card.potId()).isNotNull();
		assertThat(card.storeName()).isEqualTo("교촌 치킨 연남점");
		assertThat(card.title()).isEqualTo("교촌 치킨 연남점 같이 시켜요");
		assertThat(card.meetingPlace()).isEqualTo("동진시장 사거리 편의점 앞");
		assertThat(card.currentMemberCount()).isEqualTo(1);
		assertThat(card.capacity()).isEqualTo(4);
		assertThat(card.deadline()).isEqualTo(CURRENT.plusHours(1));
	}

	/** 리뷰 지적: 이스케이프가 없으면 % 한 글자에 전체 목록이 나온다. */
	@Test
	@DisplayName("검색어의 % 는 와일드카드가 아니라 글자 그대로 취급된다")
	void escapesLikeWildcards() {
		potRepository.save(pot("교촌 치킨 연남점", latitudeOffsetBy(50), CURRENT.plusHours(1)).build());
		potRepository.save(pot("50% 할인마트", latitudeOffsetBy(50), CURRENT.plusHours(2)).build());
		potRepository.flush();

		// % 한 글자로 전체가 딸려오면 안 된다 — "50% 할인마트" 만 걸려야 한다
		assertThat(search("%").pots()).extracting(PotSummaryResponse::storeName)
			.containsExactly("50% 할인마트");
		assertThat(search("50%").pots()).extracting(PotSummaryResponse::storeName)
			.containsExactly("50% 할인마트");
	}

	@Test
	@DisplayName("검색어의 _ 도 글자 그대로 취급된다")
	void escapesUnderscoreWildcard() {
		potRepository.save(pot("교촌 치킨 연남점", latitudeOffsetBy(50), CURRENT.plusHours(1)).build());
		potRepository.flush();

		// _ 가 와일드카드면 "교촌"의 아무 한 글자에 걸려 결과가 나온다
		assertThat(search("교_").pots()).isEmpty();
	}

	@Test
	@DisplayName("결과가 상한을 넘으면 잘라서 준다")
	void capsResultCount() {
		for (int i = 0; i < 105; i++) {
			potRepository.save(pot("가게" + i, latitudeOffsetBy(50), CURRENT.plusMinutes(30 + i)).build());
		}
		potRepository.flush();

		assertThat(search(null).pots()).hasSize(100);
	}

	@Test
	@DisplayName("카드에는 정산 계좌·가게 링크가 실리지 않는다")
	void summaryOmitsSensitiveFields() {
		potRepository.save(pot("교촌 치킨 연남점", latitudeOffsetBy(100), CURRENT.plusHours(1)).build());
		potRepository.flush();

		PotSummaryResponse card = search(null).pots().getFirst();

		// record 컴포넌트가 곧 JSON 필드다. 계좌/링크 접근자가 없다는 것이 곧 응답에 없다는 뜻이다.
		assertThat(PotSummaryResponse.class.getRecordComponents())
			.extracting(java.lang.reflect.RecordComponent::getName)
			.containsExactlyInAnyOrder("potId", "title", "storeName", "description",
				"meetingPlace", "deadline", "currentMemberCount", "capacity");
		assertThat(card.potId()).isNotNull();
	}

	@Test
	@DisplayName("반경 안에 팟이 없으면 빈 목록을 준다 — 404가 아니다")
	void emptyResultIsNotAnError() {
		potRepository.save(pot("먼가게", latitudeOffsetBy(900), CURRENT.plusHours(1)).build());
		potRepository.flush();

		assertThat(search(null).pots()).isEmpty();
	}

	/** 참여 도메인이 없어 테스트에서만 카운트를 직접 바꾼다. */
	private static void setMemberCount(Pot pot, int count) {
		try {
			var field = Pot.class.getDeclaredField("currentMemberCount");
			field.setAccessible(true);
			field.setInt(pot, count);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
