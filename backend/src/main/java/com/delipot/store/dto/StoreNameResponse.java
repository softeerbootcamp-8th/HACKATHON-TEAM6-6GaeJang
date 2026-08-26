package com.delipot.store.dto;

import com.delipot.store.StoreProvider;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 가게명 추출 결과. <b>실패해도 200이다.</b>
 *
 * <p>배민 링크는 구조적으로 성공할 수 없고, 지원하지 않는 호스트를 붙여넣는 것도 정상적인 사용
 * 행동이다. 4xx로 만들면 프론트가 "무시해도 되는 에러"를 골라내는 분기를 갖게 되므로,
 * 실패를 정상 응답의 한 값으로 표현한다.
 *
 * @param storeName 추출된 가게명. <b>null이면 실패</b> — 프론트는 가게명 칸을 손입력 상태로 둔다.
 * @param provider  인식된 배달앱. 화이트리스트에 없는 호스트면 null.
 * @param reason    실패 이유(사용자에게 보여줄 문구). 성공이면 null.
 */
@Schema(description = "가게명 추출 결과")
public record StoreNameResponse(

	@Schema(description = "추출된 가게명. null이면 추출 실패이므로 직접 입력받아야 한다",
		example = "호백반점", nullable = true)
	String storeName,

	@Schema(description = "인식된 배달앱. 지원하지 않는 호스트면 null", nullable = true)
	StoreProvider provider,

	@Schema(description = "실패 이유. 성공이면 null", nullable = true)
	String reason
) {

	public static StoreNameResponse extracted(String storeName, StoreProvider provider) {
		return new StoreNameResponse(storeName, provider, null);
	}

	public static StoreNameResponse unavailable(StoreProvider provider, String reason) {
		return new StoreNameResponse(null, provider, reason);
	}
}
