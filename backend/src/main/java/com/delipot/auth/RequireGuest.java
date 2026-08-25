package com.delipot.auth;

import com.delipot.auth.web.AuthenticationInterceptor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 애노테이션이 붙은 컨트롤러 메서드는 미인증(비로그인) 상태만 허용한다.
 * 검사는 {@link AuthenticationInterceptor} 가 담당하며, 이미 인증돼 있으면 409 를 던진다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireGuest {
}
