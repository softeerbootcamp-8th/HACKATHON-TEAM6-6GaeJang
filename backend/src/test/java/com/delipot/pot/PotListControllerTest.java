package com.delipot.pot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
import com.delipot.pot.dto.PotSummaryResponse;

@WebMvcTest(PotController.class)
class PotListControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PotService potService;

	/** 인증 인터셉터가 @WebMvcTest 에도 등록되므로 ThreadLocal 컨텍스트를 직접 심는다. */
	@BeforeEach
	void authenticate() {
		AuthContext.setMemberId(7L);
	}

	@AfterEach
	void clearAuth() {
		AuthContext.clear();
	}

	private void givenOneCard() {
		given(potService.findNearby(any(PotListRequest.class))).willReturn(new PotListResponse(List.of(
			new PotSummaryResponse(1L, "저녁에 같이 치킨 시키실 분 구해요", "교촌 치킨 연남점",
				"같이 시켜요", "동진시장 사거리 편의점 앞",
				OffsetDateTime.of(2026, 8, 25, 19, 30, 0, 0, ZoneOffset.ofHours(9)),
				2, 4)
		)));
	}

	@Test
	@DisplayName("GET /api/pots — 200과 ApiResponse 래핑으로 카드 목록을 준다")
	void getPots() throws Exception {
		givenOneCard();

		mockMvc.perform(get("/api/pots")
				.param("latitude", "37.5172")
				.param("longitude", "127.0286"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.pots[0].storeName").value("교촌 치킨 연남점"))
			.andExpect(jsonPath("$.data.pots[0].currentMemberCount").value(2))
			.andExpect(jsonPath("$.data.pots[0].capacity").value(4))
			.andExpect(jsonPath("$.data.pots[0].meetingPlace").value("동진시장 사거리 편의점 앞"))
			// 계좌·링크는 카드에 실리지 않는다
			.andExpect(jsonPath("$.data.pots[0].accountNumber").doesNotExist())
			.andExpect(jsonPath("$.data.pots[0].storeUrl").doesNotExist())
			.andExpect(jsonPath("$.error").doesNotExist());
	}

	@Test
	@DisplayName("keyword를 주면 그대로 서비스에 전달된다")
	void passesKeywordThrough() throws Exception {
		givenOneCard();

		mockMvc.perform(get("/api/pots")
				.param("latitude", "37.5172")
				.param("longitude", "127.0286")
				.param("keyword", "치킨"))
			.andExpect(status().isOk());

		ArgumentCaptor<PotListRequest> captor = ArgumentCaptor.forClass(PotListRequest.class);
		verify(potService).findNearby(captor.capture());

		assertThat(captor.getValue().keyword()).isEqualTo("치킨");
		assertThat(captor.getValue().latitude()).isEqualByComparingTo(new BigDecimal("37.5172"));
	}

	@Test
	@DisplayName("keyword가 없으면 빈 문자열로 정규화된다")
	void normalizesMissingKeyword() throws Exception {
		givenOneCard();

		mockMvc.perform(get("/api/pots")
				.param("latitude", "37.5172")
				.param("longitude", "127.0286"))
			.andExpect(status().isOk());

		ArgumentCaptor<PotListRequest> captor = ArgumentCaptor.forClass(PotListRequest.class);
		verify(potService).findNearby(captor.capture());

		assertThat(captor.getValue().searchKeyword()).isEmpty();
	}

	@Test
	@DisplayName("반경 안에 팟이 없으면 200과 빈 배열 — 404가 아니다")
	void emptyListIsNotAnError() throws Exception {
		given(potService.findNearby(any(PotListRequest.class)))
			.willReturn(new PotListResponse(List.of()));

		mockMvc.perform(get("/api/pots")
				.param("latitude", "37.5172")
				.param("longitude", "127.0286"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.pots").isArray())
			.andExpect(jsonPath("$.data.pots").isEmpty());
	}

	@Test
	@DisplayName("비로그인 상태면 401 — 임의 좌표로 남의 동네를 훑을 수 없다")
	void rejectsUnauthenticated() throws Exception {
		AuthContext.clear();

		mockMvc.perform(get("/api/pots")
				.param("latitude", "37.5172")
				.param("longitude", "127.0286"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

		verify(potService, never()).findNearby(any());
	}

	@Test
	@DisplayName("좌표가 없으면 400 INVALID_INPUT")
	void rejectsMissingCoordinates() throws Exception {
		mockMvc.perform(get("/api/pots"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		verify(potService, never()).findNearby(any());
	}

	@Test
	@DisplayName("위도가 범위를 벗어나면 400")
	void rejectsOutOfRangeLatitude() throws Exception {
		mockMvc.perform(get("/api/pots")
				.param("latitude", "137.5172")
				.param("longitude", "127.0286"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		verify(potService, never()).findNearby(any());
	}

	@Test
	@DisplayName("좌표가 숫자가 아니면 500이 아니라 400")
	void rejectsNonNumericCoordinateWith400() throws Exception {
		mockMvc.perform(get("/api/pots")
				.param("latitude", "학동로171")
				.param("longitude", "127.0286"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		verify(potService, never()).findNearby(any());
	}
}
