package com.delipot.store;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 가게 링크를 발급한 배달앱. 호스트 화이트리스트 겸 추출 전략의 선택자다.
 *
 * <p>여기에 없는 호스트는 요청을 보내지 않는다. 이 화이트리스트가 SSRF 방어의 본체다 —
 * 사용자가 {@code http://169.254.169.254/latest/meta-data/}(클라우드 인스턴스 메타데이터)나
 * 사내망 주소를 넣어도 호스트 판정에서 먼저 걸려 우리 서버가 정찰 도구로 쓰이지 않는다.
 *
 * <p>세 앱의 링크 구조가 근본적으로 달라 공통 전략이 성립하지 않는다. 실측 결과:
 *
 * <ul>
 *   <li>{@link #COUPANG_EATS} — 웹에 가게 페이지가 실재하고 SSR된다. 브라우저 UA로도 200이 오지만
 *       {@code Accept-Language}로 로케일 협상을 하므로 헤더를 빠뜨리면 og:title이
 *       {@code [Hobak Banjeom (Chinese Restaurant)] ★ 4.9}로 영문화돼 온다. 이때도 200이고
 *       파싱도 성공해서 실패로 감지되지 않는다 — 로마자 이름이 저장되고 한글 검색이 전부 빈다.</li>
 *   <li>{@link #YOGIYO} — {@code ws.yogiyo.co.kr}은 요기요 서버가 아니라 Airbridge 딥링크 중계
 *       서버다({@code server: fiber}, {@code X-Airbridge-Current-Path}). UA로 응답을 가른다:
 *       사람으로 보이면 302로 앱/웹으로 넘기고, 크롤러로 보이면 저장해 둔 og 태그만 담은 정적
 *       HTML을 200으로 준다. 302를 따라가면 {@code www.yogiyo.co.kr/mobile/} 홈으로 떨어져
 *       가게 정보가 소실된다(목적지 쿼리에 shop_id가 없다). 그래서 조건이 쿠팡이츠와 정반대다.</li>
 *   <li>{@link #BAEMIN} — 앱 온리 서비스라 공개 웹 가게 페이지가 없다. 단축링크는 301로 살아있고
 *       목적지에 {@code shopDetail_shopNo}까지 있지만, 그 페이지 응답 4KB 전체에 가게명이라는
 *       문자열이 없다(UA 3종 동일). 게다가 og:title이 {@code [배달의민족] 같이먹어요!}로
 *       쿠팡이츠와 <b>똑같은 대괄호 형식</b>이다 — 공통 파서를 쓰면 "배달의민족"이 가게명으로
 *       저장된다. 그래서 요청 자체를 보내지 않고 즉시 실패로 확정한다.</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum StoreProvider {

	/** 쿠팡이츠. 순수 링크만 공유되므로 서버 추출이 유일한 자동 기입 경로다. */
	COUPANG_EATS("쿠팡이츠", List.of("coupangeats.com"), true, true),

	/** 요기요. 앱 공유 텍스트에 가게명이 들어 있어 보통은 프론트에서 먼저 해결된다. */
	YOGIYO("요기요", List.of("yogiyo.co.kr"), true, false),

	/** 배달의민족. 네트워크로는 얻을 수 없다 — 공유 텍스트 파싱 또는 손입력만 가능하다. */
	BAEMIN("배달의민족", List.of("baemin.com"), false, false);

	private final String displayName;

	/** 등록 가능 도메인. 서브도메인은 접미사로 함께 매칭한다(ws., web., s., www.). */
	private final List<String> domains;

	/** 네트워크 요청을 시도할 가치가 있는가. false면 요청 없이 실패로 끝낸다. */
	private final boolean fetchable;

	/**
	 * 리다이렉트를 따라갈지. 요기요는 따라가면 홈으로 떨어져 정보가 소실되므로 false다.
	 * 이 한 줄이 두 앱의 성패를 가른다.
	 */
	private final boolean followRedirects;

	/**
	 * 호스트로 배달앱을 찾는다. 화이트리스트에 없으면 빈 값 — 요청을 보내지 않는다.
	 *
	 * <p>{@code endsWith("." + domain)}으로 비교하는 이유는 {@code evilcoupangeats.com} 같은
	 * 호스트가 통과하지 못하게 하기 위해서다. 단순 {@code contains}면 공격자가 도메인 이름을
	 * 자기 호스트에 끼워 넣어 화이트리스트를 무력화할 수 있다.
	 */
	public static Optional<StoreProvider> fromHost(String host) {
		if (host == null || host.isBlank()) {
			return Optional.empty();
		}
		String normalized = host.toLowerCase();
		return Arrays.stream(values())
			.filter(provider -> provider.matches(normalized))
			.findFirst();
	}

	private boolean matches(String host) {
		return domains.stream()
			.anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
	}
}
