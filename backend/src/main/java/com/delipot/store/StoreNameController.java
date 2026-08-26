package com.delipot.store;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.delipot.auth.RequireAuthenticate;
import com.delipot.global.response.ApiResponse;
import com.delipot.store.dto.StoreNameRequest;
import com.delipot.store.dto.StoreNameResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Store", description = "배달앱 가게 링크")
@RestController
@RequestMapping("/api/pots/store-name")
@RequiredArgsConstructor
public class StoreNameController {

	private final StoreNameExtractor storeNameExtractor;

	/**
	 * 팟 생성 화면에서 링크만 붙여넣었을 때, 그리고 채팅방에서 가게 링크 말풍선의 미리보기 카드를
	 * 그릴 때 호출한다. 배민·요기요는 앱 공유 문구에 가게명이 있어 프론트에서 끝나고, 순수 링크만
	 * 공유되는 쿠팡이츠가 주로 여기로 온다.
	 *
	 * <p>로그인을 요구하는 이유는 보안이다. 열어두면 우리 서버가 임의 URL을 대신 긁어주는
	 * 오픈 프록시가 된다. 어디로 나가는지는 {@link StoreProvider}의 호스트 화이트리스트가 막는다.
	 */
	@Operation(
		summary = "가게 링크에서 가게명·미리보기 추출",
		description = "쿠팡이츠·요기요 링크의 Open Graph 태그에서 가게명·이미지·설명을 읽어온다. "
			+ "배달의민족은 응답에 가게명이 없어 항상 실패한다(요청도 보내지 않는다). "
			+ "추출 실패도 200이며 storeName이 null로 내려간다 — 화면은 손입력 상태를 유지하거나 "
			+ "(채팅 링크 카드라면) 미리보기 없이 링크만 보여주면 된다."
	)
	@RequireAuthenticate
	@PostMapping
	public ApiResponse<StoreNameResponse> extractStoreName(@Valid @RequestBody StoreNameRequest request) {
		return ApiResponse.ok(storeNameExtractor.extract(request.storeUrl()));
	}
}
