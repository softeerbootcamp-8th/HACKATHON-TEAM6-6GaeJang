package com.delipot.pot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.mockito.ArgumentCaptor;

import com.delipot.auth.web.AuthContext;
import com.delipot.pot.dto.PotCreateRequest;
import com.delipot.pot.dto.PotCreateResponse;

@WebMvcTest(PotController.class)
class PotControllerTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final Long LOGIN_MEMBER_ID = 7L;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PotService potService;

	/** 인증 인터셉터가 @WebMvcTest 에도 등록되므로 ThreadLocal 컨텍스트를 직접 심는다. */
	@BeforeEach
	void authenticate() {
		AuthContext.setMemberId(LOGIN_MEMBER_ID);
	}

	@AfterEach
	void clearAuth() {
		AuthContext.clear();
	}

	/** 마감시간은 @Future 검증을 타므로 실행 시각 기준 미래(KST 오프셋)로 만든다. */
	private static String body() {
		return bodyWithDeadline(OffsetDateTime.now(SEOUL).plusHours(2).withNano(0).toString());
	}

	private static String bodyWithDeadline(String deadline) {
		return """
			{
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
			}
			""".formatted(deadline);
	}

	private void givenServiceSucceeds() {
		given(potService.create(any(Long.class), any(PotCreateRequest.class))).willReturn(new PotCreateResponse(
			1L, PotStatus.ACTIVE, 1, 3L, OffsetDateTime.of(2026, 8, 25, 18, 0, 0, 0, ZoneOffset.ofHours(9))
		));
	}

	@Test
	@DisplayName("POST /api/pots — 201과 ApiResponse 래핑으로 응답한다")
	void createPot() throws Exception {
		givenServiceSucceeds();

		mockMvc.perform(post("/api/pots")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.potId").value(1))
			.andExpect(jsonPath("$.data.status").value("ACTIVE"))
			.andExpect(jsonPath("$.data.currentMemberCount").value(1))
			.andExpect(jsonPath("$.error").doesNotExist());
	}

	/**
	 * 회귀 방지: 이전에는 deadline이 {@code LocalDateTime}이라 Z 접미사가 조용히 버려져
	 * 마감이 9시간 앞당겨졌다. 이제 오프셋을 반영한 절대 시각으로 들어온다.
	 */
	@Test
	@DisplayName("프론트가 toISOString()으로 보낸 Z 접미사 값이 오프셋 그대로 해석된다")
	void preservesOffsetFromIsoString() throws Exception {
		givenServiceSucceeds();

		// 총대의 의도: KST 2026-12-25 19:30 마감
		mockMvc.perform(post("/api/pots")
				.contentType(MediaType.APPLICATION_JSON)
				.content(bodyWithDeadline("2026-12-25T10:30:00.000Z")))
			.andExpect(status().isCreated());

		ArgumentCaptor<PotCreateRequest> captor = ArgumentCaptor.forClass(PotCreateRequest.class);
		verify(potService).create(any(Long.class), captor.capture());

		assertThat(captor.getValue().deadline().atZoneSameInstant(SEOUL).toLocalDateTime())
			.hasToString("2026-12-25T19:30");
	}

	@Test
	@DisplayName("오프셋이 없는 마감시간은 400 — 어느 지역 시각인지 알 수 없다")
	void rejectsDeadlineWithoutOffset() throws Exception {
		mockMvc.perform(post("/api/pots")
				.contentType(MediaType.APPLICATION_JSON)
				.content(bodyWithDeadline("2026-12-25T19:30:00")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		verify(potService, never()).create(any(), any());
	}

	@Test
	@DisplayName("가게 링크가 http/https가 아니면 400 INVALID_INPUT")
	void rejectsNonHttpStoreUrl() throws Exception {
		mockMvc.perform(post("/api/pots")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body().replace(
					"https://web.coupangeats.com/share?storeId=781313",
					"baemin://store/12345")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		verify(potService, never()).create(any(), any());
	}

	@Test
	@DisplayName("배달팟 인원이 1명이면 400 — 혼자 시키는 팟은 의미가 없다")
	void rejectsCapacityOne() throws Exception {
		mockMvc.perform(post("/api/pots")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body().replace("\"capacity\": 4", "\"capacity\": 1")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		verify(potService, never()).create(any(), any());
	}

	@Test
	@DisplayName("배달팟 인원이 5명이면 400 — 배달팟 정원은 최대 4명이다")
	void rejectsCapacityFive() throws Exception {
		mockMvc.perform(post("/api/pots")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body().replace("\"capacity\": 4", "\"capacity\": 5")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		verify(potService, never()).create(any(), any());
	}

	@Test
	@DisplayName("글 제목은 공백을 제외하고 30자까지 허용한다")
	void acceptsTitleWithThirtyNonWhitespaceCharacters() throws Exception {
		givenServiceSucceeds();
		String title = "가".repeat(15) + " ".repeat(10) + "나".repeat(15);

		mockMvc.perform(post("/api/pots")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body().replace("역삼역 호백반점 같이 시켜요", title)))
			.andExpect(status().isCreated());

		verify(potService).create(any(), any());
	}

	@Test
	@DisplayName("글 제목이 공백 제외 30자를 넘으면 400")
	void rejectsTitleOverThirtyNonWhitespaceCharacters() throws Exception {
		String title = "가".repeat(31);

		mockMvc.perform(post("/api/pots")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body().replace("역삼역 호백반점 같이 시켜요", title)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		verify(potService, never()).create(any(), any());
	}

	@Test
	@DisplayName("상세 설명이 공백 제외 200자를 넘으면 400")
	void rejectsDescriptionOverTwoHundredNonWhitespaceCharacters() throws Exception {
		String description = "가".repeat(201);

		mockMvc.perform(post("/api/pots")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body().replace("짜장면 먹고 싶은데 최소주문금액이 안 채워져요", description)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		verify(potService, never()).create(any(), any());
	}

	@Test
	@DisplayName("마감시간이 과거면 400")
	void rejectsPastDeadline() throws Exception {
		String past = OffsetDateTime.now(SEOUL).minusHours(1).withNano(0).toString();

		mockMvc.perform(post("/api/pots")
				.contentType(MediaType.APPLICATION_JSON)
				.content(bodyWithDeadline(past)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		verify(potService, never()).create(any(), any());
	}

	@Test
	@DisplayName("계좌번호에 문자가 섞이면 400")
	void rejectsMalformedAccountNumber() throws Exception {
		mockMvc.perform(post("/api/pots")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body().replace("3333-01-1234567", "카카오3333")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		verify(potService, never()).create(any(), any());
	}

	@Test
	@DisplayName("위도가 범위를 벗어나면 400")
	void rejectsOutOfRangeLatitude() throws Exception {
		mockMvc.perform(post("/api/pots")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body().replace("\"latitude\": 37.5006", "\"latitude\": 137.5006")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		verify(potService, never()).create(any(), any());
	}

	@Test
	@DisplayName("총대는 로그인한 회원으로 고정된다 — 요청 본문으로 지정할 수 없다")
	void hostComesFromSession() throws Exception {
		givenServiceSucceeds();

		// 요청 본문에 hostId 를 끼워 넣어도 무시되고 세션의 회원이 총대가 된다
		mockMvc.perform(post("/api/pots")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body().replace("{", "{\"hostId\": 999,")))
			.andExpect(status().isCreated());

		verify(potService).create(org.mockito.ArgumentMatchers.eq(LOGIN_MEMBER_ID), any(PotCreateRequest.class));
	}

	@Test
	@DisplayName("비로그인 상태면 401 — 남을 총대로 세우는 것을 막는다")
	void rejectsUnauthenticated() throws Exception {
		AuthContext.clear();

		mockMvc.perform(post("/api/pots")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

		verify(potService, never()).create(any(), any());
	}

	/** 회귀 방지: 아래 세 경우는 이전에 모두 500 INTERNAL_ERROR로 나갔다. */
	@Test
	@DisplayName("숫자 필드에 문자열이 오면 500이 아니라 400")
	void rejectsTypeMismatchWith400() throws Exception {
		mockMvc.perform(post("/api/pots")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body().replace("\"capacity\": 4", "\"capacity\": \"네명\"")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		verify(potService, never()).create(any(), any());
	}

	@Test
	@DisplayName("파싱 불가한 날짜 포맷이면 500이 아니라 400")
	void rejectsUnparseableDateWith400() throws Exception {
		mockMvc.perform(post("/api/pots")
				.contentType(MediaType.APPLICATION_JSON)
				.content(bodyWithDeadline("2026-12-25 19:30:00")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		verify(potService, never()).create(any(), any());
	}

	@Test
	@DisplayName("절단된 JSON이면 500이 아니라 400")
	void rejectsBrokenJsonWith400() throws Exception {
		mockMvc.perform(post("/api/pots")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"hostId\": "))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		verify(potService, never()).create(any(), any());
	}
}
