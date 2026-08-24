package com.delipot.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.delipot.global.error.ErrorCode;

/**
 * 모든 API 응답의 공통 래퍼.
 *
 * <pre>
 * 성공: {"success": true,  "data": {...}}
 * 실패: {"success": false, "error": {"code": "INVALID_INPUT", "message": "..."}}
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ErrorBody(String code, String message) {
	}

	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(true, data, null);
	}

	public static ApiResponse<Void> ok() {
		return new ApiResponse<>(true, null, null);
	}

	public static ApiResponse<Void> fail(ErrorCode code, String message) {
		return new ApiResponse<>(false, null, new ErrorBody(code.name(), message));
	}

	public static ApiResponse<Void> fail(ErrorCode code) {
		return fail(code, code.getMessage());
	}
}
