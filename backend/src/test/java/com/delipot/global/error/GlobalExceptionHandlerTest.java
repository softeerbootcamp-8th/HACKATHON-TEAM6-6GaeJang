package com.delipot.global.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.delipot.global.response.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** 에러 응답 계약(success=false, error.code/message)이 깨지지 않는지 검증한다. */
class GlobalExceptionHandlerTest {

	private final MockMvc mockMvc = MockMvcBuilders
		.standaloneSetup(new TestController())
		.setControllerAdvice(new GlobalExceptionHandler())
		.build();

	@Test
	@DisplayName("도메인 예외는 ErrorCode의 상태코드와 code로 변환된다")
	void businessException() throws Exception {
		mockMvc.perform(get("/test/business"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"))
			.andExpect(jsonPath("$.error.message").value("라이딩이 없다"))
			.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	@DisplayName("@Valid 실패는 400 INVALID_INPUT + 필드별 메시지로 내려간다")
	void validationException() throws Exception {
		mockMvc.perform(post("/test/valid")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("name: 이름은 필수다"));
	}

	@Test
	@DisplayName("예상 못한 예외는 500 INTERNAL_ERROR로 감싸 원인을 노출하지 않는다")
	void unexpectedException() throws Exception {
		mockMvc.perform(get("/test/boom"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.error.message").value("서버 오류가 발생했습니다."));
	}

	@RestController
	static class TestController {

		record Request(@NotBlank(message = "이름은 필수다") String name) {
		}

		@GetMapping("/test/business")
		ApiResponse<Void> business() {
			throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "라이딩이 없다");
		}

		@PostMapping("/test/valid")
		ApiResponse<Void> valid(@Valid @RequestBody Request request) {
			return ApiResponse.ok();
		}

		@GetMapping("/test/boom")
		ApiResponse<Void> boom() {
			throw new IllegalStateException("내부 사정");
		}
	}
}
