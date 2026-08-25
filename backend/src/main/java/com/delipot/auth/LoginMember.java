package com.delipot.auth;

import com.delipot.auth.web.LoginMemberArgumentResolver;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 붙이면 로그인한 회원의 id(Long)를 주입받는다.
 * 해석은 {@link LoginMemberArgumentResolver} 가 담당한다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginMember {
}
