package com.delipot.global.error;

import lombok.Getter;

/**
 * 도메인 규칙 위반 예외의 최상위 타입.
 * 도메인별 예외는 이 클래스를 상속해서 {@link ErrorCode}만 지정한다.
 */
@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public BusinessException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}
}
