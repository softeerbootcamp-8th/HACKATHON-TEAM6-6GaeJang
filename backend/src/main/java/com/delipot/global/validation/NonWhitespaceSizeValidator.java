package com.delipot.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NonWhitespaceSizeValidator implements ConstraintValidator<NonWhitespaceSize, String> {

	private int max;
	private int absoluteMax;

	@Override
	public void initialize(NonWhitespaceSize constraintAnnotation) {
		max = constraintAnnotation.max();
		absoluteMax = constraintAnnotation.absoluteMax();
	}

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}

		if (value.codePointCount(0, value.length()) > absoluteMax) {
			return false;
		}

		long length = value.codePoints()
			.filter(codePoint -> !Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint))
			.count();
		return length <= max;
	}
}
