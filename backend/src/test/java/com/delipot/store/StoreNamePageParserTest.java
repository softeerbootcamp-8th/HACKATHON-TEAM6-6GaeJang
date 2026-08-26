package com.delipot.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 실측한 응답 형식을 그대로 넣어 검증한다. 네트워크를 타지 않으므로 CI에서도 항상 돈다.
 *
 * <p>여기서 가장 중요한 건 정상 흐름이 아니라 {@link #baeminGenericTitleIsNeverAStoreName()}다 —
 * 배민 og:title이 쿠팡이츠와 똑같은 대괄호 형식이라, 공통 파서를 쓰면 "배달의민족"이 가게명으로
 * 저장되면서도 200 + 파싱 성공이라 실패로 감지되지 않는다.
 */
class StoreNamePageParserTest {

	private final StoreNamePageParser parser = new StoreNamePageParser();

	/** 대부분의 테스트는 가게명만 보므로, 성공했다는 전제로 가게명만 꺼내는 헬퍼. */
	private Optional<String> storeName(StoreProvider provider, String html) {
		return parser.parse(provider, html).map(StoreNamePageParser.StorePageInfo::storeName);
	}

	@Test
	@DisplayName("쿠팡이츠 - 첫 대괄호 안을 가게명으로 뽑고 평점·리뷰수는 버린다")
	void coupangEatsTakesBracketedName() {
		String html = meta("og:title", "[호백반점] ★ 4.9(6,127)");

		assertThat(storeName(StoreProvider.COUPANG_EATS, html)).contains("호백반점");
	}

	@Test
	@DisplayName("쿠팡이츠 - 대괄호가 없으면 가게 페이지가 아니므로 실패로 본다")
	void coupangEatsRejectsTitleWithoutBrackets() {
		String html = meta("og:title", "쿠팡이츠 - 맛있는 음식 배달");

		assertThat(parser.parse(StoreProvider.COUPANG_EATS, html)).isEmpty();
	}

	@Test
	@DisplayName("요기요 - og:title이 가게명 그대로라 후처리하지 않는다")
	void yogiyoUsesTitleAsIs() {
		String html = meta("og:title", "60계치킨-광주용봉점");

		assertThat(storeName(StoreProvider.YOGIYO, html)).contains("60계치킨-광주용봉점");
	}

	/**
	 * 실측: {@code https://www.baemin.com/shopDetail?shopDetail_shopNo=14698270}이 내려주는 값이다.
	 * 쿠팡이츠와 같은 대괄호 형식이라 배달앱별 분기가 없으면 "배달의민족"이 가게명이 된다.
	 */
	@Test
	@DisplayName("배민 - 대괄호 형식이 같아도 절대 가게명으로 쓰지 않는다")
	void baeminGenericTitleIsNeverAStoreName() {
		String html = meta("og:title", "[배달의민족] 같이먹어요!");

		// 배달앱별 분기에서 먼저 막힌다.
		assertThat(parser.parse(StoreProvider.BAEMIN, html)).isEmpty();
		// 분기가 새더라도 GENERIC_TITLES가 두 번째 그물로 막는다. 두 겹인 이유는 아래 테스트가 설명한다.
		assertThat(parser.parse(StoreProvider.COUPANG_EATS, html)).isEmpty();
	}

	/**
	 * 대괄호 규칙은 안에 든 게 가게명인지 광고 문구인지 구별하지 못한다. 즉 이 규칙 하나로는
	 * 배민을 걸러낼 수 없고, "어느 앱의 링크인가"를 먼저 판정하는 것이 안전장치의 본체다.
	 * 서비스명 블랙리스트는 그 뒤를 받치는 보조 수단일 뿐이다.
	 */
	@Test
	@DisplayName("대괄호 규칙 자체는 내용을 가리지 않는다 - 그래서 배달앱 판정이 먼저다")
	void bracketRuleAloneIsIndiscriminate() {
		String html = meta("og:title", "[오늘의 프로모션] 배달팁 무료");

		assertThat(storeName(StoreProvider.COUPANG_EATS, html)).contains("오늘의 프로모션");
	}

	@Test
	@DisplayName("서비스 이름만 내려오면 실패로 본다 - '요기요'라는 가게가 생기지 않게")
	void genericServiceNameIsRejected() {
		assertThat(parser.parse(StoreProvider.YOGIYO, meta("og:title", "요기요"))).isEmpty();
		assertThat(parser.parse(StoreProvider.YOGIYO, meta("og:title", "Yogiyo - Food Delivery"))).isEmpty();
	}

	@Test
	@DisplayName("속성 순서가 content 먼저여도 읽는다")
	void readsReversedAttributeOrder() {
		String html = "<meta content=\"60계치킨-광주용봉점\" property=\"og:title\" />";

		assertThat(storeName(StoreProvider.YOGIYO, html)).contains("60계치킨-광주용봉점");
	}

	@Test
	@DisplayName("og:title이 없으면 twitter:title로 대체한다")
	void fallsBackToTwitterTitle() {
		String html = meta("twitter:title", "60계치킨-광주용봉점");

		assertThat(storeName(StoreProvider.YOGIYO, html)).contains("60계치킨-광주용봉점");
	}

	@Test
	@DisplayName("HTML 엔티티를 되돌린다 - 가게명의 &가 &amp;로 저장되지 않게")
	void unescapesHtmlEntities() {
		String html = meta("og:title", "BBQ &amp; 치킨 강남점");

		assertThat(storeName(StoreProvider.YOGIYO, html)).contains("BBQ & 치킨 강남점");
	}

	@Test
	@DisplayName("본문이 없으면(요기요를 브라우저 UA로 부른 302) 실패로 본다")
	void emptyBodyFails() {
		assertThat(parser.parse(StoreProvider.YOGIYO, null)).isEmpty();
		assertThat(parser.parse(StoreProvider.YOGIYO, "")).isEmpty();
	}

	@Test
	@DisplayName("og 태그가 아예 없는 HTML은 실패로 본다")
	void htmlWithoutOgTagFails() {
		assertThat(parser.parse(StoreProvider.YOGIYO, "<html><head><title>요기요</title></head></html>"))
			.isEmpty();
	}

	@Test
	@DisplayName("DB 컬럼 길이(100자)를 넘으면 잘라 쓰지 않고 실패로 본다")
	void tooLongNameFails() {
		String html = meta("og:title", "가".repeat(101));

		assertThat(parser.parse(StoreProvider.YOGIYO, html)).isEmpty();
	}

	private String meta(String property, String content) {
		return """
			<!DOCTYPE html><html><head>
			<meta charset="utf-8">
			<meta property="%s" content="%s" />
			</head><body></body></html>
			""".formatted(property, content);
	}

	// ---------- 채팅 링크 미리보기 카드용 (og:image / og:description) ----------

	@Test
	@DisplayName("가게명과 함께 og:image·og:description이 있으면 미리보기 카드용으로 함께 담는다")
	void extractsImageAndDescriptionAlongsideStoreName() {
		String html = """
			<!DOCTYPE html><html><head>
			<meta charset="utf-8">
			<meta property="og:title" content="[호백반점] ★ 4.9(6,127)" />
			<meta property="og:image" content="https://img.coupangeats.com/store/1.jpg" />
			<meta property="og:description" content="든든한 한 끼, 호백반점" />
			</head><body></body></html>
			""";

		StoreNamePageParser.StorePageInfo info = parser.parse(StoreProvider.COUPANG_EATS, html).orElseThrow();

		assertThat(info.storeName()).isEqualTo("호백반점");
		assertThat(info.imageUrl()).isEqualTo("https://img.coupangeats.com/store/1.jpg");
		assertThat(info.description()).isEqualTo("든든한 한 끼, 호백반점");
	}

	/** og:image·og:description은 가게명과 달리 없어도 실패가 아니다 — 카드에서 그 줄만 빠진다. */
	@Test
	@DisplayName("og:image·og:description이 없어도 가게명 추출은 실패하지 않고, 두 값은 null이다")
	void imageAndDescriptionAreOptional() {
		String html = meta("og:title", "[호백반점] ★ 4.9(6,127)");

		StoreNamePageParser.StorePageInfo info = parser.parse(StoreProvider.COUPANG_EATS, html).orElseThrow();

		assertThat(info.storeName()).isEqualTo("호백반점");
		assertThat(info.imageUrl()).isNull();
		assertThat(info.description()).isNull();
	}

	@Test
	@DisplayName("og:description이 없으면 twitter:description으로 대체한다")
	void descriptionFallsBackToTwitter() {
		String html = """
			<!DOCTYPE html><html><head>
			<meta charset="utf-8">
			<meta property="og:title" content="60계치킨-광주용봉점" />
			<meta name="twitter:description" content="바삭한 치킨" />
			</head><body></body></html>
			""";

		StoreNamePageParser.StorePageInfo info = parser.parse(StoreProvider.YOGIYO, html).orElseThrow();

		assertThat(info.description()).isEqualTo("바삭한 치킨");
	}

	/** 가게명 검증(대괄호·GENERIC_TITLES)에 실패하면 og:image가 있어도 통째로 실패다 — 반쪽짜리 카드를 만들지 않는다. */
	@Test
	@DisplayName("가게명 추출이 실패하면 og:image가 있어도 전체가 실패로 처리된다")
	void imagePresentDoesNotRescueFailedStoreName() {
		String html = """
			<!DOCTYPE html><html><head>
			<meta charset="utf-8">
			<meta property="og:title" content="쿠팡이츠 - 맛있는 음식 배달" />
			<meta property="og:image" content="https://img.coupangeats.com/home.jpg" />
			</head><body></body></html>
			""";

		assertThat(parser.parse(StoreProvider.COUPANG_EATS, html)).isEmpty();
	}
}
