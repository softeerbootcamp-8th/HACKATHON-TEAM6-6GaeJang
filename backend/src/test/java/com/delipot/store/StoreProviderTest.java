package com.delipot.store;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 호스트 화이트리스트는 SSRF 방어의 본체다. 여기가 새면 우리 서버가 정찰 도구가 된다. */
class StoreProviderTest {

	@Test
	@DisplayName("실제 공유 링크의 서브도메인을 모두 인식한다")
	void recognizesRealShareHosts() {
		assertThat(StoreProvider.fromHost("web.coupangeats.com")).contains(StoreProvider.COUPANG_EATS);
		assertThat(StoreProvider.fromHost("ws.yogiyo.co.kr")).contains(StoreProvider.YOGIYO);
		assertThat(StoreProvider.fromHost("www.yogiyo.co.kr")).contains(StoreProvider.YOGIYO);
		assertThat(StoreProvider.fromHost("s.baemin.com")).contains(StoreProvider.BAEMIN);
		assertThat(StoreProvider.fromHost("www.baemin.com")).contains(StoreProvider.BAEMIN);
	}

	@Test
	@DisplayName("대문자 호스트도 인식한다")
	void hostMatchingIsCaseInsensitive() {
		assertThat(StoreProvider.fromHost("WS.YOGIYO.CO.KR")).contains(StoreProvider.YOGIYO);
	}

	/** contains 로 비교하면 통과해 버리는 케이스들이다. 화이트리스트 우회의 전형이다. */
	@Test
	@DisplayName("도메인 이름을 끼워 넣은 공격자 호스트는 통과하지 못한다")
	void rejectsLookalikeHosts() {
		assertThat(StoreProvider.fromHost("evilcoupangeats.com")).isEmpty();
		assertThat(StoreProvider.fromHost("baemin.com.attacker.io")).isEmpty();
		assertThat(StoreProvider.fromHost("yogiyo.co.kr.evil.net")).isEmpty();
	}

	@Test
	@DisplayName("내부망·메타데이터 주소는 화이트리스트에 없어 걸린다")
	void rejectsInternalHosts() {
		assertThat(StoreProvider.fromHost("169.254.169.254")).isEmpty();
		assertThat(StoreProvider.fromHost("localhost")).isEmpty();
		assertThat(StoreProvider.fromHost("10.0.0.1")).isEmpty();
	}

	@Test
	@DisplayName("배민은 네트워크로 성공할 수 없어 요청 대상이 아니다")
	void baeminIsNotFetchable() {
		assertThat(StoreProvider.BAEMIN.isFetchable()).isFalse();
		assertThat(StoreProvider.COUPANG_EATS.isFetchable()).isTrue();
		assertThat(StoreProvider.YOGIYO.isFetchable()).isTrue();
	}

	/** 이 한 줄이 뒤집히면 요기요는 조용히 항상 실패한다 — 홈으로 리다이렉트되며 정보가 소실된다. */
	@Test
	@DisplayName("요기요만 리다이렉트를 따라가지 않는다")
	void onlyYogiyoStopsAtRedirect() {
		assertThat(StoreProvider.YOGIYO.isFollowRedirects()).isFalse();
		assertThat(StoreProvider.COUPANG_EATS.isFollowRedirects()).isTrue();
	}
}
