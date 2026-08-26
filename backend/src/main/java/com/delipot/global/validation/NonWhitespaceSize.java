package com.delipot.global.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** 공백 문자를 제외한 실제 입력 글자 수를 제한한다. */
@Documented
@Constraint(validatedBy = NonWhitespaceSizeValidator.class)
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface NonWhitespaceSize {

	String message() default "공백을 제외한 글자 수가 너무 깁니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	int max();

	/** 저장소와 요청 메모리를 보호하기 위한 공백 포함 절대 상한. */
	int absoluteMax() default Integer.MAX_VALUE;
}
