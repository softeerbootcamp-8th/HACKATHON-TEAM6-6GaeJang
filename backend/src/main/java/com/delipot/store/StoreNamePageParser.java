package com.delipot.store;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * 받아온 HTML에서 가게명을 뽑는다. 네트워크를 모르는 순수 로직이라 단위 테스트로 전부 덮인다.
 *
 * <p>파싱 대상은 배달앱 API가 아니라 <b>Open Graph 메타 태그</b>다. 카카오톡 링크 미리보기가 쓰는
 * 것과 같은 통로다 — 카톡이 카드에 가게명을 그릴 수 있다는 건 그 문자열이 HTTP 응답 본문에
 * 실려 온다는 뜻이고, 우리는 같은 응답을 받아 같은 태그를 읽는다.
 *
 * <p>HTML 파서 라이브러리(Jsoup)를 넣지 않고 정규식을 쓰는 이유는 필요한 것이 {@code <head>}의
 * meta 태그 하나뿐이어서다. DOM 트리를 만들 이유가 없고, 해커톤에서 의존성을 늘리지 않는다.
 *
 * <p><b>핵심 규칙: og:title이 있다는 사실만으로 성공 판정하면 안 된다.</b> 세 앱 모두 200과
 * 유효한 og:title을 준다. 배민은 {@code [배달의민족] 같이먹어요!}로 쿠팡이츠와 똑같은 대괄호
 * 형식이어서, 공통 파서를 쓰면 "배달의민족"이 가게명으로 조용히 저장된다. 그래서 배달앱별로
 * 형식을 따로 검증한다({@link StoreProvider} 주석의 실측 결과 참고).
 */
@Component
public class StoreNamePageParser {

	/**
	 * og:title / twitter:title 추출. 속성 순서가 뒤집힌 경우까지 두 패턴으로 받는다 —
	 * HTML 명세상 속성 순서는 자유이고, 앱마다 템플릿이 달라 한쪽만 두면 조용히 실패한다.
	 */
	private static final Pattern PROPERTY_FIRST = Pattern.compile(
		"<meta[^>]+?(?:property|name)\\s*=\\s*[\"'](?:og:title|twitter:title)[\"'][^>]*?"
			+ "content\\s*=\\s*[\"']([^\"']*)[\"']",
		Pattern.CASE_INSENSITIVE);

	private static final Pattern CONTENT_FIRST = Pattern.compile(
		"<meta[^>]+?content\\s*=\\s*[\"']([^\"']*)[\"'][^>]*?"
			+ "(?:property|name)\\s*=\\s*[\"'](?:og:title|twitter:title)[\"']",
		Pattern.CASE_INSENSITIVE);

	/** 쿠팡이츠 og:title 형식: {@code [가게명] ★ 4.9(6,127)}. 첫 대괄호 안이 가게명이다. */
	private static final Pattern BRACKETED_NAME = Pattern.compile("^\\s*\\[([^\\]]+)\\]");

	/**
	 * 가게명이 아니라 서비스 이름·광고 문구가 내려온 경우. 여기에 걸리면 실패로 처리한다.
	 *
	 * <p>이 방어가 필요한 이유는 링크가 가게가 아닌 곳(홈, 앱 다운로드 랜딩)으로 흘렀을 때도
	 * 응답이 200 + 유효한 og:title이기 때문이다. 걸러내지 않으면 "요기요"라는 가게가 생긴다.
	 */
	private static final Set<String> GENERIC_TITLES = Set.of(
		"요기요", "yogiyo", "yogiyo - food delivery", "배달의민족", "배민", "쿠팡이츠", "coupang eats");

	/** DB 컬럼 길이({@code pots.store_name})와 같다. 넘으면 잘라 쓰지 않고 실패로 본다. */
	private static final int MAX_STORE_NAME_LENGTH = 100;

	/**
	 * @param html 응답 본문. null이면(요기요 302처럼 본문이 없을 때) 빈 값을 돌려준다.
	 * @return 검증을 통과한 가게명. 형식이 어긋나면 빈 값 — 추측해서 채우지 않는다.
	 */
	public Optional<String> parse(StoreProvider provider, String html) {
		if (html == null || html.isBlank()) {
			return Optional.empty();
		}

		return findTitle(html)
			.map(StoreNamePageParser::unescapeHtml)
			.flatMap(title -> extractName(provider, title))
			.map(String::strip)
			.filter(this::isPlausibleStoreName);
	}

	private Optional<String> findTitle(String html) {
		Matcher propertyFirst = PROPERTY_FIRST.matcher(html);
		if (propertyFirst.find()) {
			return Optional.of(propertyFirst.group(1));
		}
		Matcher contentFirst = CONTENT_FIRST.matcher(html);
		if (contentFirst.find()) {
			return Optional.of(contentFirst.group(1));
		}
		return Optional.empty();
	}

	/**
	 * 배달앱별 형식 해석. 이 분기가 없으면 배민의 {@code [배달의민족] 같이먹어요!}가
	 * 쿠팡이츠 규칙에 그대로 걸려 "배달의민족"이 가게명으로 저장된다.
	 */
	private Optional<String> extractName(StoreProvider provider, String title) {
		return switch (provider) {
			// 대괄호가 없으면 가게 페이지가 아니다. 통째로 쓰면 평점·리뷰수까지 이름에 섞인다.
			case COUPANG_EATS -> {
				Matcher matcher = BRACKETED_NAME.matcher(title);
				yield matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
			}
			// 요기요 단축링크의 og:title은 가게명 그대로다("60계치킨-광주용봉점"). 후처리가 없다.
			case YOGIYO -> Optional.of(title);
			// 네트워크로 여기까지 올 일이 없다(fetchable=false). 규칙이 새면 즉시 막는다.
			case BAEMIN -> Optional.empty();
		};
	}

	private boolean isPlausibleStoreName(String name) {
		if (name.isBlank() || name.length() > MAX_STORE_NAME_LENGTH) {
			return false;
		}
		return !GENERIC_TITLES.contains(name.toLowerCase(Locale.ROOT));
	}

	/**
	 * meta content에 남아 있는 HTML 엔티티를 되돌린다. 가게명에 {@code &}가 들어가면
	 * {@code &amp;}로 인코딩돼 오므로("BBQ &amp; 치킨"), 풀지 않으면 그대로 저장된다.
	 * {@code &amp;}를 마지막에 푸는 이유는 먼저 풀면 {@code &amp;lt;}가 {@code <}로 이중 해석된다.
	 */
	private static String unescapeHtml(String value) {
		return value
			.replace("&quot;", "\"")
			.replace("&#39;", "'")
			.replace("&apos;", "'")
			.replace("&lt;", "<")
			.replace("&gt;", ">")
			.replace("&nbsp;", " ")
			.replace("&amp;", "&");
	}
}
