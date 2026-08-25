package com.delipot.store;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.delipot.store.dto.StoreNameResponse;

/**
 * 네트워크를 타지 않고 끝나는 경로만 검증한다 — 링크 형식·호스트 판정·배민 차단.
 * 실제 HTTP 응답 해석은 {@link StoreNamePageParserTest}가 덮는다.
 */
class StoreNameExtractorTest {

	private final StoreNameExtractor extractor = new StoreNameExtractor(new StoreNamePageParser());

	@Test
	@DisplayName("배민 링크는 요청을 보내지 않고 즉시 실패한다")
	void baeminFailsWithoutRequest() {
		StoreNameResponse response = extractor.extract("https://s.baemin.com/0d000f0kYdAUl");

		assertThat(response.storeName()).isNull();
		assertThat(response.provider()).isEqualTo(StoreProvider.BAEMIN);
		assertThat(response.reason()).contains("직접 입력");
	}

	@Test
	@DisplayName("지원하지 않는 호스트는 요청 없이 실패한다")
	void unsupportedHostFails() {
		StoreNameResponse response = extractor.extract("https://example.com/store/1");

		assertThat(response.storeName()).isNull();
		assertThat(response.provider()).isNull();
	}

	@Test
	@DisplayName("http/https가 아닌 스킴은 호스트 판정 전에 끊는다")
	void nonHttpSchemeFails() {
		assertThat(extractor.extract("file:///etc/passwd").storeName()).isNull();
		assertThat(extractor.extract("ftp://s.baemin.com/x").storeName()).isNull();
	}

	/** 붙여넣는 중간 상태여도 400을 던지지 않는다 — 폼에 에러 배너가 떠서는 안 된다. */
	@Test
	@DisplayName("URL 형식이 아니어도 예외를 던지지 않고 실패 응답으로 흘린다")
	void malformedUrlFailsQuietly() {
		StoreNameResponse response = extractor.extract("호백반점 어때요?");

		assertThat(response.storeName()).isNull();
		assertThat(response.reason()).isNotBlank();
	}
}
