package com.delipot.global.error;

import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
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

	/**
	 * 요청 본문 자체를 못 읽은 경우 — 깨진 JSON, 타입 불일치("capacity": "네명"),
	 * 파싱 불가한 날짜 포맷 등. 처리하지 않으면 catch-all로 흘러 500 + ERROR 로그가 되는데,
	 * 원인은 전부 클라이언트 입력이므로 400으로 끊는다.
	 *
	 * <p>메시지를 그대로 내리지 않는 이유는 Jackson 예외 본문에 패키지·클래스 경로가
	 * 노출되기 때문이다.
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException e) {
		log.warn("요청 본문을 읽을 수 없음: {}", e.getMostSpecificCause().getMessage());
		return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
			.body(ApiResponse.fail(ErrorCode.INVALID_INPUT, "요청 본문의 형식이 올바르지 않습니다."));
	}

	/** 서블릿 멀티파트 제한(application.yml)을 넘은 업로드. 컨트롤러에 도달하기 전에 던져진다. */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
		return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
			.body(ApiResponse.fail(ErrorCode.INVALID_INPUT, "파일 크기가 너무 큽니다."));
	}

	/**
	 * 낙관적 락 충돌 — 두 사람이 같은 순간에 팟의 마지막 자리를 노렸을 때 한쪽이 여기로 온다.
	 *
	 * <p>처리하지 않으면 catch-all로 흘러 500 + ERROR 로그가 되는데, 서버 잘못이 아니라
	 * "먼저 온 사람이 이겼다"는 정상적인 경합 결과다. 클라이언트가 재시도하면 되므로 409로 끊는다.
	 */
	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
		log.warn("낙관적 락 충돌: {}", e.getMessage());
		return ResponseEntity.status(ErrorCode.CONFLICT.getStatus())
			.body(ApiResponse.fail(ErrorCode.CONFLICT, "다른 요청이 먼저 처리됐습니다. 다시 시도해주세요."));
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
