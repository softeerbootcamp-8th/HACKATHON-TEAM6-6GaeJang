package com.delipot.store.dto;

import com.delipot.store.StoreNamePageParser.StorePageInfo;
import com.delipot.store.StoreProvider;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 가게명(+ 링크 미리보기) 추출 결과. <b>실패해도 200이다.</b>
 *
 * <p>배민 링크는 구조적으로 성공할 수 없고, 지원하지 않는 호스트를 붙여넣는 것도 정상적인 사용
 * 행동이다. 4xx로 만들면 프론트가 "무시해도 되는 에러"를 골라내는 분기를 갖게 되므로,
 * 실패를 정상 응답의 한 값으로 표현한다.
 *
 * <p>이 API는 팟 생성 화면의 가게명 자동 기입뿐 아니라, 채팅방 링크 말풍선의 미리보기 카드
 * ({@code imageUrl}/{@code description})도 같은 응답으로 겸한다 — 둘 다 "이 가게 링크의
 * Open Graph 정보"라는 같은 질문이라 엔드포인트를 나누지 않았다.
 *
 * @param storeName   추출된 가게명. <b>null이면 실패</b> — 프론트는 가게명 칸을 손입력 상태로 둔다.
 * @param provider    인식된 배달앱. 화이트리스트에 없는 호스트면 null.
 * @param reason      실패 이유(사용자에게 보여줄 문구). 성공이면 null.
 * @param imageUrl    미리보기 이미지(og:image). 없으면 null — 카드에서 그 줄만 뺀다.
 * @param description 미리보기 설명(og:description). 없으면 null.
 */
@Schema(description = "가게명 추출 결과")
public record StoreNameResponse(

	@Schema(description = "추출된 가게명. null이면 추출 실패이므로 직접 입력받아야 한다",
		example = "호백반점", nullable = true)
	String storeName,

	@Schema(description = "인식된 배달앱. 지원하지 않는 호스트면 null", nullable = true)
	StoreProvider provider,

	@Schema(description = "실패 이유. 성공이면 null", nullable = true)
	String reason,

	@Schema(description = "채팅 링크 미리보기 카드용 이미지 URL. 없으면 null", nullable = true)
	String imageUrl,

	@Schema(description = "채팅 링크 미리보기 카드용 설명. 없으면 null", nullable = true)
	String description
) {

	public static StoreNameResponse extracted(StorePageInfo info, StoreProvider provider) {
		return new StoreNameResponse(info.storeName(), provider, null, info.imageUrl(), info.description());
	}

	public static StoreNameResponse unavailable(StoreProvider provider, String reason) {
		return new StoreNameResponse(null, provider, reason, null, null);
	}
}
