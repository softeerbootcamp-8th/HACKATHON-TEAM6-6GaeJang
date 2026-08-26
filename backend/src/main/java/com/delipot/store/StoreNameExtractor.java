package com.delipot.store;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.delipot.store.dto.StoreNameResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 배달앱 가게 링크에서 가게명을 뽑아온다. 팟 생성 화면의 "가게 링크 붙여넣기 → 가게명 자동 기입"만
 * 담당하는 독립 기능이라 팟 생성 API는 이 흐름을 전혀 모른다 — 자동이든 손입력이든
 * {@code storeName}은 똑같이 필수 문자열로 들어온다.
 *
 * <p><b>실패는 예외가 아니다.</b> 배민은 구조적으로 절대 성공할 수 없고(가게명이 응답에 없다),
 * 지원하지 않는 호스트도 정상적인 사용자 행동이다. 그래서 4xx를 던지지 않고 성공 응답에
 * {@code storeName = null}을 담아 내린다 — 프론트는 값이 없으면 조용히 손입력 상태를 유지하면 된다.
 * 예외로 만들면 화면마다 "무시해도 되는 에러"를 골라내는 분기가 생긴다.
 *
 * <p>프론트가 이 API를 부르는 건 <b>공유 텍스트에서 가게명을 못 얻었을 때뿐</b>이다. 배민·요기요는
 * 앱 공유 문구에 가게명이 들어 있어 네트워크 없이 끝나고, 순수 링크만 주는 쿠팡이츠가 주로 여기로 온다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreNameExtractor {

	/**
	 * 붙여넣기 직후 화면이 기다리는 요청이라 짧게 잡는다. 넘기면 손입력이 가능한 상태로
	 * 돌아가는 것이 더 낫다 — 자동 기입은 편의 기능이고, 여기서 오래 멈추면 폼 전체가 굳은 것처럼 보인다.
	 */
	private static final Duration TIMEOUT = Duration.ofSeconds(3);

	/**
	 * 읽을 본문 상한. og 태그는 {@code <head>}에 있으니 앞부분만 있으면 충분하고,
	 * 동시에 악의적/거대한 응답으로 힙을 밀어버리는 것을 막는다.
	 */
	private static final int MAX_BODY_BYTES = 256 * 1024;

	/** 쿠팡이츠는 실제 브라우저로 보여야 SSR 페이지를 준다. */
	private static final String BROWSER_USER_AGENT =
		"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
			+ "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

	/**
	 * 요기요(Airbridge)는 크롤러로 보일 때만 og 태그가 담긴 200을 준다. 브라우저 UA로는
	 * 본문 없는 302만 온다 — UA를 바꾸는 게 아니라 <b>UA를 크롤러로 두는 것</b>이 성공 조건이다.
	 */
	private static final String CRAWLER_USER_AGENT = "facebookexternalhit/1.1";

	private static final String FAILURE_MESSAGE = "링크에서 가게명을 가져오지 못했어요. 직접 입력해주세요.";

	private final StoreNamePageParser parser;

	/** 리다이렉트를 따라가는 클라이언트(쿠팡이츠용). */
	private final HttpClient redirectingClient = HttpClient.newBuilder()
		.connectTimeout(TIMEOUT)
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	/**
	 * 리다이렉트를 따라가지 않는 클라이언트(요기요용). 따라가면 요기요 웹 홈으로 떨어져
	 * 가게 정보가 사라진다 — 목적지 쿼리스트링에 shop_id가 없다.
	 */
	private final HttpClient nonRedirectingClient = HttpClient.newBuilder()
		.connectTimeout(TIMEOUT)
		.followRedirects(HttpClient.Redirect.NEVER)
		.build();

	public StoreNameResponse extract(String storeUrl) {
		Optional<URI> uri = parseHttpUri(storeUrl);
		if (uri.isEmpty()) {
			return StoreNameResponse.unavailable(null, "가게 링크 형식이 아니에요. 가게명을 직접 입력해주세요.");
		}

		Optional<StoreProvider> provider = StoreProvider.fromHost(uri.get().getHost());
		if (provider.isEmpty()) {
			return StoreNameResponse.unavailable(null,
				"쿠팡이츠·요기요·배달의민족 링크만 인식해요. 가게명을 직접 입력해주세요.");
		}

		StoreProvider app = provider.get();
		if (!app.isFetchable()) {
			// 배민. 시도해서 실패하는 게 아니라 성공 불가가 확정된 케이스라 요청을 아낀다.
			return StoreNameResponse.unavailable(app,
				app.getDisplayName() + " 링크는 가게명을 가져올 수 없어요. 직접 입력해주세요.");
		}

		return parser.parse(app, fetchHtml(uri.get(), app))
			.map(name -> StoreNameResponse.extracted(name, app))
			.orElseGet(() -> StoreNameResponse.unavailable(app, FAILURE_MESSAGE));
	}

	/** 스킴까지 확인한다. {@code file:}, {@code gopher:} 같은 스킴은 호스트 판정 전에 끊는다. */
	private Optional<URI> parseHttpUri(String storeUrl) {
		try {
			URI uri = new URI(storeUrl.strip());
			String scheme = uri.getScheme();
			if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
				return Optional.empty();
			}
			return uri.getHost() == null ? Optional.empty() : Optional.of(uri);
		} catch (URISyntaxException e) {
			return Optional.empty();
		}
	}

	/**
	 * @return 응답 본문. 200이 아니거나 통신이 실패하면 null — 호출부는 이를 "추출 실패"로만 취급한다.
	 */
	private String fetchHtml(URI uri, StoreProvider provider) {
		HttpRequest request = HttpRequest.newBuilder(uri)
			.timeout(TIMEOUT)
			.header("User-Agent", userAgentFor(provider))
			.header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
			// 쿠팡이츠는 이 헤더로 로케일을 협상한다. 빼면 og:title이 로마자로 영문화돼 오고,
			// 그것도 200 + 파싱 성공이라 실패로 감지되지 않은 채 저장된다(한글 검색이 전부 빈다).
			.header("Accept-Language", "ko-KR,ko;q=0.9")
			// JDK HttpClient는 gzip을 자동 해제하지 않는다. 압축을 받으면 본문이 바이너리가 되어
			// 정규식이 아무것도 못 찾는다 — 애초에 압축하지 말라고 명시한다.
			.header("Accept-Encoding", "identity")
			.GET()
			.build();

		HttpClient client = provider.isFollowRedirects() ? redirectingClient : nonRedirectingClient;

		try {
			HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream body = response.body()) {
				if (response.statusCode() != 200) {
					// 요기요를 브라우저 UA로 부르면 여기(302 + 빈 본문)로 온다.
					log.debug("가게명 추출 실패: {} 응답 {}", provider, response.statusCode());
					return null;
				}
				return readBounded(body, charsetOf(response));
			}
		} catch (IOException e) {
			log.warn("가게명 추출 통신 실패: {} {}", provider, e.getMessage());
			return null;
		} catch (InterruptedException e) {
			// 인터럽트 플래그를 삼키면 상위(톰캣 워커)가 종료 신호를 놓친다.
			Thread.currentThread().interrupt();
			return null;
		}
	}

	private String userAgentFor(StoreProvider provider) {
		return provider == StoreProvider.YOGIYO ? CRAWLER_USER_AGENT : BROWSER_USER_AGENT;
	}

	/**
	 * 상한까지만 읽는다. 경계에서 멀티바이트 문자가 잘려 마지막 글자가 깨질 수 있지만,
	 * 필요한 og 태그는 {@code <head>} 맨 앞에 있어 실사용에 영향이 없다.
	 */
	private String readBounded(InputStream body, Charset charset) throws IOException {
		return new String(body.readNBytes(MAX_BODY_BYTES), charset);
	}

	/** {@code content-type: text/html; charset=utf-8}. 없거나 모르는 값이면 UTF-8로 읽는다. */
	private Charset charsetOf(HttpResponse<?> response) {
		String contentType = response.headers().firstValue("content-type").orElse("");
		int marker = contentType.toLowerCase(Locale.ROOT).indexOf("charset=");
		if (marker < 0) {
			return StandardCharsets.UTF_8;
		}
		String name = contentType.substring(marker + "charset=".length()).split(";")[0].strip()
			.replace("\"", "");
		try {
			return Charset.forName(name);
		} catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
			return StandardCharsets.UTF_8;
		}
	}
}
