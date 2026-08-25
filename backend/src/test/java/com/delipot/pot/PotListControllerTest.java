package com.delipot.pot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.delipot.auth.web.AuthContext;
import com.delipot.pot.dto.PotListRequest;
import com.delipot.pot.dto.PotListResponse;
import com.delipot.pot.dto.PotMemberResponse;
import com.delipot.pot.dto.PotSummaryResponse;

@WebMvcTest(PotController.class)
class PotListControllerTest {

	private static final Long ME = 7L;
	private static final OffsetDateTime DEADLINE =
		OffsetDateTime.of(2026, 8, 25, 19, 30, 0, 0, ZoneOffset.ofHours(9));

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PotService potService;

	/** 인증 인터셉터가 @WebMvcTest 에도 등록되므로 ThreadLocal 컨텍스트를 직접 심는다. */
	@BeforeEach
	void authenticate() {
		AuthContext.setMemberId(ME);
	}

	@AfterEach
	void clearAuth() {
		AuthContext.clear();
	}

	private static PotSummaryResponse card(Long potId, String storeName, PotStatus status, boolean isHost) {
		return new PotSummaryResponse(potId, "저녁에 같이 치킨 시키실 분 구해요", storeName,
			"같이 시켜요", "동진시장 사거리 편의점 앞", DEADLINE, 2, 4, status, isHost, 3L,
			List.of(new PotMemberResponse(ME, "연희동주민", isHost)));
	}

	private void givenAllThreeSections() {
		given(potService.findPots(eq(ME), any(PotListRequest.class))).willReturn(new PotListResponse(
			List.of(card(1L, "교촌 치킨 연남점", PotStatus.ACTIVE, true)),
			List.of(card(2L, "호백반점", PotStatus.ACTIVE, false)),
			List.of(card(3L, "역전우동", PotStatus.ACTIVE, false))
		));
	}

	@Test
	@DisplayName("GET /api/pots — 세 섹션을 각각의 배열로 준다")
	void getPots() throws Exception {
		givenAllThreeSections();

		mockMvc.perform(get("/api/pots"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.hosted[0].storeName").value("교촌 치킨 연남점"))
			.andExpect(jsonPath("$.data.joined[0].storeName").value("호백반점"))
			.andExpect(jsonPath("$.data.all[0].storeName").value("역전우동"))
			.andExpect(jsonPath("$.error").doesNotExist());
	}

	@Test
	@DisplayName("내가 연 팟 카드에는 isHost가 실린다 — 프론트가 내용 수정·나눔 완료 버튼을 이걸로 판단한다")
	void hostedCardCarriesHostFlag() throws Exception {
		givenAllThreeSections();

		mockMvc.perform(get("/api/pots"))
			.andExpect(jsonPath("$.data.hosted[0].isHost").value(true))
			.andExpect(jsonPath("$.data.hosted[0].status").value("ACTIVE"))
			.andExpect(jsonPath("$.data.joined[0].isHost").value(false));
	}

	@Test
	@DisplayName("카드에 참여자 닉네임이 실린다 — 우측 아바타용")
	void cardCarriesMemberNicknames() throws Exception {
		givenAllThreeSections();

		mockMvc.perform(get("/api/pots"))
			.andExpect(jsonPath("$.data.all[0].members[0].nickname").value("연희동주민"))
			.andExpect(jsonPath("$.data.all[0].members[0].memberId").value(7))
			.andExpect(jsonPath("$.data.all[0].chatRoomId").value(3));
	}

	@Test
	@DisplayName("계좌·가게 링크는 카드에 실리지 않는다")
	void cardOmitsSensitiveFields() throws Exception {
		givenAllThreeSections();

		mockMvc.perform(get("/api/pots"))
			.andExpect(jsonPath("$.data.all[0].accountNumber").doesNotExist())
			.andExpect(jsonPath("$.data.all[0].storeUrl").doesNotExist());
	}

	/**
	 * 좌표를 요청에서 받던 계약을 없앤 것에 대한 회귀 방지. 좌표를 받으면 남의 동네를 훑을 수 있고,
	 * 프론트가 좌표를 따로 들고 다녀야 해서 화면마다 어긋난다.
	 */
	@Test
	@DisplayName("좌표 없이도 조회된다 — 서버가 회원 인증 주소를 쓴다")
	void doesNotRequireCoordinates() throws Exception {
		givenAllThreeSections();

		mockMvc.perform(get("/api/pots"))
			.andExpect(status().isOk());

		verify(potService).findPots(eq(ME), any(PotListRequest.class));
	}

	@Test
	@DisplayName("keyword를 주면 그대로 서비스에 전달된다")
	void passesKeywordThrough() throws Exception {
		givenAllThreeSections();

		mockMvc.perform(get("/api/pots").param("keyword", "치킨"))
			.andExpect(status().isOk());

		ArgumentCaptor<PotListRequest> captor = ArgumentCaptor.forClass(PotListRequest.class);
		verify(potService).findPots(eq(ME), captor.capture());

		assertThat(captor.getValue().keyword()).isEqualTo("치킨");
	}

	@Test
	@DisplayName("keyword가 없으면 빈 문자열로 정규화된다")
	void normalizesMissingKeyword() throws Exception {
		givenAllThreeSections();

		mockMvc.perform(get("/api/pots")).andExpect(status().isOk());

		ArgumentCaptor<PotListRequest> captor = ArgumentCaptor.forClass(PotListRequest.class);
		verify(potService).findPots(eq(ME), captor.capture());

		assertThat(captor.getValue().searchKeyword()).isEmpty();
	}

	@Test
	@DisplayName("셋 다 비어도 200과 빈 배열 셋 — 404가 아니다. 프론트는 이걸로 빈 상태 화면을 그린다")
	void emptySectionsAreNotAnError() throws Exception {
		given(potService.findPots(eq(ME), any(PotListRequest.class)))
			.willReturn(new PotListResponse(List.of(), List.of(), List.of()));

		mockMvc.perform(get("/api/pots"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.hosted").isEmpty())
			.andExpect(jsonPath("$.data.joined").isEmpty())
			.andExpect(jsonPath("$.data.all").isEmpty());
	}

	@Test
	@DisplayName("비로그인 상태면 401 — 목록은 회원 주소 기준이라 로그인 없이는 기준점이 없다")
	void rejectsUnauthenticated() throws Exception {
		AuthContext.clear();

		mockMvc.perform(get("/api/pots"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

		verify(potService, never()).findPots(any(), any());
	}

	@Test
	@DisplayName("검색어가 100자를 넘으면 400")
	void rejectsTooLongKeyword() throws Exception {
		mockMvc.perform(get("/api/pots").param("keyword", "치".repeat(101)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		verify(potService, never()).findPots(any(), any());
	}
}
