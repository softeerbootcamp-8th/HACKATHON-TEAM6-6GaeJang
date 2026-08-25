package com.delipot.pot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.delipot.pot.dto.PotCreateRequest;
import com.delipot.pot.dto.PotCreateResponse;

@WebMvcTest(PotController.class)
class PotControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PotService potService;

	/** 마감시간은 @Future 검증을 타므로 테스트 실행 시각 기준 미래로 만든다. */
	private static String body(String overrides) {
		LocalDateTime deadline = LocalDateTime.now().plusHours(2).withNano(0);
		return """
			{
			  "hostId": 1,
			  "title": "역삼역 호백반점 같이 시켜요",
			  "storeName": "호백반점",
			  "storeUrl": "https://web.coupangeats.com/share?storeId=781313",
			  "meetingPlace": "역삼 스타빌 1층 로비",
			  "latitude": 37.5006,
			  "longitude": 127.0366,
			  "capacity": 4,
			  "minOrderAmount": 20000,
			  "deadline": "%s",
			  "description": "짜장면 먹고 싶은데 최소주문금액이 안 채워져요",
			  "bankName": "카카오뱅크",
			  "accountNumber": "3333-01-1234567",
			  "accountHolder": "김하나"
			  %s
			}
			""".formatted(deadline, overrides);
	}

	@Test
	@DisplayName("POST /api/pots — 201과 ApiResponse 래핑으로 응답한다")
	void createPot() throws Exception {
		given(potService.create(any(PotCreateRequest.class))).willReturn(
			new PotCreateResponse(1L, PotStatus.RECRUITING, 1, LocalDateTime.of(2026, 8, 25, 18, 0))
		);

		mockMvc.perform(post("/api/pots")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.potId").value(1))
			.andExpect(jsonPath("$.data.status").value("RECRUITING"))
			.andExpect(jsonPath("$.data.currentMemberCount").value(1))
			.andExpect(jsonPath("$.error").doesNotExist());
	}

	@Test
	@DisplayName("가게 링크가 http/https가 아니면 400 INVALID_INPUT")
	void rejectsNonHttpStoreUrl() throws Exception {
		mockMvc.perform(post("/api/pots")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("").replace(
					"https://web.coupangeats.com/share?storeId=781313",
					"baemin://store/12345")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		verify(potService, never()).create(any());
	}

	@Test
	@DisplayName("모집 인원이 1명이면 400 — 혼자 시키는 팟은 의미가 없다")
	void rejectsCapacityOne() throws Exception {
		mockMvc.perform(post("/api/pots")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("").replace("\"capacity\": 4", "\"capacity\": 1")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		verify(potService, never()).create(any());
	}

	@Test
	@DisplayName("마감시간이 과거면 400")
	void rejectsPastDeadline() throws Exception {
		String past = LocalDateTime.now().minusHours(1).withNano(0).toString();
		mockMvc.perform(post("/api/pots")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("").replaceAll("\"deadline\": \"[^\"]+\"", "\"deadline\": \"" + past + "\"")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		verify(potService, never()).create(any());
	}

	@Test
	@DisplayName("계좌번호에 문자가 섞이면 400")
	void rejectsMalformedAccountNumber() throws Exception {
		mockMvc.perform(post("/api/pots")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("").replace("3333-01-1234567", "카카오3333")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		verify(potService, never()).create(any());
	}

	@Test
	@DisplayName("위도가 범위를 벗어나면 400")
	void rejectsOutOfRangeLatitude() throws Exception {
		mockMvc.perform(post("/api/pots")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("").replace("\"latitude\": 37.5006", "\"latitude\": 137.5006")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		verify(potService, never()).create(any());
	}

	@Test
	@DisplayName("총대 ID가 없으면 400 — 인증 도입 전까지는 요청 바디가 유일한 출처다")
	void rejectsMissingHostId() throws Exception {
		mockMvc.perform(post("/api/pots")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("").replace("\"hostId\": 1,", "")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		verify(potService, never()).create(any());
	}
}
