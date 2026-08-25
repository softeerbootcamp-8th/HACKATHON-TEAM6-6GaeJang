package com.delipot.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 애노테이션이 붙은 컨트롤러 메서드는 인증(로그인)을 요구한다.
 * 검사는 {@link AuthenticationInterceptor} 가 담당하며, 미인증이면 401 을 던진다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAuthenticate {
}
