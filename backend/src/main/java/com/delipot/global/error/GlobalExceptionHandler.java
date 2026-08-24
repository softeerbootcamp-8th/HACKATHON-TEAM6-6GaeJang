package com.delipot.global.error;

import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.delipot.global.response.ApiResponse;

import lombok.extern.slf4j.Slf4j;

/** 모든 예외를 {@link ApiResponse} 형태로 통일해서 내려주는 지점. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
		log.warn("도메인 예외: {} - {}", e.getErrorCode(), e.getMessage());
		return ResponseEntity.status(e.getErrorCode().getStatus())
			.body(ApiResponse.fail(e.getErrorCode(), e.getMessage()));
	}

	/** @Valid 실패 — 어떤 필드가 왜 틀렸는지까지 내려준다. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
		String detail = e.getBindingResult().getFieldErrors().stream()
			.map(error -> error.getField() + ": " + error.getDefaultMessage())
			.collect(Collectors.joining(", "));
		return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
			.body(ApiResponse.fail(ErrorCode.INVALID_INPUT, detail));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
		return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getStatus())
			.body(ApiResponse.fail(ErrorCode.METHOD_NOT_ALLOWED));
	}

	/** 없는 경로 요청. catch-all로 흘러가면 500 + 에러 로그가 쌓이므로 여기서 404로 끊는다. */
	@ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
	public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception e) {
		return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getStatus())
			.body(ApiResponse.fail(ErrorCode.RESOURCE_NOT_FOUND));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
		log.error("처리하지 못한 예외", e);
		return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
			.body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR));
	}
}
