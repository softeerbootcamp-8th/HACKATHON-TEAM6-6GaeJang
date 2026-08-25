package com.delipot.pot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.delipot.auth.web.AuthContext;
import com.delipot.global.error.BusinessException;
import com.delipot.global.error.ErrorCode;
import com.delipot.pot.dto.PotJoinRequest;
import com.delipot.pot.dto.PotJoinResponse;

/**
 * 참여/나가기/모집 마감/완료 엔드포인트. 도메인 예외가 약속된 HTTP 상태와 error.code로
 * 번역되는지가 핵심이다 — 프론트가 이 코드로 분기한다.
 */
@WebMvcTest(PotController.class)
class PotActionControllerTest {

	private static final Long ME = 7L;

	/** 메뉴 입력 화면이 보내는 본문. 참여와 메뉴 전달이 한 요청이다. */
	private static final String MENU_BODY = """
		{
		  "menuContent": "허니콤보 세트 (순살로 변경) + 콜라 제로 500ml",
		  "menuPrice": 12000
		}
		""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PotService potService;

	@BeforeEach
	void authenticate() {
		AuthContext.setMemberId(ME);
	}

	@AfterEach
	void clearAuth() {
		AuthContext.clear();
	}

	@Test
	@DisplayName("POST /api/pots/{id}/members — 201과 채팅방 ID를 준다")
	void joinReturnsChatRoomId() throws Exception {
		given(potService.join(eq(ME), eq(1L), any(PotJoinRequest.class))).willReturn(new PotJoinResponse(1L, 3L, 3));

		mockMvc.perform(post("/api/pots/1/members")
				.contentType(MediaType.APPLICATION_JSON)
				.content(MENU_BODY))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.chatRoomId").value(3))
			.andExpect(jsonPath("$.data.currentMemberCount").value(3));
	}

	@Test
	@DisplayName("정원이 찬 팟에 참여하면 409 POT_FULL")
	void joinFullPotConflicts() throws Exception {
		given(potService.join(eq(ME), eq(1L), any(PotJoinRequest.class))).willThrow(new BusinessException(ErrorCode.POT_FULL));

		mockMvc.perform(post("/api/pots/1/members")
				.contentType(MediaType.APPLICATION_JSON)
				.content(MENU_BODY))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("POT_FULL"));
	}

	@Test
	@DisplayName("이미 참여한 팟이면 409 POT_ALREADY_JOINED")
	void joinTwiceConflicts() throws Exception {
		given(potService.join(eq(ME), eq(1L), any(PotJoinRequest.class))).willThrow(new BusinessException(ErrorCode.POT_ALREADY_JOINED));

		mockMvc.perform(post("/api/pots/1/members")
				.contentType(MediaType.APPLICATION_JSON)
				.content(MENU_BODY))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("POT_ALREADY_JOINED"));
	}

	@Test
	@DisplayName("DELETE /api/pots/{id}/members/me — 200, 팟과 채팅방을 함께 나간다")
	void leave() throws Exception {
		mockMvc.perform(delete("/api/pots/1/members/me"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true));

		verify(potService).leave(ME, 1L);
	}

	@Test
	@DisplayName("총대가 나가려 하면 400 POT_HOST_CANNOT_LEAVE")
	void hostCannotLeave() throws Exception {
		willThrow(new BusinessException(ErrorCode.POT_HOST_CANNOT_LEAVE)).given(potService).leave(ME, 1L);

		mockMvc.perform(delete("/api/pots/1/members/me"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("POT_HOST_CANNOT_LEAVE"));
	}

	@Test
	@DisplayName("POST /api/pots/{id}/complete — 나눔 완료 200")
	void complete() throws Exception {
		mockMvc.perform(post("/api/pots/1/complete"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true));

		verify(potService).complete(ME, 1L);
	}

	@Test
	@DisplayName("총대가 아니면 나눔 완료는 403 POT_ACCESS_DENIED")
	void completeByNonHostForbidden() throws Exception {
		willThrow(new BusinessException(ErrorCode.POT_ACCESS_DENIED)).given(potService).complete(ME, 1L);

		mockMvc.perform(post("/api/pots/1/complete"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("POT_ACCESS_DENIED"));
	}

	@Test
	@DisplayName("이미 나눔 완료된 팟을 다시 완료하면 409 POT_NOT_ACTIVE")
	void completeTwiceConflicts() throws Exception {
		willThrow(new BusinessException(ErrorCode.POT_NOT_ACTIVE)).given(potService).complete(ME, 1L);

		mockMvc.perform(post("/api/pots/1/complete"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("POT_NOT_ACTIVE"));
	}

	@Test
	@DisplayName("메뉴를 비우고 참여하면 400 — 메뉴 없는 참여는 총대가 주문을 넣을 수 없다")
	void joinWithoutMenuIsRejected() throws Exception {
		mockMvc.perform(post("/api/pots/1/members")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"menuContent\": \"  \", \"menuPrice\": 12000}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		verify(potService, never()).join(any(), any(), any());
	}

	@Test
	@DisplayName("금액이 없으면 400")
	void joinWithoutPriceIsRejected() throws Exception {
		mockMvc.perform(post("/api/pots/1/members")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"menuContent\": \"허니콤보\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		verify(potService, never()).join(any(), any(), any());
	}

	@Test
	@DisplayName("비로그인이면 모든 액션이 401")
	void rejectsUnauthenticated() throws Exception {
		AuthContext.clear();

		mockMvc.perform(post("/api/pots/1/members")
				.contentType(MediaType.APPLICATION_JSON)
				.content(MENU_BODY)).andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/pots/1/members/me")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/pots/1/complete")).andExpect(status().isUnauthorized());

		verify(potService, never()).join(any(), any(), any());
		verify(potService, never()).leave(any(), any());
		verify(potService, never()).complete(any(), any());
	}
}
