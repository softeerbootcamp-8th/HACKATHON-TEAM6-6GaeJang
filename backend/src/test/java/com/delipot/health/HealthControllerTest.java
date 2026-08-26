package com.delipot.health;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.delipot.health.dto.HealthResponse;
import com.delipot.health.dto.HealthResponse.Status;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private HealthService healthService;

	@Test
	@DisplayName("GET /api/health — ApiResponse 래핑 형태로 응답한다")
	void health() throws Exception {
		given(healthService.check()).willReturn(new HealthResponse(
			Status.UP, Status.UP, "local", "0.0.1-TEST",
			OffsetDateTime.of(2026, 8, 24, 9, 0, 0, 0, ZoneOffset.ofHours(9))
		));

		mockMvc.perform(get("/api/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.status").value("UP"))
			.andExpect(jsonPath("$.data.database").value("UP"))
			.andExpect(jsonPath("$.data.profile").value("local"))
			.andExpect(jsonPath("$.error").doesNotExist());
	}

	@Test
	@DisplayName("DB가 DOWN이어도 HTTP 200으로 응답한다")
	void healthWithDatabaseDown() throws Exception {
		given(healthService.check()).willReturn(new HealthResponse(
			Status.DOWN, Status.DOWN, "local", "0.0.1-TEST", OffsetDateTime.now()
		));

		mockMvc.perform(get("/api/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("DOWN"));
	}
}
